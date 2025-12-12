//! Capability gating tests for tools
//!
//! Validates that tool execution respects capability tokens
//! and guardrail action outcomes.

use clara_oss_integrations::capabilities_adapter::{
    Capability, CapabilitiesProvider, CapabilityToken,
};
use clara_guardrails::Action;
use std::collections::HashSet;
use chrono::Utc;

fn create_test_token(caps: Vec<Capability>) -> CapabilityToken {
    CapabilityToken {
        turn_id: "test_turn".to_string(),
        capabilities: caps.into_iter().collect(),
        expires_at: Utc::now().timestamp() + 300, // 5 minutes
    }
}

/// Test: ALLOW_CHAT → plan read allowed, write denied
#[test]
fn allow_chat_permits_read_denies_write() {
    let provider = clara_oss_integrations::capabilities_adapter::create_provider().unwrap();
    
    let token = create_test_token(vec![
        Capability::AllowChat,
        Capability::AllowPlanRead,
        // Explicitly NO AllowPlanWrite
    ]);
    
    // Read should be allowed
    assert!(
        provider.has_capability(&token, Capability::AllowPlanRead),
        "ALLOW_CHAT should permit plan read"
    );
    
    assert!(
        provider.has_capability(&token, Capability::AllowChat),
        "ALLOW_CHAT capability should be present"
    );
    
    // Write should be denied
    assert!(
        !provider.has_capability(&token, Capability::AllowPlanWrite),
        "ALLOW_CHAT should DENY plan write"
    );
    
    assert!(
        !provider.has_capability(&token, Capability::AllowToolExecution),
        "ALLOW_CHAT should DENY tool execution"
    );
}

/// Test: ALLOW_PLAN_WRITE → revise OK, home/member claims validated
#[test]
fn allow_plan_write_permits_revise() {
    let provider = clara_oss_integrations::capabilities_adapter::create_provider().unwrap();
    
    let token = create_test_token(vec![
        Capability::AllowChat,
        Capability::AllowPlanRead,
        Capability::AllowPlanWrite,
        Capability::AllowToolExecution,
    ]);
    
    // Write operations should be allowed
    assert!(
        provider.has_capability(&token, Capability::AllowPlanWrite),
        "ALLOW_PLAN_WRITE should permit plan write"
    );
    
    assert!(
        provider.has_capability(&token, Capability::AllowToolExecution),
        "ALLOW_PLAN_WRITE should permit tool execution"
    );
    
    // Note: Home/member claim validation would happen in the tool execution layer
    // This test validates capability checking only
}

/// Test: DENY_TOOLS → all tool calls rejected with POLICY_BLOCK code
#[test]
fn deny_tools_rejects_all_tools() {
    let provider = clara_oss_integrations::capabilities_adapter::create_provider().unwrap();
    
    let token = create_test_token(vec![
        Capability::DenyTools,
        Capability::AllowChat, // Chat still allowed
    ]);
    
    // All tool-related capabilities should be denied
    assert!(
        !provider.has_capability(&token, Capability::AllowPlanWrite),
        "DENY_TOOLS should block plan write"
    );
    
    assert!(
        !provider.has_capability(&token, Capability::AllowToolExecution),
        "DENY_TOOLS should block tool execution"
    );
    
    assert!(
        !provider.has_capability(&token, Capability::AllowPrintable),
        "DENY_TOOLS should block printable generation"
    );
    
    assert!(
        !provider.has_capability(&token, Capability::AllowFamilyAssign),
        "DENY_TOOLS should block family assignment"
    );
    
    // Chat should still work
    assert!(
        provider.has_capability(&token, Capability::AllowChat),
        "DENY_TOOLS should still allow chat"
    );
}

/// Test: Guardrail Action → Capability mapping
#[test]
fn guardrail_action_maps_to_capabilities() {
    let provider = clara_oss_integrations::capabilities_adapter::create_provider().unwrap();
    
    // Test ALLOW action
    let token_allow = provider.create_token("turn_1", &Action::Allow).unwrap();
    assert!(
        provider.has_capability(&token_allow, Capability::AllowPlanWrite),
        "ALLOW action should grant write capabilities"
    );
    
    // Test DOWNGRADE action
    let token_downgrade = provider.create_token("turn_2", &Action::Downgrade).unwrap();
    assert!(
        !provider.has_capability(&token_downgrade, Capability::AllowPlanWrite),
        "DOWNGRADE action should deny write capabilities"
    );
    assert!(
        provider.has_capability(&token_downgrade, Capability::AllowPlanRead),
        "DOWNGRADE action should still allow read"
    );
    
    // Test BLOCK action
    let token_block = provider.create_token("turn_3", &Action::Block).unwrap();
    assert!(
        !provider.has_capability(&token_block, Capability::AllowToolExecution),
        "BLOCK action should deny all tool execution"
    );
    assert!(
        provider.has_capability(&token_block, Capability::DenyTools),
        "BLOCK action should set DENY_TOOLS"
    );
}

/// Test: Expired tokens are rejected
#[test]
fn expired_tokens_are_rejected() {
    let provider = clara_oss_integrations::capabilities_adapter::create_provider().unwrap();
    
    let mut token = create_test_token(vec![Capability::AllowPlanWrite]);
    token.expires_at = Utc::now().timestamp() - 1; // Expired
    
    assert!(
        !provider.has_capability(&token, Capability::AllowPlanWrite),
        "Expired tokens should be rejected"
    );
}

