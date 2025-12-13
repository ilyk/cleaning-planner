//! Onboarding API handlers - extract home data from Clara conversations

use axum::{
    extract::State,
    response::Json,
    Extension,
};
use cleanflow_auth::AuthExtension;
use cleanflow_domain::services::{ConversationMessage, ExtractHomeRequest, ExtractHomeResponse};
use serde::{Deserialize, Serialize};
use tracing::info;

use crate::state::AppState;

/// Request body for extracting home data from conversation
#[derive(Debug, Deserialize)]
pub struct ExtractFromConversationRequest {
    /// Session ID from Clara onboarding
    pub session_id: String,
    /// Conversation transcript (list of messages)
    pub conversation: Vec<ConversationMessageDto>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ConversationMessageDto {
    pub role: String,
    pub content: String,
}

/// Response for home extraction
#[derive(Debug, Serialize)]
pub struct ExtractFromConversationResponse {
    /// Generated home ID
    pub home_id: String,
    /// Whether extraction was successful
    pub success: bool,
    /// Summary of extracted data
    pub summary: ExtractedSummary,
    /// Optional message
    #[serde(skip_serializing_if = "Option::is_none")]
    pub message: Option<String>,
}

#[derive(Debug, Serialize)]
pub struct ExtractedSummary {
    pub rooms_count: usize,
    pub members_count: usize,
    pub pets_count: usize,
    pub has_preferences: bool,
    pub has_problem_areas: bool,
}

/// Extract structured home data from Clara onboarding conversation
///
/// POST /v1/onboarding/extract
///
/// This endpoint is called after Clara completes the onboarding conversation
/// (when [ONBOARDING_COMPLETE] marker is detected). It uses LLM to extract
/// structured home data from the conversation transcript and persists it to
/// the database.
pub async fn extract_from_conversation(
    State(state): State<AppState>,
    Extension(auth): Extension<AuthExtension>,
    Json(request): Json<ExtractFromConversationRequest>,
) -> Result<Json<ExtractFromConversationResponse>, crate::errors::CleanFlowError> {
    info!(
        user_id = %auth.claims.sub,
        session_id = %request.session_id,
        messages = request.conversation.len(),
        "Extracting home data from onboarding conversation",
    );

    // Convert DTO to domain model
    let conversation_messages: Vec<ConversationMessage> = request
        .conversation
        .into_iter()
        .map(|m| ConversationMessage {
            role: m.role,
            content: m.content,
        })
        .collect();

    // Build extraction request
    let extract_request = ExtractHomeRequest {
        user_id: auth.claims.sub.clone(),
        session_id: request.session_id,
        conversation_transcript: conversation_messages,
    };

    // Call extraction service
    let result = state
        .home_extraction_service
        .extract_from_conversation(extract_request)
        .await
        .map_err(|e| {
            tracing::error!(error = %e, "Failed to extract home data");
            crate::errors::CleanFlowError::Internal(format!("Extraction failed: {}", e))
        })?;

    info!(
        home_id = %result.home_id,
        rooms = result.extracted_data.rooms.len(),
        members = result.extracted_data.members.len(),
        "Home data extraction complete"
    );

    Ok(Json(ExtractFromConversationResponse {
        home_id: result.home_id,
        success: result.success,
        summary: ExtractedSummary {
            rooms_count: result.extracted_data.rooms.len(),
            members_count: result.extracted_data.members.len(),
            pets_count: result.extracted_data.pets.len(),
            has_preferences: !result.extracted_data.preferences.preferred_cleaning_times.is_empty()
                || result.extracted_data.preferences.quiet_hours.is_some()
                || result.extracted_data.preferences.cleaning_style.is_some(),
            has_problem_areas: !result.extracted_data.problem_areas.is_empty(),
        },
        message: result.message,
    }))
}

/// Request body for manual home setup (wizard fallback)
#[derive(Debug, Deserialize)]
pub struct ManualHomeSetupRequest {
    /// Optional home name
    pub home_name: Option<String>,
    /// List of rooms
    pub rooms: Vec<ManualRoomDto>,
    /// List of household members
    pub members: Vec<ManualMemberDto>,
    /// List of pets (optional)
    #[serde(default)]
    pub pets: Vec<ManualPetDto>,
    /// Preferences (optional)
    #[serde(default)]
    pub preferences: ManualPreferencesDto,
}

#[derive(Debug, Deserialize)]
pub struct ManualRoomDto {
    pub name: String,
    /// Room type: kitchen, bathroom, bedroom, living, other
    pub kind: String,
    #[serde(default)]
    pub notes: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct ManualMemberDto {
    pub name: String,
    /// Role: adult, kid, teen
    pub role: String,
}

#[derive(Debug, Deserialize)]
pub struct ManualPetDto {
    /// Pet type: dog, cat, bird, etc
    pub pet_type: String,
    pub name: Option<String>,
    /// Shedding level: high, medium, low, none
    pub shedding_level: Option<String>,
}

#[derive(Debug, Default, Deserialize)]
pub struct ManualPreferencesDto {
    #[serde(default)]
    pub preferred_cleaning_times: Vec<String>,
    pub quiet_hours: Option<String>,
    #[serde(default)]
    pub busy_days: Vec<String>,
    pub cleaning_style: Option<String>,
}

/// Response for manual home setup
#[derive(Debug, Serialize)]
pub struct ManualHomeSetupResponse {
    pub home_id: String,
    pub success: bool,
}

/// Create home from manual wizard setup (Clara fallback)
///
/// POST /v1/onboarding/setup
///
/// This endpoint is used when user opts to set up their home manually
/// instead of using Clara conversational onboarding.
pub async fn manual_home_setup(
    State(state): State<AppState>,
    Extension(auth): Extension<AuthExtension>,
    Json(request): Json<ManualHomeSetupRequest>,
) -> Result<Json<ManualHomeSetupResponse>, crate::errors::CleanFlowError> {
    info!(
        user_id = %auth.claims.sub,
        rooms = request.rooms.len(),
        members = request.members.len(),
        "Creating home from manual setup",
    );

    // Build extracted data from manual input
    let extracted = cleanflow_domain::services::ExtractedHomeData {
        home_name: request.home_name,
        rooms: request
            .rooms
            .into_iter()
            .map(|r| cleanflow_domain::services::home_extraction_service::ExtractedRoom {
                name: r.name,
                kind: r.kind,
                notes: r.notes,
            })
            .collect(),
        members: request
            .members
            .into_iter()
            .map(|m| cleanflow_domain::services::home_extraction_service::ExtractedMember {
                name: m.name,
                role: m.role,
                notes: None,
            })
            .collect(),
        pets: request
            .pets
            .into_iter()
            .map(|p| cleanflow_domain::services::home_extraction_service::ExtractedPet {
                pet_type: p.pet_type,
                name: p.name,
                shedding_level: p.shedding_level,
                rooms_frequent: vec![],
            })
            .collect(),
        preferences: cleanflow_domain::services::home_extraction_service::ExtractedPreferences {
            preferred_cleaning_times: request.preferences.preferred_cleaning_times,
            quiet_hours: request.preferences.quiet_hours,
            busy_days: request.preferences.busy_days,
            cleaning_style: request.preferences.cleaning_style,
        },
        problem_areas: vec![],
    };

    // Use extraction service to persist (reusing the same persistence logic)
    let extract_request = ExtractHomeRequest {
        user_id: auth.claims.sub.clone(),
        session_id: format!("manual_{}", uuid::Uuid::new_v4()),
        conversation_transcript: vec![], // Empty for manual setup
    };

    // For manual setup, we need a different code path since there's no conversation
    // Let's directly create the home using the database pool
    let home_id = create_home_from_manual_data(&state.db_pool, &auth.claims.sub, &extracted)
        .await
        .map_err(|e| {
            tracing::error!(error = %e, "Failed to create home from manual data");
            crate::errors::CleanFlowError::Internal(format!("Setup failed: {}", e))
        })?;

    info!(home_id = %home_id, "Manual home setup complete");

    Ok(Json(ManualHomeSetupResponse {
        home_id,
        success: true,
    }))
}

/// Helper function to create home from manual data
async fn create_home_from_manual_data(
    db_pool: &sqlx::PgPool,
    user_id: &str,
    data: &cleanflow_domain::services::ExtractedHomeData,
) -> anyhow::Result<String> {
    let home_id = format!("home_{}", &uuid::Uuid::new_v4().to_string()[..12]);
    let home_name = data.home_name.clone().unwrap_or_else(|| "My Home".to_string());
    let prefs_json = serde_json::to_value(&data.preferences).ok();

    // Insert home - using runtime query to avoid enum type issues
    sqlx::query(
        "INSERT INTO homes (id, owner_user_id, name, tz, locale, metadata, created_at, updated_at)
         VALUES ($1, $2, $3, 'UTC', 'en-US', $4, NOW(), NOW())"
    )
    .bind(&home_id)
    .bind(user_id)
    .bind(&home_name)
    .bind(&prefs_json)
    .execute(db_pool)
    .await?;

    // Insert rooms - using runtime query with enum cast
    for room in &data.rooms {
        let room_id = format!("room_{}", &uuid::Uuid::new_v4().to_string()[..8]);
        let room_kind = match room.kind.to_lowercase().as_str() {
            "kitchen" => "kitchen",
            "bathroom" | "bath" => "bathroom",
            "bedroom" | "bed" => "bedroom",
            "living" | "living room" => "living",
            _ => "other",
        };
        let metadata = room.notes.as_ref().map(|n| serde_json::json!({"notes": n}));

        sqlx::query(
            "INSERT INTO rooms (id, home_id, name, kind, metadata)
             VALUES ($1, $2, $3, $4::room_kind, $5)"
        )
        .bind(&room_id)
        .bind(&home_id)
        .bind(&room.name)
        .bind(room_kind)
        .bind(&metadata)
        .execute(db_pool)
        .await?;
    }

    // Insert members - using runtime query with enum cast
    for member in &data.members {
        let member_id = format!("member_{}", &uuid::Uuid::new_v4().to_string()[..8]);
        let member_role = match member.role.to_lowercase().as_str() {
            "adult" | "parent" => "adult",
            "kid" | "child" | "teen" | "teenager" => "kid",
            "guest" => "guest",
            _ => "adult",
        };

        sqlx::query(
            "INSERT INTO members (id, home_id, name, role, created_at)
             VALUES ($1, $2, $3, $4::member_role, NOW())"
        )
        .bind(&member_id)
        .bind(&home_id)
        .bind(&member.name)
        .bind(member_role)
        .execute(db_pool)
        .await?;
    }

    // Store pets in home metadata
    if !data.pets.is_empty() {
        let pets_json = serde_json::to_value(&data.pets)?;
        sqlx::query(
            "UPDATE homes
             SET metadata = COALESCE(metadata, '{}'::jsonb) || jsonb_build_object('pets', $1::jsonb)
             WHERE id = $2"
        )
        .bind(&pets_json)
        .bind(&home_id)
        .execute(db_pool)
        .await?;
    }

    Ok(home_id)
}
