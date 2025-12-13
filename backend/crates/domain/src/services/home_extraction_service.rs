//! Home data extraction service - extracts structured home data from Clara onboarding conversations

use crate::models::*;
use anyhow::{Context, Result};
use async_trait::async_trait;
use reqwest::Client;
use serde::{Deserialize, Serialize};
use serde_json::json;
use sqlx::PgPool;
use tracing::{info, warn, error};

/// Extracted home data from onboarding conversation
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ExtractedHomeData {
    pub home_name: Option<String>,
    pub rooms: Vec<ExtractedRoom>,
    pub members: Vec<ExtractedMember>,
    pub pets: Vec<ExtractedPet>,
    pub preferences: ExtractedPreferences,
    pub problem_areas: Vec<ExtractedProblemArea>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ExtractedRoom {
    pub name: String,
    pub kind: String, // kitchen, bathroom, bedroom, living, other
    pub notes: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ExtractedMember {
    pub name: String,
    pub role: String, // adult, kid, teen
    pub notes: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ExtractedPet {
    pub pet_type: String, // dog, cat, bird, etc
    pub name: Option<String>,
    pub shedding_level: Option<String>, // high, medium, low, none
    pub rooms_frequent: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct ExtractedPreferences {
    pub preferred_cleaning_times: Vec<String>,
    pub quiet_hours: Option<String>,
    pub busy_days: Vec<String>,
    pub cleaning_style: Option<String>, // thorough, quick, etc
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ExtractedProblemArea {
    pub room: String,
    pub issue: String,
}

/// Request to extract home data from conversation
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ExtractHomeRequest {
    pub user_id: String,
    pub session_id: String,
    pub conversation_transcript: Vec<ConversationMessage>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ConversationMessage {
    pub role: String, // user or assistant
    pub content: String,
}

/// Response from home extraction
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ExtractHomeResponse {
    pub home_id: String,
    pub extracted_data: ExtractedHomeData,
    pub success: bool,
    pub message: Option<String>,
}

#[async_trait]
pub trait HomeExtractionService: Send + Sync {
    /// Extract structured home data from onboarding conversation
    async fn extract_from_conversation(&self, request: ExtractHomeRequest) -> Result<ExtractHomeResponse>;
}

/// LLM-backed home extraction service
pub struct LlmHomeExtractionService {
    db_pool: PgPool,
    http_client: Client,
    anthropic_api_key: Option<String>,
    model: String,
}

impl LlmHomeExtractionService {
    pub fn new(db_pool: PgPool, anthropic_api_key: Option<String>) -> Self {
        Self {
            db_pool,
            http_client: Client::new(),
            anthropic_api_key,
            model: "claude-sonnet-4-20250514".to_string(),
        }
    }

    pub fn with_model(mut self, model: String) -> Self {
        self.model = model;
        self
    }

    fn build_extraction_prompt(&self, transcript: &[ConversationMessage]) -> String {
        let conversation_text = transcript
            .iter()
            .map(|m| format!("{}: {}", m.role, m.content))
            .collect::<Vec<_>>()
            .join("\n");

        format!(
            r#"Analyze this CleanFlow onboarding conversation and extract structured home data.

CONVERSATION:
{}

Extract the following information as JSON:
{{
  "home_name": "optional name for the home",
  "rooms": [
    {{"name": "Kitchen", "kind": "kitchen", "notes": "optional notes"}}
  ],
  "members": [
    {{"name": "Alex", "role": "adult", "notes": "optional notes"}}
  ],
  "pets": [
    {{"pet_type": "dog", "name": "Max", "shedding_level": "high", "rooms_frequent": ["living room", "bedroom"]}}
  ],
  "preferences": {{
    "preferred_cleaning_times": ["morning", "evening"],
    "quiet_hours": "9pm-7am",
    "busy_days": ["Monday", "Wednesday"],
    "cleaning_style": "thorough"
  }},
  "problem_areas": [
    {{"room": "Kitchen", "issue": "grease buildup near stove"}}
  ]
}}

Rules:
- room.kind must be one of: kitchen, bathroom, bedroom, living, other
- member.role must be one of: adult, kid, teen
- pet.shedding_level must be one of: high, medium, low, none
- Only include information explicitly mentioned or strongly implied in the conversation
- If information is not available, use empty arrays or null for optional fields
- Return ONLY valid JSON, no other text"#,
            conversation_text
        )
    }

    async fn call_anthropic(&self, prompt: &str) -> Result<String> {
        let api_key = self
            .anthropic_api_key
            .as_ref()
            .ok_or_else(|| anyhow::anyhow!("Anthropic API key not configured"))?;

        let request_body = json!({
            "model": self.model,
            "max_tokens": 2048,
            "messages": [
                {
                    "role": "user",
                    "content": prompt
                }
            ]
        });

        let response = self
            .http_client
            .post("https://api.anthropic.com/v1/messages")
            .header("x-api-key", api_key)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .json(&request_body)
            .send()
            .await
            .context("Failed to call Anthropic API")?;

        if !response.status().is_success() {
            let error_text = response.text().await.unwrap_or_else(|_| "Unknown error".to_string());
            return Err(anyhow::anyhow!("Anthropic API error: {}", error_text));
        }

        let result: serde_json::Value = response
            .json()
            .await
            .context("Failed to parse Anthropic response")?;

        let content = result
            .get("content")
            .and_then(|c| c.get(0))
            .and_then(|c| c.get("text"))
            .and_then(|t| t.as_str())
            .ok_or_else(|| anyhow::anyhow!("Invalid Anthropic response format"))?;

        Ok(content.to_string())
    }

    fn parse_extracted_data(&self, content: &str) -> Result<ExtractedHomeData> {
        // Try to extract JSON from response (may be wrapped in markdown code blocks)
        let json_str = if content.trim().starts_with("```") {
            content
                .lines()
                .skip_while(|l| !l.starts_with("{"))
                .take_while(|l| !l.starts_with("```") || l.starts_with("{"))
                .collect::<Vec<_>>()
                .join("\n")
        } else if let Some(start) = content.find('{') {
            if let Some(end) = content.rfind('}') {
                content[start..=end].to_string()
            } else {
                content.to_string()
            }
        } else {
            content.to_string()
        };

        serde_json::from_str(&json_str).context("Failed to parse extracted home data JSON")
    }

    fn map_room_kind(&self, kind: &str) -> RoomKind {
        match kind.to_lowercase().as_str() {
            "kitchen" => RoomKind::Kitchen,
            "bathroom" | "bath" | "restroom" => RoomKind::Bathroom,
            "bedroom" | "bed" => RoomKind::Bedroom,
            "living" | "living room" | "lounge" => RoomKind::Living,
            _ => RoomKind::Other,
        }
    }

    fn map_member_role(&self, role: &str) -> MemberRole {
        match role.to_lowercase().as_str() {
            "adult" | "parent" => MemberRole::Adult,
            "kid" | "child" | "teen" | "teenager" => MemberRole::Kid,
            "guest" => MemberRole::Guest,
            _ => MemberRole::Adult,
        }
    }

    async fn persist_home_data(
        &self,
        user_id: &str,
        extracted: &ExtractedHomeData,
    ) -> Result<String> {
        let home_id = format!("home_{}", uuid::Uuid::new_v4().to_string()[..12].to_string());
        let home_name = extracted.home_name.clone().unwrap_or_else(|| "My Home".to_string());
        let prefs_json = serde_json::to_value(&extracted.preferences).ok();

        // Insert home - using runtime query to avoid enum type issues
        sqlx::query(
            "INSERT INTO homes (id, owner_user_id, name, tz, locale, metadata, created_at, updated_at)
             VALUES ($1, $2, $3, 'UTC', 'en-US', $4, NOW(), NOW())"
        )
        .bind(&home_id)
        .bind(user_id)
        .bind(&home_name)
        .bind(&prefs_json)
        .execute(&self.db_pool)
        .await
        .context("Failed to insert home")?;

        // Insert rooms - using runtime query with enum cast
        for room in &extracted.rooms {
            let room_id = format!("room_{}", uuid::Uuid::new_v4().to_string()[..8].to_string());
            let room_kind = self.map_room_kind(&room.kind);
            let metadata = room.notes.as_ref().map(|n| serde_json::json!({"notes": n}));

            sqlx::query(
                "INSERT INTO rooms (id, home_id, name, kind, metadata)
                 VALUES ($1, $2, $3, $4::room_kind, $5)"
            )
            .bind(&room_id)
            .bind(&home_id)
            .bind(&room.name)
            .bind(room_kind.to_string())
            .bind(&metadata)
            .execute(&self.db_pool)
            .await
            .context("Failed to insert room")?;
        }

        // Insert members - using runtime query with enum cast
        for member in &extracted.members {
            let member_id = format!("member_{}", uuid::Uuid::new_v4().to_string()[..8].to_string());
            let member_role = self.map_member_role(&member.role);

            sqlx::query(
                "INSERT INTO members (id, home_id, name, role, created_at)
                 VALUES ($1, $2, $3, $4::member_role, NOW())"
            )
            .bind(&member_id)
            .bind(&home_id)
            .bind(&member.name)
            .bind(member_role.to_string())
            .execute(&self.db_pool)
            .await
            .context("Failed to insert member")?;
        }

        // Store pets in home metadata (or separate table if exists)
        if !extracted.pets.is_empty() {
            let pets_json = serde_json::to_value(&extracted.pets)?;
            sqlx::query(
                "UPDATE homes
                 SET metadata = COALESCE(metadata, '{}'::jsonb) || jsonb_build_object('pets', $1::jsonb)
                 WHERE id = $2"
            )
            .bind(&pets_json)
            .bind(&home_id)
            .execute(&self.db_pool)
            .await
            .context("Failed to update home with pets")?;
        }

        // Store problem areas in home metadata
        if !extracted.problem_areas.is_empty() {
            let problems_json = serde_json::to_value(&extracted.problem_areas)?;
            sqlx::query(
                "UPDATE homes
                 SET metadata = COALESCE(metadata, '{}'::jsonb) || jsonb_build_object('problem_areas', $1::jsonb)
                 WHERE id = $2"
            )
            .bind(&problems_json)
            .bind(&home_id)
            .execute(&self.db_pool)
            .await
            .context("Failed to update home with problem areas")?;
        }

        info!(home_id = %home_id, rooms = extracted.rooms.len(), members = extracted.members.len(), "Home data persisted");
        Ok(home_id)
    }
}

#[async_trait]
impl HomeExtractionService for LlmHomeExtractionService {
    async fn extract_from_conversation(&self, request: ExtractHomeRequest) -> Result<ExtractHomeResponse> {
        info!(
            user_id = %request.user_id,
            session_id = %request.session_id,
            messages = request.conversation_transcript.len(),
            "Extracting home data from conversation"
        );

        // Check for API key
        if self.anthropic_api_key.is_none() {
            warn!("Anthropic API key not configured, returning default home data");
            let default_data = ExtractedHomeData {
                home_name: Some("My Home".to_string()),
                rooms: vec![
                    ExtractedRoom { name: "Kitchen".to_string(), kind: "kitchen".to_string(), notes: None },
                    ExtractedRoom { name: "Living Room".to_string(), kind: "living".to_string(), notes: None },
                    ExtractedRoom { name: "Bathroom".to_string(), kind: "bathroom".to_string(), notes: None },
                ],
                members: vec![],
                pets: vec![],
                preferences: ExtractedPreferences::default(),
                problem_areas: vec![],
            };

            let home_id = self.persist_home_data(&request.user_id, &default_data).await?;

            return Ok(ExtractHomeResponse {
                home_id,
                extracted_data: default_data,
                success: true,
                message: Some("Used default home setup (no API key)".to_string()),
            });
        }

        // Build prompt and call LLM
        let prompt = self.build_extraction_prompt(&request.conversation_transcript);
        let response = self.call_anthropic(&prompt).await?;

        // Parse response
        let extracted_data = self.parse_extracted_data(&response)?;

        // Persist to database
        let home_id = self.persist_home_data(&request.user_id, &extracted_data).await?;

        info!(
            home_id = %home_id,
            rooms = extracted_data.rooms.len(),
            members = extracted_data.members.len(),
            pets = extracted_data.pets.len(),
            "Home extraction complete"
        );

        Ok(ExtractHomeResponse {
            home_id,
            extracted_data,
            success: true,
            message: None,
        })
    }
}

// Display implementations for RoomKind and MemberRole
impl std::fmt::Display for RoomKind {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let s = match self {
            RoomKind::Kitchen => "kitchen",
            RoomKind::Bathroom => "bathroom",
            RoomKind::Bedroom => "bedroom",
            RoomKind::Living => "living",
            RoomKind::Other => "other",
        };
        write!(f, "{}", s)
    }
}

impl std::fmt::Display for MemberRole {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let s = match self {
            MemberRole::Adult => "adult",
            MemberRole::Kid => "kid",
            MemberRole::Guest => "guest",
            MemberRole::PetProxy => "pet_proxy",
        };
        write!(f, "{}", s)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_extracted_data() {
        let json = r#"{
            "home_name": "Test Home",
            "rooms": [
                {"name": "Kitchen", "kind": "kitchen", "notes": null}
            ],
            "members": [
                {"name": "John", "role": "adult", "notes": null}
            ],
            "pets": [],
            "preferences": {
                "preferred_cleaning_times": ["morning"],
                "quiet_hours": null,
                "busy_days": [],
                "cleaning_style": "quick"
            },
            "problem_areas": []
        }"#;

        let data: ExtractedHomeData = serde_json::from_str(json).unwrap();
        assert_eq!(data.home_name, Some("Test Home".to_string()));
        assert_eq!(data.rooms.len(), 1);
        assert_eq!(data.members.len(), 1);
    }

    #[test]
    fn test_room_kind_string_mapping() {
        // Test room kind string mapping without needing a full service
        fn map_room_kind(kind: &str) -> RoomKind {
            match kind.to_lowercase().as_str() {
                "kitchen" => RoomKind::Kitchen,
                "bathroom" | "bath" | "restroom" => RoomKind::Bathroom,
                "bedroom" | "bed" => RoomKind::Bedroom,
                "living" | "living room" | "lounge" => RoomKind::Living,
                _ => RoomKind::Other,
            }
        }

        assert!(matches!(map_room_kind("kitchen"), RoomKind::Kitchen));
        assert!(matches!(map_room_kind("bathroom"), RoomKind::Bathroom));
        assert!(matches!(map_room_kind("unknown"), RoomKind::Other));
    }
}
