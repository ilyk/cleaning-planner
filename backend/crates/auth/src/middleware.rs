//! Authentication middleware for Axum

use crate::{Claims, ClaimsError, JwtValidator};
use axum::{
    extract::{Request, State},
    http::StatusCode,
    middleware::Next,
    response::{IntoResponse, Response},
};
use std::sync::Arc;

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
    let auth_header = req
        .headers()
        .get("authorization")
        .and_then(|h| h.to_str().ok())
        .ok_or(AuthError::MissingAuthHeader)?;

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

