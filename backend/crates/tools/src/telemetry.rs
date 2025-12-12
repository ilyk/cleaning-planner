//! Telemetry recording tool

use crate::{ToolError, ToolResult};
use clara_store::Store;
use serde::Deserialize;
use serde_json::json;

#[derive(Debug, Deserialize)]
struct CompleteArgs {
    session_id: String,
    feedback: Option<String>,
}

/// Record session completion
pub async fn complete(
    _store: &Store,
    home_id: &str,
    args: serde_json::Value,
) -> Result<ToolResult, ToolError> {
    let args: CompleteArgs = serde_json::from_value(args)
        .map_err(|e| ToolError::InvalidArgs(format!("Invalid args: {}", e)))?;

    tracing::info!(
        home_id = home_id,
        session_id = args.session_id,
        "Recording session completion"
    );

    // TODO: Record completion metrics

    Ok(ToolResult::success(json!({
        "recorded": true,
        "session_id": args.session_id,
    })))
}

