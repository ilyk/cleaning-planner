//! Server-side tool execution for Clara
//!
//! Implements plan generation, revision, telemetry, printables, and family assignment.
//! All tools validate home_id and respect capability masks from guardrails.

pub mod capabilities;
pub mod executor;
pub mod family;
pub mod plan;
pub mod printable;
pub mod telemetry;

pub use capabilities::CapabilityMask;
pub use executor::ToolExecutor;

use anyhow::Result;
use serde::{Deserialize, Serialize};
use thiserror::Error;

/// Tool call request
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ToolCall {
    pub name: String,
    pub args: serde_json::Value,
}

/// Tool call result
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ToolResult {
    pub success: bool,
    pub data: Option<serde_json::Value>,
    pub error: Option<String>,
}

/// Tool execution errors
#[derive(Debug, Error)]
pub enum ToolError {
    #[error("Tool not found: {0}")]
    NotFound(String),

    #[error("Invalid arguments: {0}")]
    InvalidArgs(String),

    #[error("Capability denied: {0}")]
    CapabilityDenied(String),

    #[error("Home ID mismatch")]
    HomeIdMismatch,

    #[error("Internal error: {0}")]
    Internal(#[from] anyhow::Error),
}

impl ToolResult {
    pub fn success(data: serde_json::Value) -> Self {
        Self {
            success: true,
            data: Some(data),
            error: None,
        }
    }

    pub fn error(error: String) -> Self {
        Self {
            success: false,
            data: None,
            error: Some(error),
        }
    }
}

