//! Plan API handlers

use axum::{
    extract::{Path, Query, State},
    http::HeaderMap,
    response::Json,
    Extension,
};
use cleanflow_auth::AuthExtension;
use cleanflow_domain::models::*;
use serde::Deserialize;
use tracing::info;
use crate::state::AppState;

fn header_str(headers: &HeaderMap, key: &str) -> Option<String> {
    headers.get(key).and_then(|v| v.to_str().ok()).map(|s| s.to_string())
}

/// Generate or fetch a plan
pub async fn generate_plan(
    State(state): State<AppState>,
    Extension(auth): Extension<AuthExtension>,
    headers: HeaderMap,
    Json(request): Json<GeneratePlanRequest>,
) -> Result<Json<GeneratePlanResponse>, crate::errors::CleanFlowError> {
    info!(
        user_id = %auth.claims.sub,
        home_id = %request.home_id,
        date = %request.date,
        mode = ?request.mode,
        x_client_version = ?header_str(&headers, "x-client-version"),
        x_prompt_version = ?header_str(&headers, "x-prompt-version"),
        x_policy_version = ?header_str(&headers, "x-policy-version"),
        "Generating plan"
    );
    
    if request.home_id != auth.claims.home_id {
        return Err(crate::errors::CleanFlowError::Forbidden(
            "Access denied to home".to_string(),
        ));
    }
    
    let response = state.real_plan_service.generate_plan(
        request, 
        header_str(&headers, "x-prompt-version"), 
        header_str(&headers, "x-policy-version")
    ).await?;
    Ok(Json(response))
}

/// Revise a plan with user edits
pub async fn revise_plan(
    State(state): State<AppState>,
    Extension(auth): Extension<AuthExtension>,
    headers: HeaderMap,
    Json(request): Json<RevisePlanRequest>,
) -> Result<Json<GeneratePlanResponse>, crate::errors::CleanFlowError> {
    info!(
        user_id = %auth.claims.sub,
        plan_id = %request.plan_id,
        edits_count = %request.edits.len(),
        x_client_version = ?header_str(&headers, "x-client-version"),
        x_prompt_version = ?header_str(&headers, "x-prompt-version"),
        x_policy_version = ?header_str(&headers, "x-policy-version"),
        "Revising plan"
    );
    
    let response = state.real_plan_service.revise_plan(request).await?;
    Ok(Json(response))
}

/// Get a plan by ID
pub async fn get_plan(
    State(state): State<AppState>,
    Extension(auth): Extension<AuthExtension>,
    Path(plan_id): Path<String>,
) -> Result<Json<Plan>, crate::errors::CleanFlowError> {
    info!(
        user_id = %auth.claims.sub,
        plan_id = %plan_id,
        "Getting plan"
    );
    
    let plan = state
        .real_plan_service
        .get_plan(&plan_id)
        .await?
        .ok_or_else(|| crate::errors::CleanFlowError::PlanNotFound(plan_id))?;
    
    Ok(Json(plan))
}

/// List plans with pagination
pub async fn list_plans(
    State(state): State<AppState>,
    Extension(auth): Extension<AuthExtension>,
    Query(params): Query<ListPlansQuery>,
) -> Result<Json<PaginatedResponse<Plan>>, crate::errors::CleanFlowError> {
    info!(
        user_id = %auth.claims.sub,
        home_id = %params.home_id,
        "Listing plans"
    );
    
    if params.home_id != auth.claims.home_id {
        return Err(crate::errors::CleanFlowError::Forbidden(
            "Access denied to home".to_string(),
        ));
    }
    
    let pagination = PaginationParams {
        limit: params.limit,
        cursor: params.cursor,
    };
    
    let response = state
        .real_plan_service
        .list_plans(&params.home_id, params.date_from, pagination)
        .await?;
    
    Ok(Json(response))
}

/// Generate printable PDF
pub async fn generate_printable(
    State(state): State<AppState>,
    Extension(auth): Extension<AuthExtension>,
    Json(request): Json<PrintableRequest>,
) -> Result<Json<PrintableResponse>, crate::errors::CleanFlowError> {
    info!(
        user_id = %auth.claims.sub,
        plan_id = %request.plan_id,
        "Generating printable"
    );
    
    let response = state.real_printable_service.generate_printable(request).await?;
    Ok(Json(response))
}

/// Assign tasks to family members
pub async fn assign_family(
    State(state): State<AppState>,
    Extension(auth): Extension<AuthExtension>,
    Json(request): Json<FamilyAssignRequest>,
) -> Result<Json<Vec<Assignment>>, crate::errors::CleanFlowError> {
    info!(
        user_id = %auth.claims.sub,
        plan_id = %request.plan_id,
        assignments_count = %request.assignments.len(),
        "Assigning family tasks"
    );
    
    let assignments = state.real_plan_service.assign_family(request).await?;
    Ok(Json(assignments))
}

/// Record task completion/skip
pub async fn record_telemetry(
    State(state): State<AppState>,
    Extension(auth): Extension<AuthExtension>,
    Json(request): Json<TelemetryCompleteRequest>,
) -> Result<Json<TelemetryCompleteResponse>, crate::errors::CleanFlowError> {
    info!(
        user_id = %auth.claims.sub,
        task_id = %request.task_id,
        status = %request.status,
        "Recording telemetry"
    );
    
    let response = state.telemetry_service.record_telemetry(request).await?;
    Ok(Json(response))
}

/// Query parameters for list plans
#[derive(Debug, Deserialize)]
pub struct ListPlansQuery {
    pub home_id: String,
    pub date_from: Option<chrono::NaiveDate>,
    pub limit: Option<i32>,
    pub cursor: Option<String>,
}

/// Skip a task and reschedule
pub async fn skip_task(
    State(state): State<AppState>,
    Extension(auth): Extension<AuthExtension>,
    Path(task_id): Path<String>,
    Json(request): Json<SkipTaskRequest>,
) -> Result<Json<SkipTaskResponse>, crate::errors::CleanFlowError> {
    info!(
        user_id = %auth.claims.sub,
        task_id = %task_id,
        reason = ?request.reason,
        "Skipping task"
    );

    // Record skip via telemetry service
    let telemetry_request = TelemetryCompleteRequest {
        task_id: task_id.clone(),
        status: "skip".to_string(),
        duration_sec: None,
        comment: request.reason,
        source: "api".to_string(),
    };

    let telemetry_response = state.telemetry_service.record_telemetry(telemetry_request).await?;

    Ok(Json(SkipTaskResponse {
        ok: true,
        task_id,
        new_state: TaskState::Skipped,
        telemetry_id: telemetry_response.telemetry_id,
    }))
}
