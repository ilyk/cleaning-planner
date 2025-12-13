//! HTTP request handlers

use crate::state::AppState;
use axum::{
    extract::{ws::WebSocketUpgrade, Query, State},
    http::HeaderMap,
    response::{IntoResponse, Response},
    Extension, Json,
};
use cleanflow_auth::AuthExtension;
use cleanflow_protocol;
use cleanflow_telemetry::Metrics;
use serde::{Deserialize, Serialize};
use std::sync::Arc;
use tracing;
use uuid::Uuid;

/// Health check handler
pub async fn health_check() -> impl IntoResponse {
    Json(serde_json::json!({
        "status": "ok",
        "service": "cleanflow-server",
        "version": "2025.10.29"
    }))
}

/// Detailed health check with feature flags and versions
pub async fn health_details_check(
    State(state): State<AppState>,
) -> impl IntoResponse {
    let mut features: Vec<&str> = Vec::new();
    
    #[cfg(feature = "openai_realtime")]
    features.push("openai_realtime");
    
    #[cfg(feature = "use-llm-security")]
    features.push("use-llm-security");
    
    #[cfg(feature = "use-pdf")]
    features.push("use-pdf");
    
    #[cfg(feature = "use-path-security")]
    features.push("use-path-security");
    
    #[cfg(feature = "use-blockchain-runtime")]
    features.push("use-blockchain-runtime");
    
    #[cfg(feature = "use-worker-capabilities")]
    features.push("use-worker-capabilities");
    
    #[cfg(feature = "use-module-registry")]
    features.push("use-module-registry");
    
    #[cfg(feature = "use-threat-intel")]
    features.push("use-threat-intel");
    
    #[cfg(feature = "use-quantum-shield")]
    features.push("use-quantum-shield");

    Json(serde_json::json!({
        "status": "ok",
        "service": "cleanflow-server",
        "version": "2025.10.29",
        "features": features,
        "policy_version": state.config.versions.guardrail_policy_version,
        "prompt_version": state.config.versions.prompt_version,
    }))
}

/// Metrics handler
pub async fn metrics_handler(State(_state): State<AppState>) -> impl IntoResponse {
    match Metrics::export() {
        Ok(metrics_text) => (axum::http::StatusCode::OK, metrics_text).into_response(),
        Err(e) => {
            tracing::error!(error = %e, "Failed to export metrics");
            (axum::http::StatusCode::INTERNAL_SERVER_ERROR, "Failed to export metrics").into_response()
        }
    }
}

#[derive(Debug, Serialize)]
pub struct CreateSessionResponse {
    pub session_id: String,
    pub stream_url: String,
}

/// Create session handler
pub async fn create_session(
    State(state): State<AppState>,
    Extension(auth): Extension<AuthExtension>,
    headers: HeaderMap,
) -> Result<Json<CreateSessionResponse>, crate::errors::CleanFlowError> {
    tracing::info!(
        user_id = auth.claims.sub,
        x_prompt_version = headers.get("x-prompt-version").and_then(|v| v.to_str().ok()),
        x_policy_version = headers.get("x-policy-version").and_then(|v| v.to_str().ok()),
        "Creating session"
    );

    let session_id = state
        .session_manager
        .create_session(&auth.claims.sub, &auth.claims.home_id)
        .await
        .map_err(|e| crate::errors::CleanFlowError::Internal(e.to_string()))?;

    let stream_url = format!("/v1/clara/stream?sessionId={}", session_id);

    Ok(Json(CreateSessionResponse {
        session_id,
        stream_url,
    }))
}

#[derive(Debug, Serialize)]
pub struct StartTurnResponse {
    pub turn_id: String,
    pub stream_url: String,
}

#[derive(Debug, Deserialize)]
pub struct StartTurnRequest {
    pub session_id: String,
}

