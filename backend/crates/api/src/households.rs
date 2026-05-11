//! Household (Home) CRUD API handlers

use axum::{
    extract::{Path, State},
    response::Json,
    Extension,
};
use cleanflow_auth::AuthExtension;
use cleanflow_domain::models::*;
use tracing::info;

use crate::errors::CleanFlowError;
use crate::state::AppState;

/// List all households for the authenticated user
pub async fn list_households(
    State(state): State<AppState>,
    Extension(auth): Extension<AuthExtension>,
) -> Result<Json<Vec<Home>>, CleanFlowError> {
    info!(
        user_id = %auth.claims.sub,
        "Listing households"
    );

    let homes = state.home_service.list_homes(&auth.claims.sub).await?;
    Ok(Json(homes))
}

/// Get a single household by ID
pub async fn get_household(
    State(state): State<AppState>,
    Extension(auth): Extension<AuthExtension>,
    Path(home_id): Path<String>,
) -> Result<Json<Home>, CleanFlowError> {
    info!(
        user_id = %auth.claims.sub,
        home_id = %home_id,
        "Getting household"
    );

    let home = state
        .home_service
        .get_home(&home_id)
        .await?
        .ok_or_else(|| CleanFlowError::HomeNotFound(home_id.clone()))?;

    // Auth check: user must own this home or have access
    if home.owner_user_id != auth.claims.sub && home_id != auth.claims.home_id {
        return Err(CleanFlowError::Forbidden(
            "Access denied to household".to_string(),
        ));
    }

    Ok(Json(home))
}

/// Create a new household
pub async fn create_household(
    State(state): State<AppState>,
    Extension(auth): Extension<AuthExtension>,
    Json(request): Json<CreateHomeRequest>,
) -> Result<Json<Home>, CleanFlowError> {
    info!(
        user_id = %auth.claims.sub,
        home_name = %request.name,
        "Creating household"
    );

    let home = state
        .home_service
        .create_home(&auth.claims.sub, request)
        .await?;
    Ok(Json(home))
}

/// Update an existing household
pub async fn update_household(
    State(state): State<AppState>,
    Extension(auth): Extension<AuthExtension>,
    Path(home_id): Path<String>,
    Json(request): Json<UpdateHomeRequest>,
) -> Result<Json<Home>, CleanFlowError> {
    info!(
        user_id = %auth.claims.sub,
        home_id = %home_id,
        "Updating household"
    );

    // First get the home to check ownership
    let existing = state
        .home_service
        .get_home(&home_id)
        .await?
        .ok_or_else(|| CleanFlowError::HomeNotFound(home_id.clone()))?;

    // Auth check: user must own this home
    if existing.owner_user_id != auth.claims.sub {
        return Err(CleanFlowError::Forbidden(
            "Only the owner can update this household".to_string(),
        ));
    }

    let home = state
        .home_service
        .update_home(&home_id, request)
        .await?
        .ok_or_else(|| CleanFlowError::HomeNotFound(home_id))?;

    Ok(Json(home))
}

/// Delete a household
pub async fn delete_household(
    State(state): State<AppState>,
    Extension(auth): Extension<AuthExtension>,
    Path(home_id): Path<String>,
) -> Result<Json<serde_json::Value>, CleanFlowError> {
    info!(
        user_id = %auth.claims.sub,
        home_id = %home_id,
        "Deleting household"
    );

    // First get the home to check ownership
    let existing = state
        .home_service
        .get_home(&home_id)
        .await?
        .ok_or_else(|| CleanFlowError::HomeNotFound(home_id.clone()))?;

    // Auth check: user must own this home
    if existing.owner_user_id != auth.claims.sub {
        return Err(CleanFlowError::Forbidden(
            "Only the owner can delete this household".to_string(),
        ));
    }

    let deleted = state.home_service.delete_home(&home_id).await?;
    if !deleted {
        return Err(CleanFlowError::HomeNotFound(home_id));
    }

    Ok(Json(serde_json::json!({ "ok": true })))
}
