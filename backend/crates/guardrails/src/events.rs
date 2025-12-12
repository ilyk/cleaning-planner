//! Guardrail events sent from server to client

use serde::{Deserialize, Serialize};

/// Guardrail notice event
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub struct GuardrailNotice {
    #[serde(rename = "type")]
    pub event_type: String,
    pub class: String,
    pub reason: String,
    pub message: String,
}

impl GuardrailNotice {
    pub fn new(class: impl Into<String>, reason: impl Into<String>, message: impl Into<String>) -> Self {
        Self {
            event_type: "guardrail.notice".to_string(),
            class: class.into(),
            reason: reason.into(),
            message: message.into(),
        }
    }
}

/// Mask range event
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub struct GuardrailMask {
    #[serde(rename = "type")]
    pub event_type: String,
    pub range: TimeRange,
}

impl GuardrailMask {
    pub fn new(start_ms: u64, end_ms: u64) -> Self {
        Self {
            event_type: "guardrail.mask".to_string(),
            range: TimeRange { start_ms, end_ms },
        }
    }
}

/// Time range for masking
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TimeRange {
    pub start_ms: u64,
    pub end_ms: u64,
}

/// Capability update event
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub struct CapabilityUpdate {
    #[serde(rename = "type")]
    pub event_type: String,
    pub allow: Vec<String>,
    pub deny: Vec<String>,
}

impl CapabilityUpdate {
    pub fn new(allow: Vec<String>, deny: Vec<String>) -> Self {
        Self {
            event_type: "capability.update".to_string(),
            allow,
            deny,
        }
    }
}

/// Interrupt event
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub struct GuardrailInterrupt {
    #[serde(rename = "type")]
    pub event_type: String,
    pub reason: String,
}

impl GuardrailInterrupt {
    pub fn new(reason: impl Into<String>) -> Self {
        Self {
            event_type: "interrupt".to_string(),
            reason: reason.into(),
        }
    }
}

/// All guardrail events
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(untagged)]
pub enum GuardrailEvent {
    Notice(GuardrailNotice),
    Mask(GuardrailMask),
    CapabilityUpdate(CapabilityUpdate),
    Interrupt(GuardrailInterrupt),
}

impl GuardrailEvent {
    pub fn to_json(&self) -> serde_json::Result<String> {
        serde_json::to_string(self)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_guardrail_notice_serialization() {
        let notice = GuardrailNotice::new(
            "R2",
            "pii_digits",
            "I can't process personal numbers. Let's continue without them.",
        );
        let json = serde_json::to_string(&notice).unwrap();
        assert!(json.contains("guardrail.notice"));
        assert!(json.contains("R2"));
        assert!(json.contains("pii_digits"));
    }

    #[test]
    fn test_guardrail_mask_serialization() {
        let mask = GuardrailMask::new(1200, 1800);
        let json = serde_json::to_string(&mask).unwrap();
        assert!(json.contains("guardrail.mask"));
        assert!(json.contains("1200"));
        assert!(json.contains("1800"));
    }

    #[test]
    fn test_capability_update_serialization() {
        let update = CapabilityUpdate::new(
            vec!["ALLOW_CHAT".to_string(), "ALLOW_PLAN_READ".to_string()],
            vec!["DENY_TOOLS".to_string()],
        );
        let json = serde_json::to_string(&update).unwrap();
        assert!(json.contains("capability.update"));
        assert!(json.contains("ALLOW_CHAT"));
        assert!(json.contains("DENY_TOOLS"));
    }

    #[test]
    fn test_interrupt_serialization() {
        let interrupt = GuardrailInterrupt::new("policy_block");
        let json = serde_json::to_string(&interrupt).unwrap();
        assert!(json.contains("interrupt"));
        assert!(json.contains("policy_block"));
    }
}


