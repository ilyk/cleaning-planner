//! Idempotency middleware for request deduplication

use crate::state::AppState;
use axum::{
    extract::{Request, State},
    http::StatusCode,
    middleware::Next,
    response::{IntoResponse, Response},
};
use clara_domain::services::CachedResponse;
use tracing::{info, warn};

/// Idempotency key header name
const IDEMPOTENCY_KEY_HEADER: &str = "idempotency-key";

/// Middleware to handle idempotency for create/mutate operations
/// Uses AppState directly since IdempotencyStore is part of AppState
pub async fn idempotency_middleware(
    State(state): State<AppState>,
    req: Request,
    next: Next,
) -> Result<Response, IdempotencyError> {
    // Only apply idempotency to POST/PATCH requests
    if !matches!(req.method(), &axum::http::Method::POST | &axum::http::Method::PATCH) {
        return Ok(next.run(req).await);
    }

    // Extract idempotency key from headers
    let idempotency_key = match req.headers().get(IDEMPOTENCY_KEY_HEADER) {
        Some(value) => match value.to_str() {
            Ok(key) => key.to_string(),
            Err(_) => {
                warn!("Invalid idempotency key header");
                return Ok(next.run(req).await);
            }
        },
        None => {
            // No idempotency key provided, proceed normally
            return Ok(next.run(req).await);
        }
    };

    // Generate request hash for deduplication
    // Simplified: just use empty JSON for now
    let request_body = serde_json::json!({});

    // Check if we've seen this request before
    let idempotency_store = &state.idempotency_store;
    match idempotency_store.check_idempotent(&idempotency_key, &request_body).await {
        Ok(Some(cached_response)) => {
            info!(
                idempotency_key = %idempotency_key,
                "Returning cached response for idempotent request"
            );

            // Return cached response
            return Ok(axum::response::Response::builder()
                .status(cached_response.status)
                .header("content-type", "application/json")
                .body(axum::body::Body::from(cached_response.body.to_string()))
                .unwrap()
                .into_response());
        }
        Ok(None) => {
            // New request, proceed normally
            info!(
                idempotency_key = %idempotency_key,
                "Processing new idempotent request"
            );
        }
        Err(e) => {
            warn!(
                error = %e,
                idempotency_key = %idempotency_key,
                "Failed to check idempotency, proceeding normally"
            );
        }
    }

    // Process the request
    let response = next.run(req).await;

    // Store response for future idempotency checks
    if response.status().is_success() {
        let cached_response = CachedResponse {
            status: response.status().as_u16(),
            body: serde_json::json!({}),
        };

        if let Err(e) = idempotency_store
            .store_response(&idempotency_key, &request_body, &cached_response)
            .await
        {
            warn!(
                error = %e,
                idempotency_key = %idempotency_key,
                "Failed to store idempotency response"
            );
        }
    }

    Ok(response)
}

/// Idempotency middleware errors
#[derive(Debug)]
pub enum IdempotencyError {
    Internal(String),
}

impl IntoResponse for IdempotencyError {
    fn into_response(self) -> Response {
        let (status, message) = match self {
            IdempotencyError::Internal(msg) => (StatusCode::INTERNAL_SERVER_ERROR, msg),
        };
        (status, message).into_response()
    }
}

/// Helper to check if a request should be idempotent
pub fn should_be_idempotent(method: &axum::http::Method, path: &str) -> bool {
    matches!(method, &axum::http::Method::POST | &axum::http::Method::PATCH)
        && (path.starts_with("/v1/plan/") || path.starts_with("/v1/family/") || path.starts_with("/v1/telemetry/"))
}
