//! Real database-backed lookup service implementation

use crate::models::*;
use anyhow::{Context, Result};
use async_trait::async_trait;
use sqlx::{PgPool, Row};
use tracing::{info, warn};

#[async_trait]
pub trait RealLookupService: Send + Sync {
    /// Get home profile with members and rooms
    async fn get_home(&self, home_id: &str) -> Result<Option<HomeProfile>>;

    /// Get task templates for a home
    async fn get_task_templates(&self, home_id: &str) -> Result<Vec<TaskTemplate>>;
}

/// Database-backed lookup service
pub struct DbLookupService {
    pool: Option<PgPool>,
}

impl DbLookupService {
    pub fn new() -> Self {
        Self { pool: None }
    }

    pub fn with_pool(pool: PgPool) -> Self {
        Self { pool: Some(pool) }
    }

    fn parse_room_kind(kind_str: &str) -> Option<RoomKind> {
        match kind_str.to_lowercase().as_str() {
            "kitchen" => Some(RoomKind::Kitchen),
            "bathroom" => Some(RoomKind::Bathroom),
            "bedroom" => Some(RoomKind::Bedroom),
            "living" => Some(RoomKind::Living),
            "other" => Some(RoomKind::Other),
            _ => None,
        }
    }

    fn parse_member_role(role_str: &str) -> MemberRole {
        match role_str.to_lowercase().as_str() {
            "adult" => MemberRole::Adult,
            "kid" => MemberRole::Kid,
            "guest" => MemberRole::Guest,
            "pet_proxy" => MemberRole::PetProxy,
            _ => MemberRole::Adult,
        }
    }
}

#[async_trait]
impl RealLookupService for DbLookupService {
    async fn get_home(&self, home_id: &str) -> Result<Option<HomeProfile>> {
        let pool = match &self.pool {
            Some(p) => p,
            None => {
                warn!("No database pool configured, returning mock data");
                return Ok(Some(self.mock_home_profile(home_id)));
            }
        };

        // Query home
        let home_row = sqlx::query(
            "SELECT id, owner_user_id, name, tz, locale, metadata, created_at, updated_at
             FROM homes WHERE id = $1"
        )
        .bind(home_id)
        .fetch_optional(pool)
        .await
        .context("Failed to query home")?;

        let home_row = match home_row {
            Some(row) => row,
            None => {
                info!(home_id = %home_id, "Home not found");
                return Ok(None);
            }
        };

        let home = Home {
            id: home_row.get("id"),
            owner_user_id: home_row.get("owner_user_id"),
            name: home_row.get("name"),
            tz: home_row.get("tz"),
            locale: home_row.get("locale"),
            metadata: home_row.get("metadata"),
            created_at: home_row.get("created_at"),
            updated_at: home_row.get("updated_at"),
        };

        // Query members
        let member_rows = sqlx::query(
            "SELECT id, home_id, name, role::text as role, avatar_url, created_at
             FROM members WHERE home_id = $1"
        )
        .bind(home_id)
        .fetch_all(pool)
        .await
        .context("Failed to query members")?;

        let members: Vec<Member> = member_rows
            .into_iter()
            .map(|row| {
                let role_str: String = row.get("role");
                Member {
                    id: row.get("id"),
                    home_id: row.get("home_id"),
                    name: row.get("name"),
                    role: Self::parse_member_role(&role_str),
                    avatar_url: row.get("avatar_url"),
                    created_at: row.get("created_at"),
                }
            })
            .collect();

        // Query rooms
        let room_rows = sqlx::query(
            "SELECT id, home_id, name, kind::text as kind, metadata
             FROM rooms WHERE home_id = $1"
        )
        .bind(home_id)
        .fetch_all(pool)
        .await
        .context("Failed to query rooms")?;

        let rooms: Vec<Room> = room_rows
            .into_iter()
            .map(|row| {
                let kind_str: Option<String> = row.get("kind");
                Room {
                    id: row.get("id"),
                    home_id: row.get("home_id"),
                    name: row.get("name"),
                    kind: kind_str.and_then(|k| Self::parse_room_kind(&k)),
                    metadata: row.get("metadata"),
                }
            })
            .collect();

        info!(
            home_id = %home_id,
            members = members.len(),
            rooms = rooms.len(),
            "Loaded home profile from database"
        );

        Ok(Some(HomeProfile { home, members, rooms }))
    }

