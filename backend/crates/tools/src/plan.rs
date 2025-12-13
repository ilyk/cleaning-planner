//! Plan generation and revision tools

use crate::{ToolError, ToolResult};
use cleanflow_store::Store;
use serde::{Deserialize, Serialize};
use serde_json::json;

#[derive(Debug, Deserialize)]
struct GenerateArgs {
    title: String,
    description: Option<String>,
}

#[derive(Debug, Deserialize)]
struct ReviseArgs {
    plan_id: String,
    changes: serde_json::Value,
}

/// Generate a new plan
pub async fn generate(
    store: &Store,
    home_id: &str,
    args: serde_json::Value,
) -> Result<ToolResult, ToolError> {
    let args: GenerateArgs = serde_json::from_value(args)
        .map_err(|e| ToolError::InvalidArgs(format!("Invalid args: {}", e)))?;

    tracing::info!(
        home_id = home_id,
        title = args.title,
        "Generating plan"
    );

    // Create plan with enhanced structure
    let content = json!({
        "title": args.title,
        "description": args.description.unwrap_or_default(),
        "tasks": [],
        "created_by": "cleanflow",
        "status": "active"
    });

    let plan = store
        .plan
        .create_plan(home_id, &args.title, content)
        .await
        .map_err(|e| ToolError::Internal(e))?;

    tracing::info!(
        plan_id = %plan.plan_id,
        "Plan created successfully"
    );

    Ok(ToolResult::success(json!({
        "plan_id": plan.plan_id.to_string(),
        "title": plan.title,
        "home_id": plan.home_id,
    })))
}

/// Revise an existing plan
pub async fn revise(
    store: &Store,
    home_id: &str,
    args: serde_json::Value,
) -> Result<ToolResult, ToolError> {
    let args: ReviseArgs = serde_json::from_value(args)
        .map_err(|e| ToolError::InvalidArgs(format!("Invalid args: {}", e)))?;

    let plan_id = uuid::Uuid::parse_str(&args.plan_id)
        .map_err(|e| ToolError::InvalidArgs(format!("Invalid plan_id: {}", e)))?;

    tracing::info!(plan_id = %plan_id, home_id = home_id, "Revising plan");

    // Get existing plan
    let plan = store
        .plan
        .get_plan(plan_id)
        .await
        .map_err(|e| ToolError::Internal(e))?
        .ok_or_else(|| ToolError::InvalidArgs("Plan not found".to_string()))?;

    // Verify home_id
    if plan.home_id != home_id {
        tracing::warn!(
            plan_id = %plan_id,
            plan_home_id = plan.home_id,
            request_home_id = home_id,
            "Home ID mismatch"
        );
        return Err(ToolError::HomeIdMismatch);
    }

    // Merge changes with existing content
    let mut updated_content = plan.content.clone();
    if let serde_json::Value::Object(ref mut map) = updated_content {
        if let serde_json::Value::Object(changes_map) = args.changes {
            for (key, value) in changes_map {
                map.insert(key, value);
            }
        }
    }

    // Update plan
    store.plan.update_plan(plan_id, updated_content).await
        .map_err(|e| ToolError::Internal(e))?;

    tracing::info!(plan_id = %plan_id, "Plan updated successfully");

    Ok(ToolResult::success(json!({
        "plan_id": plan_id.to_string(),
        "updated": true,
        "home_id": home_id,
    })))
}

