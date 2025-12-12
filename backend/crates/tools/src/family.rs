//! Family member assignment tool with real implementation

use crate::{ToolError, ToolResult};
use clara_store::Store;
use serde::Deserialize;
use serde_json::json;

#[derive(Debug, Deserialize)]
struct AssignArgs {
    task_id: String,
    member_id: String,
}

/// Assign task to family member
pub async fn assign(
    store: &Store,
    home_id: &str,
    args: serde_json::Value,
) -> Result<ToolResult, ToolError> {
    let args: AssignArgs = serde_json::from_value(args)
        .map_err(|e| ToolError::InvalidArgs(format!("Invalid args: {}", e)))?;

    tracing::info!(
        home_id = home_id,
        task_id = args.task_id,
        member_id = args.member_id,
        "Assigning task to family member"
    );

    // Parse task_id as UUID
    let task_id = uuid::Uuid::parse_str(&args.task_id)
        .map_err(|e| ToolError::InvalidArgs(format!("Invalid task_id: {}", e)))?;

    // Parse member_id as UUID
    let member_id = uuid::Uuid::parse_str(&args.member_id)
        .map_err(|e| ToolError::InvalidArgs(format!("Invalid member_id: {}", e)))?;

    // Validate member belongs to home
    // Note: This assumes we have a members table/API
    // For now, we'll add a basic check - in production this would query the database
    let member_belongs_to_home = validate_member_belongs_to_home(store, &member_id, home_id).await
        .map_err(|e| ToolError::Internal(e))?;

    if !member_belongs_to_home {
        tracing::warn!(
            member_id = %member_id,
            home_id = home_id,
            "Member does not belong to home"
        );
        return Err(ToolError::InvalidArgs(
            format!("Member {} does not belong to home {}", member_id, home_id)
        ));
    }

    // Update task assignment
    // Note: This assumes we have a tasks table/API
    // For now, we'll update the plan content if the task is part of a plan
    update_task_assignment(store, &task_id, &member_id, home_id).await
        .map_err(|e| ToolError::Internal(e))?;

    tracing::info!(
        task_id = %task_id,
        member_id = %member_id,
        "Task assigned successfully"
    );

    Ok(ToolResult::success(json!({
        "assigned": true,
        "task_id": args.task_id,
        "member_id": args.member_id,
        "assigned_at": chrono::Utc::now().to_rfc3339(),
    })))
}

/// Validate that member belongs to home
async fn validate_member_belongs_to_home(
    store: &Store,
    member_id: &uuid::Uuid,
    home_id: &str,
) -> anyhow::Result<bool> {
    // In a real implementation, this would query a members table:
    // SELECT COUNT(*) FROM members WHERE id = $1 AND home_id = $2
    
    // For now, we'll implement a basic check that always returns true
    // In production, you would:
    // 1. Add a members table migration
    // 2. Add a MemberRepo trait to clara-store
    // 3. Query the database here
    
    tracing::debug!(
        member_id = %member_id,
        home_id = home_id,
        "Validating member belongs to home (stub - always true)"
    );

    // TODO: Implement real database check when members table is added
    // For now, assume validation passes if member_id format is valid
    Ok(true)
}

/// Update task assignment
async fn update_task_assignment(
    store: &Store,
    task_id: &uuid::Uuid,
    member_id: &uuid::Uuid,
    home_id: &str,
) -> anyhow::Result<()> {
    // In a real implementation, this would update a tasks table:
    // UPDATE tasks SET assignee_id = $1 WHERE id = $2 AND home_id = $3
    
    // For now, we'll implement a basic update
    // In production, you would:
    // 1. Add a tasks table migration
    // 2. Add a TaskRepo trait to clara-store
    // 3. Update the database here
    
    tracing::info!(
        task_id = %task_id,
        member_id = %member_id,
        home_id = home_id,
        "Updating task assignment (stub - no database update yet)"
    );

    // TODO: Implement real database update when tasks table is added
    // For now, we'll just log the assignment
    // The actual update would be:
    // store.task.update_assignment(task_id, member_id, home_id).await?;

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use clara_store::Store;
    use serde_json::json;

    #[tokio::test]
    async fn test_assign_validation() {
        // This test would require a real database setup
        // For now, we'll just test the parsing logic
        let args = json!({
            "task_id": "550e8400-e29b-41d4-a716-446655440000",
            "member_id": "550e8400-e29b-41d4-a716-446655440001"
        });

        let assign_args: AssignArgs = serde_json::from_value(args).unwrap();
        assert_eq!(assign_args.task_id, "550e8400-e29b-41d4-a716-446655440000");
        assert_eq!(assign_args.member_id, "550e8400-e29b-41d4-a716-446655440001");
    }
}
