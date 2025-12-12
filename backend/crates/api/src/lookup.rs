//! Lookup and metadata API handlers

use axum::{
    extract::{Path, Query, State},
    response::Json,
    Extension,
};
use clara_auth::AuthExtension;
use clara_domain::models::*;
use clara_domain::services::HomeProfile;
use serde::Deserialize;
use std::sync::Arc;
use tracing::info;
use crate::state::AppState;

/// Get home profile with members and rooms
pub async fn get_home(
    State(state): State<AppState>,
    Extension(auth): Extension<AuthExtension>,
    Path(home_id): Path<String>,
) -> Result<Json<HomeProfile>, crate::errors::CleanFlowError> {
    info!(
        user_id = %auth.claims.sub,
        home_id = %home_id,
        "Getting home profile"
    );
    
    // Validate home_id matches user's home
    if home_id != auth.claims.home_id {
        return Err(crate::errors::CleanFlowError::Forbidden(
            "Access denied to home".to_string(),
        ));
    }
    
    let home = state.real_lookup_service.get_home(&home_id).await?
        .ok_or_else(|| crate::errors::CleanFlowError::NotFound("Home not found".to_string()))?;
    Ok(Json(home))
}

/// List task templates for a home
pub async fn get_task_templates(
    State(state): State<AppState>,
    Extension(auth): Extension<AuthExtension>,
    Query(params): Query<TaskTemplatesQuery>,
) -> Result<Json<Vec<TaskTemplate>>, crate::errors::CleanFlowError> {
    info!(
        user_id = %auth.claims.sub,
        home_id = %params.home_id,
        "Getting task templates"
    );
    
    // Validate home_id matches user's home
    if params.home_id != auth.claims.home_id {
        return Err(crate::errors::CleanFlowError::Forbidden(
            "Access denied to home".to_string(),
        ));
    }
    
    let templates = state.real_lookup_service.get_task_templates(&params.home_id).await?;
    Ok(Json(templates))
}

/// Query parameters for task templates
#[derive(Debug, Deserialize)]
pub struct TaskTemplatesQuery {
    pub home_id: String,
}

// Using HomeProfile from clara_domain::services

/// Service traits for lookup operations
#[async_trait::async_trait]
pub trait HomeService: Send + Sync {
    async fn get_home(&self, home_id: &str) -> Result<HomeProfile, crate::errors::CleanFlowError>;
}

#[async_trait::async_trait]
pub trait TaskTemplateService: Send + Sync {
    async fn get_templates(&self, home_id: &str) -> Result<Vec<TaskTemplate>, crate::errors::CleanFlowError>;
}

/// Mock implementations
pub struct MockHomeService;

#[async_trait::async_trait]
impl HomeService for MockHomeService {
    async fn get_home(&self, home_id: &str) -> Result<HomeProfile, crate::errors::CleanFlowError> {
        // Mock implementation
        let home = Home {
            id: home_id.to_string(),
            owner_user_id: "user_123".to_string(),
            name: "Sample Home".to_string(),
            tz: "America/Los_Angeles".to_string(),
            locale: "en-US".to_string(),
            created_at: chrono::Utc::now(),
            updated_at: chrono::Utc::now(),
            metadata: None,
        };
        
        let members = vec![
            Member {
                id: "m_dad".to_string(),
                home_id: home_id.to_string(),
                name: "Alex".to_string(),
                role: MemberRole::Adult,
                avatar_url: None,
                created_at: chrono::Utc::now(),
            },
            Member {
                id: "m_kid".to_string(),
                home_id: home_id.to_string(),
                name: "Sam".to_string(),
                role: MemberRole::Kid,
                avatar_url: None,
                created_at: chrono::Utc::now(),
            },
        ];
        
        let rooms = vec![
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
        ];
        
        Ok(HomeProfile {
            home,
            members,
            rooms,
        })
    }
}

pub struct MockTaskTemplateService;

#[async_trait::async_trait]
impl TaskTemplateService for MockTaskTemplateService {
    async fn get_templates(&self, _home_id: &str) -> Result<Vec<TaskTemplate>, crate::errors::CleanFlowError> {
        // Mock implementation
        let templates = vec![
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
        ];
        
        Ok(templates)
    }
}
