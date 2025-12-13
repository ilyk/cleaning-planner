//! CleanFlow-specific API handlers (home registration, initial plan)

use axum::{
    extract::{State},
    response::Json,
    Extension,
};
use cleanflow_auth::AuthExtension;
use cleanflow_domain::models::{GeneratePlanRequest, GeneratePlanResponse, PlanMode};
use serde::{Deserialize, Serialize};
use std::sync::Arc;
use tracing::info;

use crate::state::AppState;

/// Request body for home registration/update
#[derive(Debug, Deserialize)]
pub struct HomeRegistrationRequest {
    pub home_id: String,
    pub profile: serde_json::Value,
}

/// Response for home registration
#[derive(Debug, Serialize)]
pub struct HomeRegistrationResponse {
    pub home_id: String,
}

/// Register or update a home profile for CleanFlow
pub async fn register_home(
    State(_state): State<AppState>,
    Extension(auth): Extension<AuthExtension>,
    Json(request): Json<HomeRegistrationRequest>,
) -> Result<Json<HomeRegistrationResponse>, crate::errors::CleanFlowError> {
    info!(
        user_id = %auth.claims.sub,
        home_id = %request.home_id,
        "Registering CleanFlow home profile",
    );

    // Enforce that the caller can only modify their own home
    if request.home_id != auth.claims.home_id {
        return Err(crate::errors::CleanFlowError::Forbidden(
            "Access denied to home".to_string(),
        ));
    }

    // TODO: Persist profile into homes/rooms tables using a dedicated repository
    // For now, this endpoint simply validates auth and echoes back the home_id.

    Ok(Json(HomeRegistrationResponse {
        home_id: request.home_id,
    }))
}

/// Request body for CleanFlow initial plan generation
#[derive(Debug, Deserialize)]
pub struct InitialPlanRequest {
    pub home_id: String,
    pub date: chrono::NaiveDate,
    pub mode: PlanMode,
    /// Optional raw profile payload to provide additional context
    pub profile: Option<serde_json::Value>,
}

/// Generate an initial plan for a home using existing plan service
pub async fn generate_initial_plan(
    State(state): State<AppState>,
    Extension(auth): Extension<AuthExtension>,
    Json(request): Json<InitialPlanRequest>,
) -> Result<Json<GeneratePlanResponse>, crate::errors::CleanFlowError> {
    info!(
        user_id = %auth.claims.sub,
        home_id = %request.home_id,
        date = %request.date,
        mode = ?request.mode,
        "Generating CleanFlow initial plan",
    );

    if request.home_id != auth.claims.home_id {
        return Err(crate::errors::CleanFlowError::Forbidden(
            "Access denied to home".to_string(),
        ));
    }

    // Reuse existing plan generation pipeline; additional context from profile
    // can later be threaded into constraints/tools.
    let plan_request = GeneratePlanRequest {
        home_id: request.home_id.clone(),
        date: request.date,
        mode: request.mode,
        constraints: None,
        client: None,
    };

    let response = state
        .real_plan_service
        .generate_plan(plan_request, None, None)
        .await?;

    Ok(Json(response))
}

/// Request body for plan optimization
#[derive(Debug, serde::Deserialize)]
pub struct OptimizePlanRequest {
    pub home_id: String,
    pub plan_id: String,
    pub mode: Option<String>,
    pub reasons: Option<String>,
}

/// Optimize an existing plan for a home
pub async fn optimize_plan(
    State(state): State<AppState>,
    Extension(auth): Extension<AuthExtension>,
    Json(request): Json<OptimizePlanRequest>,
) -> Result<Json<GeneratePlanResponse>, crate::errors::CleanFlowError> {
    if request.home_id != auth.claims.home_id {
        return Err(crate::errors::CleanFlowError::Forbidden(
            "Access denied to home".to_string(),
        ));
    }

    let mode = match request
        .mode
        .as_deref()
        .unwrap_or("gentle")
        .to_lowercase()
        .as_str()
    {
        "aggressive" => cleanflow_domain::services::OptimizationMode::Aggressive,
        _ => cleanflow_domain::services::OptimizationMode::Gentle,
    };

    let response = state
        .cleanflow_optimizer
        .optimize_plan(
            &request.home_id,
            &request.plan_id,
            mode,
            request.reasons.clone(),
        )
        .await?;

    Ok(Json(response))
}

/// Query parameters for fetching suggestions
#[derive(Debug, serde::Deserialize)]
pub struct SuggestionsQuery {
    pub home_id: String,
    pub plan_id: Option<String>,
}

/// Response for suggestions list
#[derive(Debug, serde::Serialize)]
pub struct SuggestionsResponse {
    pub home_id: String,
    pub plan_id: Option<String>,
    pub suggestions: Vec<SuggestionPayload>,
}

#[derive(Debug, serde::Serialize)]
pub struct SuggestionPayload {
    pub id: String,
    pub text: String,
    pub confidence: i32,
    pub action: String,
    pub source: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub target: Option<serde_json::Value>,
    pub state: SuggestionState,
}

#[derive(Debug, serde::Serialize)]
pub struct SuggestionState {
    pub accepted: bool,
    pub dismissed: bool,
    pub applied_locally: bool,
}

/// Get suggestions for a home/plan using LLM analysis
pub async fn get_suggestions(
    State(state): State<AppState>,
    Extension(auth): Extension<AuthExtension>,
    axum::extract::Query(query): axum::extract::Query<SuggestionsQuery>,
) -> Result<Json<SuggestionsResponse>, crate::errors::CleanFlowError> {
    if query.home_id != auth.claims.home_id {
        return Err(crate::errors::CleanFlowError::Forbidden(
            "Access denied to home".to_string(),
        ));
    }

    info!(
        home_id = %query.home_id,
        plan_id = ?query.plan_id,
        "Fetching LLM-generated suggestions"
    );

    // Generate suggestions using LLM service
    let suggestions = state
        .cleanflow_suggestion_service
        .generate_suggestions(&query.home_id, query.plan_id.as_deref())
        .await
        .map_err(|e| {
            tracing::error!(error = %e, "Failed to generate suggestions");
            crate::errors::CleanFlowError::Internal(e.to_string())
        })?;

    // Convert domain suggestions to API response format
    let payloads: Vec<SuggestionPayload> = suggestions
        .into_iter()
        .map(|s| SuggestionPayload {
            id: s.id,
            text: s.text,
            confidence: s.confidence,
            action: s.action,
            source: s.source,
            target: s.target.map(|t| {
                serde_json::json!({
                    "task_id": t.task_id,
                    "field": t.field,
                    "proposed_value": t.proposed_value
                })
            }),
            state: SuggestionState {
                accepted: s.state.accepted,
                dismissed: s.state.dismissed,
                applied_locally: s.state.applied_locally,
            },
        })
        .collect();

    Ok(Json(SuggestionsResponse {
        home_id: query.home_id,
        plan_id: query.plan_id,
        suggestions: payloads,
    }))
}
