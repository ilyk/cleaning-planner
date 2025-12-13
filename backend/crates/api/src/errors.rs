//! Centralized error handling for CleanFlow API

use axum::{
    http::StatusCode,
    response::{IntoResponse, Response},
    Json,
};
use cleanflow_domain::models::{ApiError, ErrorCode, ErrorDetails};
use serde_json::json;
use tracing::error;
use uuid::Uuid;

/// API error responses following CleanFlow spec
#[derive(Debug, thiserror::Error)]
pub enum CleanFlowError {
    #[error("Unauthorized: {0}")]
    Unauthorized(String),
    
    #[error("Forbidden: {0}")]
    Forbidden(String),
    
    #[error("Rate limited: {0}")]
    RateLimited(String),
    
    #[error("Validation failed: {0}")]
    ValidationFailed(String),
    
    #[error("Conflict: {0}")]
    Conflict(String),
    
    #[error("Not found: {0}")]
    NotFound(String),
    
    #[error("Plan not found: {0}")]
    PlanNotFound(String),
    
    #[error("Session not found: {0}")]
    SessionNotFound(String),
    
    #[error("Session already connected: {0}")]
    SessionAlreadyConnected(String),
    
    #[error("Invalid request: {0}")]
    InvalidRequest(String),
    
    #[error("Internal error: {0}")]
    Internal(String),
}

impl CleanFlowError {
    pub fn error_code(&self) -> ErrorCode {
        match self {
            CleanFlowError::Unauthorized(_) => ErrorCode::Unauthorized,
            CleanFlowError::Forbidden(_) => ErrorCode::Forbidden,
            CleanFlowError::RateLimited(_) => ErrorCode::RateLimited,
            CleanFlowError::ValidationFailed(_) => ErrorCode::ValidationFailed,
            CleanFlowError::Conflict(_) => ErrorCode::Conflict,
            CleanFlowError::NotFound(_) => ErrorCode::NotFound,
            CleanFlowError::PlanNotFound(_) => ErrorCode::PlanNotFound,
            CleanFlowError::SessionNotFound(_) => ErrorCode::SessionNotFound,
            CleanFlowError::SessionAlreadyConnected(_) => ErrorCode::SessionAlreadyConnected,
            CleanFlowError::InvalidRequest(_) => ErrorCode::InvalidRequest,
            CleanFlowError::Internal(_) => ErrorCode::Internal,
        }
    }
    
    pub fn status_code(&self) -> StatusCode {
        match self {
            CleanFlowError::Unauthorized(_) => StatusCode::UNAUTHORIZED,
            CleanFlowError::Forbidden(_) => StatusCode::FORBIDDEN,
            CleanFlowError::RateLimited(_) => StatusCode::TOO_MANY_REQUESTS,
            CleanFlowError::ValidationFailed(_) => StatusCode::BAD_REQUEST,
            CleanFlowError::Conflict(_) => StatusCode::CONFLICT,
            CleanFlowError::NotFound(_) => StatusCode::NOT_FOUND,
            CleanFlowError::PlanNotFound(_) => StatusCode::NOT_FOUND,
            CleanFlowError::SessionNotFound(_) => StatusCode::NOT_FOUND,
            CleanFlowError::SessionAlreadyConnected(_) => StatusCode::CONFLICT,
            CleanFlowError::InvalidRequest(_) => StatusCode::BAD_REQUEST,
            CleanFlowError::Internal(_) => StatusCode::INTERNAL_SERVER_ERROR,
        }
    }
    
    pub fn details(&self) -> Option<serde_json::Value> {
        match self {
            CleanFlowError::PlanNotFound(plan_id) => Some(json!({ "planId": plan_id })),
            CleanFlowError::SessionNotFound(session_id) => Some(json!({ "sessionId": session_id })),
            CleanFlowError::SessionAlreadyConnected(session_id) => Some(json!({ "sessionId": session_id })),
            _ => None,
        }
    }
}

impl IntoResponse for CleanFlowError {
    fn into_response(self) -> Response {
        let status = self.status_code();
        let code = self.error_code();
        let message = self.to_string();
        let details = self.details();
        let request_id = Uuid::new_v4().to_string();
        
        // Log error for debugging
        error!(
            error = %self,
            request_id = %request_id,
            "API error occurred"
        );
        
        let error_response = ApiError {
            error: ErrorDetails {
                code: code.as_str().to_string(),
                message,
                details,
                request_id,
            },
        };
        
        (status, Json(error_response)).into_response()
    }
}

/// Convert anyhow errors to CleanFlow errors
impl From<anyhow::Error> for CleanFlowError {
    fn from(err: anyhow::Error) -> Self {
        CleanFlowError::Internal(err.to_string())
    }
}

/// Convert database errors to CleanFlow errors
impl From<sqlx::Error> for CleanFlowError {
    fn from(err: sqlx::Error) -> Self {
        match err {
            sqlx::Error::RowNotFound => CleanFlowError::NotFound("Resource not found".to_string()),
            _ => CleanFlowError::Internal(format!("Database error: {}", err)),
        }
    }
}
