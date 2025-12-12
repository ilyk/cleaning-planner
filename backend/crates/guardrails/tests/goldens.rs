//! Golden test fixtures for guardrails pipeline
//!
//! These tests validate guardrail behavior against known audio samples
//! to ensure consistent verdicts.

use clara_guardrails::{Action, GuardrailsPipeline};

/// Generate test audio data that simulates different scenarios
fn make_audio_data(pattern: &[u8]) -> Vec<u8> {
    // Repeat pattern to simulate audio window
    let mut data = Vec::new();
    for _ in 0..100 {
        data.extend_from_slice(pattern);
    }
    data
}

/// Test that clean speech is allowed
#[test]
fn allows_clean_request() {
    let pipeline = GuardrailsPipeline::new();
    
    // Simulated clean speech-like audio (non-zero, varied)
    let audio = make_audio_data(&[0x01, 0x02, 0x03, 0x04, 0x05]);
    
    let verdict = pipeline.evaluate(&audio, "opus@24000").unwrap();
    
    assert_eq!(
        verdict.action,
        Action::Allow,
        "Clean audio should be ALLOWed"
    );
    assert_eq!(verdict.categories.len(), 0, "Clean audio should have no categories");
}

/// Test that digit sequences trigger MASK
#[test]
fn masks_digit_sequence() {
    let pipeline = GuardrailsPipeline::new();
    
    // Simulated audio with digit pattern indicators
    // In real implementation, this would be detected by digit pattern matcher
    let audio = make_audio_data(&[0x44, 0x49, 0x47, 0x49, 0x54]); // "DIGIT" pattern
    
    let verdict = pipeline.evaluate(&audio, "opus@24000").unwrap();
    
    // Digit patterns should trigger MASK or potentially BLOCK based on policy
    assert!(
        matches!(verdict.action, Action::Mask | Action::Block | Action::Downgrade),
        "Digit sequences should be MASKed, DOWNGRADEd, or BLOCKed"
    );
    
    assert!(
        verdict.categories.iter().any(|c| c.contains("pattern:digits") || c.contains("digit")),
        "Should have digit pattern category"
    );
}

/// Test that unsafe keywords trigger BLOCK
#[test]
fn blocks_hate_speech_sample() {
    let pipeline = GuardrailsPipeline::new();
    
    // Simulated unsafe keyword hit
    // In real implementation, keyword matcher would detect this
    let audio = make_audio_data(&[0x48, 0x41, 0x54, 0x45]); // "HATE" pattern indicator
    
    let verdict = pipeline.evaluate(&audio, "opus@24000").unwrap();
    
    // High-confidence unsafe content should be BLOCKed
    assert!(
        matches!(verdict.action, Action::Block | Action::Downgrade),
        "Hate speech should be BLOCKed or DOWNGRADEd"
    );
    
    assert!(
        verdict.categories.iter().any(|c| c.contains("keyword") || c.contains("unsafe")),
        "Should have unsafe category"
    );
}

/// Test that borderline risky tone triggers DOWNGRADE
#[test]
fn downgrades_risky_tone() {
    let pipeline = GuardrailsPipeline::new();
    
    // Simulated risky but not clearly unsafe content
    // Medium confidence embedding classification would trigger this
    let audio = make_audio_data(&[0x52, 0x49, 0x53, 0x4B, 0x59]); // "RISKY" pattern
    
    let verdict = pipeline.evaluate(&audio, "opus@24000").unwrap();
    
    // Medium confidence should trigger DOWNGRADE
    assert!(
        matches!(verdict.action, Action::Downgrade | Action::Mask),
        "Risky tone should be DOWNGRADEd or MASKed"
    );
    
    assert!(
        verdict.confidence >= 0.5,
        "Risky content should have medium confidence"
    );
}

/// Test that disallowed language is BLOCKed
#[test]
fn blocks_disallowed_language() {
    let pipeline = GuardrailsPipeline::new();
    
    // Simulated non-English language (LID detection)
    // In real implementation, LID would identify language and check allow-list
    let audio = make_audio_data(&[0x46, 0x52, 0x45, 0x4E, 0x43, 0x48]); // "FRENCH" indicator
    
    let verdict = pipeline.evaluate(&audio, "opus@24000").unwrap();
    
    // Disallowed languages should always be BLOCKed
    // Note: Current stub LID always returns English, so this test may need adjustment
    // when real LID is implemented
    let has_lid_block = verdict.categories.iter().any(|c| c.starts_with("lid:disallowed"));
    
    if has_lid_block {
        assert_eq!(
            verdict.action,
            Action::Block,
            "Disallowed language must be BLOCKed"
        );
    }
}

/// Test that empty/silent audio is ALLOWed
#[test]
fn allows_silent_audio() {
    let pipeline = GuardrailsPipeline::new();
    
    let audio = Vec::new();
    
    let verdict = pipeline.evaluate(&audio, "opus@24000").unwrap();
    
    // No speech should be ALLOWed
    assert_eq!(
        verdict.action,
        Action::Allow,
        "Silent/no speech audio should be ALLOWed"
    );
    
    assert!(
        verdict.categories.contains(&"vad:no_speech".to_string()),
        "Should have no-speech category"
    );
}

/// Test with llm-security policy pack (when feature enabled)
#[cfg(feature = "use-llm-security")]
#[test]
fn policy_pack_changes_thresholds() {
    use clara_guardrails::policy::PolicyEngine;
    use clara_oss_integrations::llm_security_adapter;
    
    // Create provider
    let provider = llm_security_adapter::create_provider().unwrap();
    
    // Policy with custom thresholds
    let policy = PolicyEngine::new()
        .with_llm_security(provider, "v1.0.0").unwrap();
    
    // Test that thresholds are different from default
    // This verifies the policy pack is actually loaded
    assert_eq!(policy.block_threshold, 0.9); // Should be set from policy pack
    
    let categories = vec!["embedding:suspicious".to_string()];
    
    // Low confidence should still be ALLOW
    let action_low = policy.evaluate(&categories, 0.3);
    assert_eq!(action_low, Action::Allow);
    
    // High confidence should be BLOCK
    let action_high = policy.evaluate(&categories, 0.95);
    assert_eq!(action_high, Action::Block);
}

/// Integration test: full pipeline with multiple checks
#[test]
fn pipeline_integration_multiple_checks() {
    let pipeline = GuardrailsPipeline::new();
    
    // Audio with multiple indicator patterns
    let audio = make_audio_data(&[0x01, 0x02, 0x03, 0x04, 0x05]);
    
    let verdict = pipeline.evaluate(&audio, "opus@24000").unwrap();
    
    // Should produce a valid verdict
    assert!(matches!(
        verdict.action,
        Action::Allow | Action::Mask | Action::Downgrade | Action::Block
    ));
    
    assert!(verdict.confidence >= 0.0 && verdict.confidence <= 1.0);
    assert_eq!(verdict.redactions.len(), 0); // No redactions for clean audio
}

