//! Capability masks for tool access control

use cleanflow_guardrails::Action;
use serde::{Deserialize, Serialize};

/// Capability mask determining which tools are available
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CapabilityMask {
    pub allow_read: bool,
    pub allow_write: bool,
    pub allow_generate: bool,
    pub allow_external_calls: bool,
}

impl CapabilityMask {
    /// Full capabilities (for ALLOW action)
    pub fn full() -> Self {
        Self {
            allow_read: true,
            allow_write: true,
            allow_generate: true,
            allow_external_calls: true,
        }
    }

    /// Read-only capabilities (for DOWNGRADE action)
    pub fn read_only() -> Self {
        Self {
            allow_read: true,
            allow_write: false,
            allow_generate: false,
            allow_external_calls: false,
        }
    }

    /// No capabilities (for BLOCK action)
    pub fn none() -> Self {
        Self {
            allow_read: false,
            allow_write: false,
            allow_generate: false,
            allow_external_calls: false,
        }
    }

    /// Create capability mask from guardrail action
    pub fn from_action(action: &Action) -> Self {
        match action {
            Action::Allow => Self::full(),
            Action::Mask => Self::full(), // Mask doesn't affect tools, only audio
            Action::Downgrade => Self::read_only(),
            Action::Block => Self::none(),
        }
    }

    /// Check if tool is allowed
    pub fn allows_tool(&self, tool_name: &str) -> bool {
        match tool_name {
            "plan_generate" | "plan_revise" => self.allow_generate && self.allow_write,
            "telemetry_complete" => self.allow_write,
            "plan_printable" => self.allow_read && self.allow_external_calls,
            "family_assign" => self.allow_write,
            _ => false,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_capability_mask_full() {
        let mask = CapabilityMask::full();
        assert!(mask.allows_tool("plan_generate"));
        assert!(mask.allows_tool("telemetry_complete"));
        assert!(mask.allows_tool("plan_printable"));
    }

    #[test]
    fn test_capability_mask_read_only() {
        let mask = CapabilityMask::read_only();
        assert!(!mask.allows_tool("plan_generate"));
        assert!(!mask.allows_tool("telemetry_complete"));
        assert!(!mask.allows_tool("plan_printable")); // Needs external_calls
    }

    #[test]
    fn test_capability_mask_from_action() {
        let allow_mask = CapabilityMask::from_action(&Action::Allow);
        assert!(allow_mask.allows_tool("plan_generate"));

        let downgrade_mask = CapabilityMask::from_action(&Action::Downgrade);
        assert!(!downgrade_mask.allows_tool("plan_generate"));

        let block_mask = CapabilityMask::from_action(&Action::Block);
        assert!(!block_mask.allows_tool("plan_generate"));
    }
}