/// Start turn handler
pub async fn start_turn(
    State(state): State<AppState>,
    Extension(auth): Extension<AuthExtension>,
    headers: HeaderMap,
    Json(req): Json<StartTurnRequest>,
) -> Result<Json<StartTurnResponse>, crate::errors::CleanFlowError> {
    tracing::info!(
        user_id = auth.claims.sub,
        session_id = req.session_id,
        x_prompt_version = headers.get("x-prompt-version").and_then(|v| v.to_str().ok()),
        x_policy_version = headers.get("x-policy-version").and_then(|v| v.to_str().ok()),
        "Starting turn"
    );

    let turn_id = state
        .session_manager
        .start_turn(&req.session_id)
        .await
        .map_err(|e| match e {
            cleanflow_session::SessionError::RateLimited(msg) => crate::errors::CleanFlowError::RateLimited(msg),
            cleanflow_session::SessionError::SessionNotFound(msg) => crate::errors::CleanFlowError::SessionNotFound(msg),
            _ => crate::errors::CleanFlowError::Internal(e.to_string()),
        })?;

    let stream_url = format!(
        "/v1/clara/stream?sessionId={}&turnId={}",
        req.session_id, turn_id
    );

    Ok(Json(StartTurnResponse {
        turn_id,
        stream_url,
    }))
}

#[derive(Debug, Deserialize)]
pub struct StreamQuery {
    #[serde(rename = "sessionId")]
    pub session_id: String,
    #[serde(rename = "turnId")]
    pub turn_id: Option<String>,
}

/// WebSocket upgrade handler for Clara voice streaming
pub async fn websocket_handler(
    ws: WebSocketUpgrade,
    State(state): State<AppState>,
    Extension(auth): Extension<AuthExtension>,
    Query(query): Query<StreamQuery>,
) -> Result<Response, crate::errors::CleanFlowError> {
    let session_id = query.session_id.clone();

    // Validate session exists and is valid for connection
    state
        .session_manager
        .validate_session(&session_id)
        .await
        .map_err(|e| match e {
            cleanflow_session::SessionError::SessionNotFound(msg) => crate::errors::CleanFlowError::SessionNotFound(msg),
            cleanflow_session::SessionError::SessionAlreadyConnected(msg) => crate::errors::CleanFlowError::SessionAlreadyConnected(msg),
            _ => crate::errors::CleanFlowError::Internal(e.to_string()),
        })?;

    // Get or create turn_id
    let turn_id = match query.turn_id {
        Some(tid) => tid,
        None => {
            // Create a new turn
            state
                .session_manager
                .start_turn(&session_id)
                .await
                .map_err(|e| match e {
                    cleanflow_session::SessionError::RateLimited(msg) => crate::errors::CleanFlowError::RateLimited(msg),
                    cleanflow_session::SessionError::SessionNotFound(msg) => crate::errors::CleanFlowError::SessionNotFound(msg),
                    _ => crate::errors::CleanFlowError::Internal(e.to_string()),
                })?
        }
    };

    tracing::info!(
        session_id = %session_id,
        turn_id = %turn_id,
        user_id = %auth.claims.sub,
        "WebSocket upgrade request"
    );

    // Extract all needed values before the closure to ensure they are Send + 'static
    let session_manager = state.session_manager.clone();
    let config = state.config.clone();
    let store = state.store.clone();
    let metrics = state.metrics.clone();
    let home_id = auth.claims.home_id.clone();

    // Use on_upgrade to handle the WebSocket connection
    Ok(ws.on_upgrade(move |socket| async move {
        cleanflow_stream::handle_websocket(
            socket,
            session_id,
            turn_id,
            session_manager,
            config,
            store,
            metrics,
            home_id,
        )
        .await
    }))
}

/// Cancel turn handler
pub async fn cancel_turn(
    State(_state): State<AppState>,
    Extension(auth): Extension<AuthExtension>,
    Json(req): Json<CancelTurnRequest>,
) -> Result<Json<CancelTurnResponse>, crate::errors::CleanFlowError> {
    tracing::info!(
        user_id = auth.claims.sub,
        turn_id = req.turn_id,
        "Canceling turn"
    );

    // TODO: Implement turn cancellation logic
    Ok(Json(CancelTurnResponse { ok: true }))
}

#[derive(Debug, Deserialize)]
pub struct CancelTurnRequest {
    pub turn_id: String,
}

#[derive(Debug, Serialize)]
pub struct CancelTurnResponse {
    pub ok: bool,
}

