//! LLM adapter trait and implementations

pub mod claude;
pub mod mock;
pub mod openai;
pub mod traits;

pub use claude::ClaudeAdapter;
pub use traits::{LlmEvent, LlmRealtime};

use anyhow::Result;

/// LLM provider configuration
pub struct LlmConfig {
    pub provider: String,
    pub openai_api_key: Option<String>,
    pub openai_model: String,
    pub anthropic_api_key: Option<String>,
    pub anthropic_model: String,
}

/// Create LLM adapter based on configuration
pub fn create_adapter(config: &LlmConfig, use_mock: bool) -> Result<Box<dyn LlmRealtime>> {
    if use_mock {
        return Ok(Box::new(mock::MockLlmAdapter::new()));
    }

    match config.provider.as_str() {
        "anthropic" | "claude" => {
            if let Some(api_key) = &config.anthropic_api_key {
                Ok(Box::new(claude::ClaudeAdapter::new(
                    api_key.clone(),
                    config.anthropic_model.clone(),
                )))
            } else {
                tracing::warn!("Anthropic API key not set, falling back to mock adapter");
                Ok(Box::new(mock::MockLlmAdapter::new()))
            }
        }
        "openai" => {
            if let Some(api_key) = &config.openai_api_key {
                Ok(Box::new(openai::OpenAiRealtimeAdapter::new(
                    api_key.clone(),
                    config.openai_model.clone(),
                )))
            } else {
                tracing::warn!("OpenAI API key not set, falling back to mock adapter");
                Ok(Box::new(mock::MockLlmAdapter::new()))
            }
        }
        _ => {
            tracing::warn!(provider = %config.provider, "Unknown LLM provider, falling back to mock adapter");
            Ok(Box::new(mock::MockLlmAdapter::new()))
        }
    }
}

/// Legacy function for backward compatibility
#[deprecated(note = "Use create_adapter with LlmConfig instead")]
pub fn create_adapter_legacy(
    openai_api_key: Option<String>,
    model: String,
    use_mock: bool,
) -> Result<Box<dyn LlmRealtime>> {
    if use_mock || openai_api_key.is_none() {
        Ok(Box::new(mock::MockLlmAdapter::new()))
    } else {
        Ok(Box::new(openai::OpenAiRealtimeAdapter::new(
            openai_api_key.unwrap(),
            model,
        )))
    }
}
