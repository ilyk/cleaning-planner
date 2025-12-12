//! Error codes and types for Clara protocol

use serde::{Deserialize, Serialize};
use std::fmt;
use thiserror::Error;

/// Protocol error codes
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum ErrorCode {
    /// Authentication failed
    AuthFailed,
    /// JWT token expired or invalid
    InvalidToken,
    /// Sequence number out of order
    SeqOutOfOrder,
    /// Policy blocked this request
    PolicyBlock,
    /// Request timed out
    Timeout,
    /// Rate limit exceeded
    RateLimited,
    /// Payload too large (>20KB)
    PayloadTooLarge,
    /// Invalid audio format
    InvalidFormat,
    /// Protocol version mismatch
    VersionMismatch,
    /// Session not found
    SessionNotFound,
    /// Turn not found
    TurnNotFound,
    /// Session already has an active connection
    SessionAlreadyConnected,
    /// Invalid request
    InvalidRequest,
    /// Internal server error
    InternalError,
    /// Guardrail check failed
    GuardrailFailed,
    /// Maximum audio duration exceeded
    MaxDurationExceeded,
    /// Capability denied
    CapabilityDenied,
    /// Unknown error
    Unknown,
}

impl fmt::Display for ErrorCode {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{:?}", self)
    }
}

/// Protocol error
#[derive(Debug, Error)]
pub enum ProtocolError {
    #[error("Authentication failed: {0}")]
    AuthFailed(String),

    #[error("Sequence out of order: expected {expected}, got {actual}")]
    SeqOutOfOrder { expected: u64, actual: u64 },

    #[error("Policy block: {0}")]
    PolicyBlock(String),

    #[error("Timeout: {0}")]
    Timeout(String),

    #[error("Rate limited: {0}")]
    RateLimited(String),

    #[error("Payload too large: {size} bytes (max {max})")]
    PayloadTooLarge { size: usize, max: usize },

    #[error("Invalid format: {0}")]
    InvalidFormat(String),

    #[error("Version mismatch: expected {expected}, got {actual}")]
    VersionMismatch { expected: String, actual: String },

    #[error("Session not found: {0}")]
    SessionNotFound(String),

    #[error("Turn not found: {0}")]
    TurnNotFound(String),

    #[error("Session already connected: {0}")]
    SessionAlreadyConnected(String),

    #[error("Invalid request: {0}")]
    InvalidRequest(String),

    #[error("Internal error: {0}")]
    InternalError(String),

    #[error("Guardrail failed: {0}")]
    GuardrailFailed(String),

    #[error("Max duration exceeded: {duration}s (max {max}s)")]
    MaxDurationExceeded { duration: u64, max: u64 },

    #[error("Capability denied: {0}")]
    CapabilityDenied(String),
}

impl ProtocolError {
    /// Get the error code for this error
    pub fn code(&self) -> ErrorCode {
        match self {
            Self::AuthFailed(_) => ErrorCode::AuthFailed,
            Self::SeqOutOfOrder { .. } => ErrorCode::SeqOutOfOrder,
            Self::PolicyBlock(_) => ErrorCode::PolicyBlock,
            Self::Timeout(_) => ErrorCode::Timeout,
            Self::RateLimited(_) => ErrorCode::RateLimited,
            Self::PayloadTooLarge { .. } => ErrorCode::PayloadTooLarge,
            Self::InvalidFormat(_) => ErrorCode::InvalidFormat,
            Self::VersionMismatch { .. } => ErrorCode::VersionMismatch,
            Self::SessionNotFound(_) => ErrorCode::SessionNotFound,
            Self::TurnNotFound(_) => ErrorCode::TurnNotFound,
            Self::SessionAlreadyConnected(_) => ErrorCode::SessionAlreadyConnected,
            Self::InvalidRequest(_) => ErrorCode::InvalidRequest,
            Self::InternalError(_) => ErrorCode::InternalError,
            Self::GuardrailFailed(_) => ErrorCode::GuardrailFailed,
            Self::MaxDurationExceeded { .. } => ErrorCode::MaxDurationExceeded,
            Self::CapabilityDenied(_) => ErrorCode::CapabilityDenied,
        }
    }
}

