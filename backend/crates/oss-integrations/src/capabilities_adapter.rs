//! Worker Capabilities integration for turn-level capability tokens
//!
//! Provides capability-based access control for Clara's turn operations.

use anyhow::Result;
use serde::{Deserialize, Serialize};
use std::collections::HashSet;

/// Guardrail action outcome (local definition to avoid cyclic dependency)
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum GuardrailAction {
    /// Fully allow the operation
    Allow,
    /// Allow but mask sensitive content
    Mask,
    /// Downgrade capabilities
    Downgrade,
    /// Block the operation entirely
    Block,
}

/// Capability token for a turn
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CapabilityToken {
    pub turn_id: String,
    pub capabilities: HashSet<Capability>,
    pub expires_at: i64,
}

#[derive(Debug, Clone, Hash, Eq, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum Capability {
    AllowChat,
    AllowPlanRead,
    AllowPlanWrite,
    AllowToolExecution,
    DenyTools,
    AllowPrintable,
    AllowFamilyAssign,
}

/// Capabilities provider trait
pub trait CapabilitiesProvider: Send + Sync {
    /// Create capability token for a turn based on guardrail action
    fn create_token(&self, turn_id: &str, action: GuardrailAction) -> Result<CapabilityToken>;

    /// Check if capability is granted
    fn has_capability(&self, token: &CapabilityToken, cap: Capability) -> bool;

    /// Revoke capabilities
    fn revoke(&self, turn_id: &str) -> Result<()>;

    /// Get default capabilities for action
    fn capabilities_for_action(&self, action: GuardrailAction) -> HashSet<Capability>;
}

/// Mock implementation
#[derive(Clone)]
pub struct MockCapabilitiesProvider;

impl MockCapabilitiesProvider {
    pub fn new() -> Self {
        Self
    }
}

impl Default for MockCapabilitiesProvider {
    fn default() -> Self {
        Self::new()
    }
}

impl CapabilitiesProvider for MockCapabilitiesProvider {
    fn create_token(&self, turn_id: &str, action: GuardrailAction) -> Result<CapabilityToken> {
        let capabilities = self.capabilities_for_action(action);
        let expires_at = chrono::Utc::now().timestamp() + 300; // 5 minutes

        tracing::debug!(
            turn_id = turn_id,
            action = ?action,
            capabilities = ?capabilities,
            "Created capability token"
        );

        Ok(CapabilityToken {
            turn_id: turn_id.to_string(),
            capabilities,
            expires_at,
        })
    }

    fn has_capability(&self, token: &CapabilityToken, cap: Capability) -> bool {
        // Check expiration
        if chrono::Utc::now().timestamp() > token.expires_at {
            tracing::warn!(turn_id = token.turn_id, "Capability token expired");
            return false;
        }

        token.capabilities.contains(&cap)
    }

    fn revoke(&self, turn_id: &str) -> Result<()> {
        tracing::info!(turn_id = turn_id, "Mock: Revoking capabilities");
        Ok(())
    }

    fn capabilities_for_action(&self, action: GuardrailAction) -> HashSet<Capability> {
        match action {
            GuardrailAction::Allow => {
                // Full capabilities
                vec![
                    Capability::AllowChat,
                    Capability::AllowPlanRead,
                    Capability::AllowPlanWrite,
                    Capability::AllowToolExecution,
                    Capability::AllowPrintable,
                    Capability::AllowFamilyAssign,
                ]
                .into_iter()
                .collect()
            }
            GuardrailAction::Mask => {
                // Same as allow (mask affects audio, not capabilities)
                vec![
                    Capability::AllowChat,
                    Capability::AllowPlanRead,
                    Capability::AllowPlanWrite,
                    Capability::AllowToolExecution,
                    Capability::AllowPrintable,
                    Capability::AllowFamilyAssign,
                ]
                .into_iter()
                .collect()
            }
            GuardrailAction::Downgrade => {
                // Read-only
                vec![Capability::AllowChat, Capability::AllowPlanRead]
                    .into_iter()
                    .collect()
            }
            GuardrailAction::Block => {
                // No capabilities
                vec![Capability::DenyTools].into_iter().collect()
            }
        }
    }
}

/// Factory function
pub fn create_provider() -> Box<dyn CapabilitiesProvider> {
    tracing::debug!("Using mock capabilities provider");
    Box::new(MockCapabilitiesProvider::new())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_allow_action_grants_full_caps() {
        let provider = MockCapabilitiesProvider::new();
        let caps = provider.capabilities_for_action(GuardrailAction::Allow);
        assert!(caps.contains(&Capability::AllowChat));
        assert!(caps.contains(&Capability::AllowToolExecution));
    }

    #[test]
    fn test_block_action_denies_tools() {
        let provider = MockCapabilitiesProvider::new();
        let caps = provider.capabilities_for_action(GuardrailAction::Block);
        assert!(caps.contains(&Capability::DenyTools));
        assert!(!caps.contains(&Capability::AllowToolExecution));
    }
}
