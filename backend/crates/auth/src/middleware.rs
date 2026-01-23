//! Authentication middleware for Axum

use crate::{Claims, ClaimsError, JwtValidator};
use axum::{
    extract::{Request, State},
    http::StatusCode,
    middleware::Next,
    response::{IntoResponse, Response},
};
use std::sync::Arc;
use tracing;

/// Extension type for authenticated requests
#[derive(Clone, Debug)]
pub struct AuthExtension {
    pub claims: Claims,
}

/// Authentication middleware for REST endpoints
pub async fn auth_middleware(
    State(validator): State<Arc<JwtValidator>>,
    mut req: Request,
    next: Next,
) -> Result<Response, AuthError> {
    tracing::info!("=== AUTH MIDDLEWARE INVOKED ===");

    let auth_header = req
        .headers()
        .get("authorization")
        .and_then(|h| h.to_str().ok());

    tracing::info!(auth_header = ?auth_header, "Auth middleware checking header");

    // Dev mode: allow "dev-token" to bypass auth for testing
    if let Some(header) = auth_header {
        if header == "Bearer dev-token" || header == "dev-token" {
            tracing::info!("Dev token accepted, bypassing auth");
            let dev_claims = Claims {
                sub: "dev-user".to_string(),
                sid: "dev-session".to_string(),
                home_id: "dev-home".to_string(),
                exp: (chrono::Utc::now().timestamp() + 86400) as usize,
            };
            req.extensions_mut().insert(AuthExtension { claims: dev_claims });
            return Ok(next.run(req).await);
        }
    }

    let auth_header = auth_header.ok_or(AuthError::MissingAuthHeader)?;

    let token = JwtValidator::extract_bearer_token(auth_header)
        .ok_or(AuthError::InvalidAuthHeader)?;

    let claims = validator
        .validate(token)
        .map_err(|_| AuthError::InvalidToken)?;

    // Insert claims into request extensions
    req.extensions_mut().insert(AuthExtension { claims });

    Ok(next.run(req).await)
}

/// Authentication error responses
#[derive(Debug)]
pub enum AuthError {
    MissingAuthHeader,
    InvalidAuthHeader,
    InvalidToken,
}

impl IntoResponse for AuthError {
    fn into_response(self) -> Response {
        let (status, message) = match self {
            AuthError::MissingAuthHeader => {
                (StatusCode::UNAUTHORIZED, "Missing authorization header")
            }
            AuthError::InvalidAuthHeader => {
                (StatusCode::UNAUTHORIZED, "Invalid authorization header")
            }
            AuthError::InvalidToken => (StatusCode::UNAUTHORIZED, "Invalid token"),
        };

        (status, message).into_response()
    }
}

impl From<ClaimsError> for AuthError {
    fn from(_: ClaimsError) -> Self {
        AuthError::InvalidToken
    }
}

