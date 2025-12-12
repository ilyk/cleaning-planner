//! Tool executor

use crate::{capabilities::CapabilityMask, family, plan, printable, telemetry, ToolCall, ToolError, ToolResult};
use clara_store::Store;
use std::sync::Arc;

/// Tool executor
pub struct ToolExecutor {
    store: Store,
    capabilities: CapabilityMask,
    home_id: String,
}

impl ToolExecutor {
    pub fn new(store: Store, capabilities: CapabilityMask, home_id: String) -> Self {
        Self {
            store,
            capabilities,
            home_id,
        }
    }

    /// Execute a tool call
    pub async fn execute(&self, call: ToolCall) -> Result<ToolResult, ToolError> {
        // Check capability
        if !self.capabilities.allows_tool(&call.name) {
            return Err(ToolError::CapabilityDenied(format!(
                "Tool '{}' not allowed with current capabilities",
                call.name
            )));
        }

        // Route to appropriate handler
        match call.name.as_str() {
            "plan_generate" => plan::generate(&self.store, &self.home_id, call.args).await,
            "plan_revise" => plan::revise(&self.store, &self.home_id, call.args).await,
            "telemetry_complete" => {
                telemetry::complete(&self.store, &self.home_id, call.args).await
            }
            "plan_printable" => printable::generate(&self.store, &self.home_id, call.args).await,
            "family_assign" => family::assign(&self.store, &self.home_id, call.args).await,
            _ => Err(ToolError::NotFound(call.name)),
        }
    }
}