    async fn get_task_templates(&self, _home_id: &str) -> Result<Vec<TaskTemplate>> {
        let pool = match &self.pool {
            Some(p) => p,
            None => {
                warn!("No database pool configured, returning mock templates");
                return Ok(self.mock_task_templates());
            }
        };

        // Query task templates (global templates, not home-specific for now)
        let rows = sqlx::query(
            "SELECT id, title, default_estimate_min, room_kind::text as room_kind, frequency, tools, policy_tags, i18n
             FROM task_templates"
        )
        .fetch_all(pool)
        .await
        .context("Failed to query task templates")?;

        let templates: Vec<TaskTemplate> = rows
            .into_iter()
            .map(|row| {
                let room_kind_str: Option<String> = row.get("room_kind");
                let policy_tags_json: Option<serde_json::Value> = row.get("policy_tags");
                TaskTemplate {
                    id: row.get("id"),
                    title: row.get("title"),
                    default_estimate_min: row.get("default_estimate_min"),
                    room_kind: room_kind_str.and_then(|k| Self::parse_room_kind(&k)),
                    frequency: row.get("frequency"),
                    tools: row.get("tools"),
                    policy_tags: policy_tags_json.and_then(|v| {
                        serde_json::from_value::<Vec<String>>(v).ok()
                    }),
                    i18n: row.get("i18n"),
                }
            })
            .collect();

        info!(count = templates.len(), "Loaded task templates from database");
        Ok(templates)
    }
}

impl DbLookupService {
    /// Mock home profile for when database is not available
    fn mock_home_profile(&self, home_id: &str) -> HomeProfile {
        HomeProfile {
            home: Home {
                id: home_id.to_string(),
                owner_user_id: "user_123".to_string(),
                name: "Sample Home".to_string(),
                tz: "America/Los_Angeles".to_string(),
                locale: "en-US".to_string(),
                created_at: chrono::Utc::now(),
                updated_at: chrono::Utc::now(),
                metadata: None,
            },
            members: vec![
                Member {
                    id: "m_dad".to_string(),
                    home_id: home_id.to_string(),
                    name: "Alex".to_string(),
                    role: MemberRole::Adult,
                    avatar_url: None,
                    created_at: chrono::Utc::now(),
                },
            ],
            rooms: vec![
                Room {
                    id: "r_kitchen".to_string(),
                    home_id: home_id.to_string(),
                    name: "Kitchen".to_string(),
                    kind: Some(RoomKind::Kitchen),
                    metadata: None,
                },
                Room {
                    id: "r_living".to_string(),
                    home_id: home_id.to_string(),
                    name: "Living Room".to_string(),
                    kind: Some(RoomKind::Living),
                    metadata: None,
                },
            ],
        }
    }

    /// Mock task templates for when database is not available
    fn mock_task_templates(&self) -> Vec<TaskTemplate> {
        vec![
            TaskTemplate {
                id: "tmpl_wipe_counters".to_string(),
                title: "Wipe kitchen counters".to_string(),
                default_estimate_min: 5,
                room_kind: Some(RoomKind::Kitchen),
                frequency: Some("daily".to_string()),
                tools: Some(serde_json::json!(["sponge", "cleaner"])),
                policy_tags: Some(vec!["safe_for_kids".to_string()]),
                i18n: None,
            },
            TaskTemplate {
                id: "tmpl_vacuum_living".to_string(),
                title: "Vacuum living room".to_string(),
                default_estimate_min: 15,
                room_kind: Some(RoomKind::Living),
                frequency: Some("weekly".to_string()),
                tools: Some(serde_json::json!(["vacuum"])),
                policy_tags: Some(vec!["requires_supervision".to_string()]),
                i18n: None,
            },
        ]
    }
}

/// Home profile with members and rooms
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct HomeProfile {
    pub home: Home,
    pub members: Vec<Member>,
    pub rooms: Vec<Room>,
}
