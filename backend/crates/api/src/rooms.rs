//! Room CRUD API handlers

use axum::{
    extract::{Path, Query, State},
    response::Json,
    Extension,
};
use cleanflow_auth::AuthExtension;
use cleanflow_domain::models::*;
use serde::Deserialize;
use tracing::info;

use crate::errors::CleanFlowError;
use crate::state::AppState;

/// Query parameters for listing rooms
#[derive(Debug, Deserialize)]
pub struct ListRoomsQuery {
    pub home_id: String,
}

/// List rooms for a household
pub async fn list_rooms(
    State(state): State<AppState>,
    Extension(auth): Extension<AuthExtension>,
    Query(params): Query<ListRoomsQuery>,
) -> Result<Json<Vec<Room>>, CleanFlowError> {
    info!(
        user_id = %auth.claims.sub,
        home_id = %params.home_id,
        "Listing rooms"
    );

    // Auth check: user must have access to this home
    if params.home_id != auth.claims.home_id {
        return Err(CleanFlowError::Forbidden(
            "Access denied to home".to_string(),
        ));
    }

    let rooms = state.room_service.list_rooms(&params.home_id).await?;
    Ok(Json(rooms))
}

/// Get a single room by ID
pub async fn get_room(
    State(state): State<AppState>,
    Extension(auth): Extension<AuthExtension>,
    Path(room_id): Path<String>,
) -> Result<Json<Room>, CleanFlowError> {
    info!(
        user_id = %auth.claims.sub,
        room_id = %room_id,
        "Getting room"
    );

    let room = state
        .room_service
        .get_room(&room_id)
        .await?
        .ok_or_else(|| CleanFlowError::RoomNotFound(room_id.clone()))?;

    // Auth check: user must have access to this home
    if room.home_id != auth.claims.home_id {
        return Err(CleanFlowError::Forbidden(
            "Access denied to room".to_string(),
        ));
    }

    Ok(Json(room))
}

/// Create a new room
pub async fn create_room(
    State(state): State<AppState>,
    Extension(auth): Extension<AuthExtension>,
    Json(request): Json<CreateRoomRequest>,
) -> Result<Json<Room>, CleanFlowError> {
    info!(
        user_id = %auth.claims.sub,
        home_id = %request.home_id,
        room_name = %request.name,
        "Creating room"
    );

    // Auth check: user must have access to this home
    if request.home_id != auth.claims.home_id {
        return Err(CleanFlowError::Forbidden(
            "Access denied to home".to_string(),
        ));
    }

    let room = state.room_service.create_room(request).await?;
    Ok(Json(room))
}

/// Update an existing room
pub async fn update_room(
    State(state): State<AppState>,
    Extension(auth): Extension<AuthExtension>,
    Path(room_id): Path<String>,
    Json(request): Json<UpdateRoomRequest>,
) -> Result<Json<Room>, CleanFlowError> {
    info!(
        user_id = %auth.claims.sub,
        room_id = %room_id,
        "Updating room"
    );

    // First get the room to check ownership
    let existing = state
        .room_service
        .get_room(&room_id)
        .await?
        .ok_or_else(|| CleanFlowError::RoomNotFound(room_id.clone()))?;

    // Auth check: user must have access to this home
    if existing.home_id != auth.claims.home_id {
        return Err(CleanFlowError::Forbidden(
            "Access denied to room".to_string(),
        ));
    }

    let room = state
        .room_service
        .update_room(&room_id, request)
        .await?
        .ok_or_else(|| CleanFlowError::RoomNotFound(room_id))?;

    Ok(Json(room))
}

/// Delete a room
pub async fn delete_room(
    State(state): State<AppState>,
    Extension(auth): Extension<AuthExtension>,
    Path(room_id): Path<String>,
) -> Result<Json<serde_json::Value>, CleanFlowError> {
    info!(
        user_id = %auth.claims.sub,
        room_id = %room_id,
        "Deleting room"
    );

    // First get the room to check ownership
    let existing = state
        .room_service
        .get_room(&room_id)
        .await?
        .ok_or_else(|| CleanFlowError::RoomNotFound(room_id.clone()))?;

    // Auth check: user must have access to this home
    if existing.home_id != auth.claims.home_id {
        return Err(CleanFlowError::Forbidden(
            "Access denied to room".to_string(),
        ));
    }

    let deleted = state.room_service.delete_room(&room_id).await?;
    if !deleted {
        return Err(CleanFlowError::RoomNotFound(room_id));
    }

    Ok(Json(serde_json::json!({ "ok": true })))
}
