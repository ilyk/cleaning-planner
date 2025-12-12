//! Configuration management for Clara backend
//!
//! Loads configuration from environment variables and provides
//! typed access to all settings, feature flags, and policy versions.

use anyhow::{Context, Result};
use serde::Deserialize;
use std::time::Duration;

/// Application configuration
#[derive(Debug, Clone, Deserialize)]
pub struct AppConfig {
    /// Server configuration
    pub server: ServerConfig,

    /// Database configuration
    pub database: DatabaseConfig,

    /// Redis configuration
    pub redis: RedisConfig,

    /// Authentication configuration
    pub auth: AuthConfig,

    /// LLM configuration
    pub llm: LlmConfig,

    /// Rate limiting configuration
    pub rate_limit: RateLimitConfig,

    /// Timeout configuration
    pub timeouts: TimeoutConfig,

    /// Policy and prompt versions
    pub versions: VersionConfig,

    /// Feature flags
    pub features: FeatureFlags,

    /// Guardrails configuration
    #[serde(default)]
    pub guardrails: GuardrailsConfig,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ServerConfig {
    /// Server host
    #[serde(default = "default_host")]
    pub host: String,

    /// Server port
    #[serde(default = "default_port")]
    pub port: u16,

    /// Maximum WebSocket frame size
    #[serde(default = "default_max_frame_size")]
    pub max_frame_size: usize,

    /// WebSocket heartbeat interval in seconds
    #[serde(default = "default_heartbeat_interval")]
    pub heartbeat_interval_secs: u64,

    /// Maximum missed heartbeats before disconnect
    #[serde(default = "default_max_missed_heartbeats")]
    pub max_missed_heartbeats: u32,
}

#[derive(Debug, Clone, Deserialize)]
pub struct DatabaseConfig {
    /// PostgreSQL connection URL
    pub url: String,

    /// Maximum number of connections in the pool
    #[serde(default = "default_db_max_connections")]
    pub max_connections: u32,
}

#[derive(Debug, Clone, Deserialize)]
pub struct RedisConfig {
    /// Redis connection URL
    pub url: String,

    /// Connection pool size
    #[serde(default = "default_redis_pool_size")]
    pub pool_size: usize,
}

#[derive(Debug, Clone, Deserialize)]
pub struct AuthConfig {
    /// JWT signing secret (HS256)
    pub jwt_secret: String,

    /// JWT token expiration in seconds
    #[serde(default = "default_jwt_expiration")]
    pub jwt_expiration_secs: u64,

    /// Enable frame-level HMAC validation
    #[serde(default)]
    pub enable_frame_hmac: bool,
}

#[derive(Debug, Clone, Deserialize)]
pub struct LlmConfig {
    /// LLM provider: "openai" or "anthropic"
    #[serde(default = "default_llm_provider")]
    pub provider: String,

    /// OpenAI API key
    pub openai_api_key: Option<String>,

    /// OpenAI model name
    #[serde(default = "default_openai_model")]
    pub openai_model: String,

    /// Anthropic API key
    pub anthropic_api_key: Option<String>,

    /// Anthropic model name
    #[serde(default = "default_anthropic_model")]
    pub anthropic_model: String,

    /// LLM timeout in seconds
    #[serde(default = "default_llm_timeout")]
    pub llm_timeout_secs: u64,

    /// Earcon threshold (ms) - play earcon if LLM exceeds this
    #[serde(default = "default_earcon_threshold")]
    pub earcon_threshold_ms: u64,
}

#[derive(Debug, Clone, Deserialize)]
pub struct RateLimitConfig {
    /// Maximum turns per minute per session
    #[serde(default = "default_turns_per_minute")]
    pub turns_per_minute: u32,

    /// Burst capacity
    #[serde(default = "default_burst_capacity")]
    pub burst_capacity: u32,
}

#[derive(Debug, Clone, Deserialize)]
pub struct TimeoutConfig {
    /// Maximum input audio duration in seconds
    #[serde(default = "default_max_input_duration")]
    pub max_input_duration_secs: u64,

    /// Maximum output audio duration in seconds
    #[serde(default = "default_max_output_duration")]
    pub max_output_duration_secs: u64,

    /// Barge-in stop target in milliseconds
    #[serde(default = "default_barge_in_stop_target")]
    pub barge_in_stop_target_ms: u64,
}

#[derive(Debug, Clone, Deserialize)]
pub struct VersionConfig {
    /// Guardrail policy version
    #[serde(default = "default_guardrail_version")]
    pub guardrail_policy_version: String,

    /// Prompt version
    #[serde(default = "default_prompt_version")]
    pub prompt_version: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct FeatureFlags {
    /// Enable OpenAI Realtime API
    #[serde(default)]
    pub openai_realtime: bool,

    /// Enable OSS llm-security integration
    #[serde(default)]
    pub oss_llm_security: bool,

    /// Enable OSS path-security integration
    #[serde(default)]
    pub oss_path_security: bool,

    /// Enable OSS blockchain-runtime integration
    #[serde(default)]
    pub oss_blockchain_runtime: bool,

    /// Enable OSS worker-capabilities integration
    #[serde(default)]
    pub oss_worker_capabilities: bool,

    /// Enable OSS module-registry integration
    #[serde(default)]
    pub oss_module_registry: bool,

    /// Enable OSS threat-intel integration
    #[serde(default)]
    pub oss_threat_intel: bool,

    /// Enable OSS quantum-shield integration
    #[serde(default)]
    pub oss_quantum_shield: bool,

    /// Enable guardrails KWS
    #[serde(default = "default_guardrails_kws_enabled")]
    pub guardrails_kws_enabled: bool,

    /// Enable guardrails embeddings
    #[serde(default = "default_guardrails_embeddings_enabled")]
    pub guardrails_embeddings_enabled: bool,
}

#[derive(Debug, Clone, Deserialize)]
pub struct GuardrailsConfig {
    /// Quarantine span duration in milliseconds
    #[serde(default = "default_quarantine_span_ms")]
    pub quarantine_span_ms: u64,

    /// Quarantine slide window in milliseconds
    #[serde(default = "default_quarantine_slide_ms")]
    pub quarantine_slide_ms: u64,

    /// Sample rate in Hz
    #[serde(default = "default_sample_rate_hz")]
    pub sample_rate_hz: u32,

    /// VAD threshold
    #[serde(default = "default_vad_threshold")]
    pub vad_threshold: f32,

    /// Keyword threshold
    #[serde(default = "default_keyword_threshold")]
    pub keyword_threshold: f32,

    /// Embedding thresholds
    #[serde(default)]
    pub embed_thresholds: GuardrailEmbedThresholds,

    /// Max input duration in seconds
    #[serde(default = "default_max_input_duration_guardrails")]
    pub max_input_duration_secs: u64,

    /// Max output duration in seconds
    #[serde(default = "default_max_output_duration_guardrails")]
    pub max_output_duration_secs: u64,

    /// Cooldown between turns in seconds
    #[serde(default = "default_cooldown_secs")]
    pub cooldown_secs: u64,

    /// Policy version
    #[serde(default = "default_policy_version")]
    pub policy_version: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct GuardrailEmbedThresholds {
    #[serde(default = "default_embed_harassment")]
    pub harassment: f32,
    #[serde(default = "default_embed_sexual")]
    pub sexual: f32,
    #[serde(default = "default_embed_violence")]
    pub violence: f32,
    #[serde(default = "default_embed_self_harm")]
    pub self_harm: f32,
    #[serde(default = "default_embed_hate")]
    pub hate: f32,
    #[serde(default = "default_embed_pii")]
    pub pii: f32,
}

impl Default for GuardrailEmbedThresholds {
    fn default() -> Self {
        Self {
            harassment: 0.72,
            sexual: 0.68,
            violence: 0.70,
            self_harm: 0.60,
            hate: 0.65,
            pii: 0.62,
        }
    }
}

// Default value functions
fn default_host() -> String {
    "0.0.0.0".to_string()
}

fn default_port() -> u16 {
    8080
}

fn default_max_frame_size() -> usize {
    20 * 1024 // 20KB
}

fn default_heartbeat_interval() -> u64 {
    10
}

fn default_max_missed_heartbeats() -> u32 {
    3
}

fn default_db_max_connections() -> u32 {
    10
}

fn default_redis_pool_size() -> usize {
    10
}

fn default_jwt_expiration() -> u64 {
    3600 // 1 hour
}

fn default_llm_provider() -> String {
    "anthropic".to_string()  // Default to Claude for CleanFlow
}

fn default_openai_model() -> String {
    "gpt-4o-realtime-preview".to_string()
}

fn default_anthropic_model() -> String {
    "claude-sonnet-4-20250514".to_string()  // Claude Sonnet 4 for CleanFlow
}

fn default_llm_timeout() -> u64 {
    30
}

fn default_earcon_threshold() -> u64 {
    800
}

fn default_turns_per_minute() -> u32 {
    3
}

fn default_burst_capacity() -> u32 {
    6
}

fn default_max_input_duration() -> u64 {
    60
}

fn default_max_output_duration() -> u64 {
    90
}

fn default_barge_in_stop_target() -> u64 {
    50
}

fn default_guardrail_version() -> String {
    "guardrail-v1.0.0".to_string()
}

fn default_prompt_version() -> String {
    "prompt-v1.0.0".to_string()
}

fn default_guardrails_kws_enabled() -> bool {
    true
}

fn default_guardrails_embeddings_enabled() -> bool {
    true
}

fn default_quarantine_span_ms() -> u64 {
    1200
}

fn default_quarantine_slide_ms() -> u64 {
    200
}

fn default_sample_rate_hz() -> u32 {
    24000
}

fn default_vad_threshold() -> f32 {
    0.6
}

fn default_keyword_threshold() -> f32 {
    0.65
}

fn default_max_input_duration_guardrails() -> u64 {
    45
}

fn default_max_output_duration_guardrails() -> u64 {
    90
}

fn default_cooldown_secs() -> u64 {
    2
}

fn default_policy_version() -> String {
    "2025-10-28.3".to_string()
}

fn default_embed_harassment() -> f32 {
    0.72
}

fn default_embed_sexual() -> f32 {
    0.68
}

fn default_embed_violence() -> f32 {
    0.70
}

fn default_embed_self_harm() -> f32 {
    0.60
}

fn default_embed_hate() -> f32 {
    0.65
}

fn default_embed_pii() -> f32 {
    0.62
}

impl Default for GuardrailsConfig {
    fn default() -> Self {
        Self {
            quarantine_span_ms: default_quarantine_span_ms(),
            quarantine_slide_ms: default_quarantine_slide_ms(),
            sample_rate_hz: default_sample_rate_hz(),
            vad_threshold: default_vad_threshold(),
            keyword_threshold: default_keyword_threshold(),
            embed_thresholds: GuardrailEmbedThresholds::default(),
            max_input_duration_secs: default_max_input_duration_guardrails(),
            max_output_duration_secs: default_max_output_duration_guardrails(),
            cooldown_secs: default_cooldown_secs(),
            policy_version: default_policy_version(),
        }
    }
}

impl AppConfig {
    /// Load configuration from environment variables
    pub fn from_env() -> Result<Self> {
        // Load .env file if present (fail silently if not)
        let _ = dotenvy::dotenv();

        let config = config::Config::builder()
            // Server configuration
            .set_default("server.host", default_host())?
            .set_override_option("server.host", std::env::var("HOST").ok())?
            .set_default("server.port", i64::from(default_port()))?
            .set_override_option(
                "server.port",
                std::env::var("PORT").ok().and_then(|p| p.parse::<i64>().ok()),
            )?
            .set_default(
                "server.max_frame_size",
                i64::try_from(default_max_frame_size())?,
            )?
            .set_default(
                "server.heartbeat_interval_secs",
                i64::try_from(default_heartbeat_interval())?,
            )?
            .set_default(
                "server.max_missed_heartbeats",
                i64::from(default_max_missed_heartbeats()),
            )?
            // Database configuration
            .set_override_option(
                "database.url",
                std::env::var("DATABASE_URL").ok(),
            )?
            .set_default(
                "database.max_connections",
                i64::from(default_db_max_connections()),
            )?
            // Redis configuration
            .set_override_option("redis.url", std::env::var("REDIS_URL").ok())?
            .set_default(
                "redis.pool_size",
                i64::try_from(default_redis_pool_size())?,
            )?
            // Auth configuration
            .set_override_option(
                "auth.jwt_secret",
                std::env::var("JWT_SECRET").ok(),
            )?
            .set_default(
                "auth.jwt_expiration_secs",
                i64::try_from(default_jwt_expiration())?,
            )?
            .set_default("auth.enable_frame_hmac", false)?
            // LLM configuration
            .set_override_option(
                "llm.provider",
                std::env::var("LLM_PROVIDER").ok(),
            )?
            .set_default("llm.provider", default_llm_provider())?
            .set_override_option(
                "llm.openai_api_key",
                std::env::var("OPENAI_API_KEY").ok(),
            )?
            .set_default("llm.openai_model", default_openai_model())?
            .set_override_option(
                "llm.anthropic_api_key",
                std::env::var("ANTHROPIC_API_KEY").ok(),
            )?
            .set_default("llm.anthropic_model", default_anthropic_model())?
            .set_default(
                "llm.llm_timeout_secs",
                i64::try_from(default_llm_timeout())?,
            )?
            .set_default(
                "llm.earcon_threshold_ms",
                i64::try_from(default_earcon_threshold())?,
            )?
            // Rate limit configuration
            .set_default(
                "rate_limit.turns_per_minute",
                i64::from(default_turns_per_minute()),
            )?
            .set_default(
                "rate_limit.burst_capacity",
                i64::from(default_burst_capacity()),
            )?
            // Timeout configuration
            .set_default(
                "timeouts.max_input_duration_secs",
                i64::try_from(default_max_input_duration())?,
            )?
            .set_default(
                "timeouts.max_output_duration_secs",
                i64::try_from(default_max_output_duration())?,
            )?
            .set_default(
                "timeouts.barge_in_stop_target_ms",
                i64::try_from(default_barge_in_stop_target())?,
            )?
            // Version configuration
            .set_default("versions.guardrail_policy_version", default_guardrail_version())?
            .set_default("versions.prompt_version", default_prompt_version())?
            // Feature flags
            .set_default("features.openai_realtime", false)?
            .set_default("features.oss_llm_security", false)?
            .set_default("features.oss_path_security", false)?
            .set_default("features.oss_blockchain_runtime", false)?
            .set_default("features.oss_worker_capabilities", false)?
            .set_default("features.oss_module_registry", false)?
            .set_default("features.oss_threat_intel", false)?
            .set_default("features.oss_quantum_shield", false)?
            .build()
            .context("Failed to build configuration")?;

        let app_config: AppConfig = config
            .try_deserialize()
            .context("Failed to deserialize configuration")?;

        // Validate required fields
        app_config.validate()?;

        Ok(app_config)
    }

    /// Validate configuration
    fn validate(&self) -> Result<()> {
        if self.database.url.is_empty() {
            anyhow::bail!("DATABASE_URL must be set");
        }

        if self.redis.url.is_empty() {
            anyhow::bail!("REDIS_URL must be set");
        }

        if self.auth.jwt_secret.is_empty() {
            anyhow::bail!("JWT_SECRET must be set");
        }

        Ok(())
    }

    /// Get server bind address
    pub fn bind_address(&self) -> String {
        format!("{}:{}", self.server.host, self.server.port)
    }

    /// Get heartbeat interval as Duration
    pub fn heartbeat_interval(&self) -> Duration {
        Duration::from_secs(self.server.heartbeat_interval_secs)
    }

    /// Get LLM timeout as Duration
    pub fn llm_timeout(&self) -> Duration {
        Duration::from_secs(self.llm.llm_timeout_secs)
    }

    /// Get max input duration as Duration
    pub fn max_input_duration(&self) -> Duration {
        Duration::from_secs(self.timeouts.max_input_duration_secs)
    }

    /// Get max output duration as Duration
    pub fn max_output_duration(&self) -> Duration {
        Duration::from_secs(self.timeouts.max_output_duration_secs)
    }

    /// Get barge-in stop target as Duration
    pub fn barge_in_stop_target(&self) -> Duration {
        Duration::from_millis(self.timeouts.barge_in_stop_target_ms)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_default_values() {
        assert_eq!(default_host(), "0.0.0.0");
        assert_eq!(default_port(), 8080);
        assert_eq!(default_max_frame_size(), 20 * 1024);
        assert_eq!(default_turns_per_minute(), 3);
        assert_eq!(default_burst_capacity(), 6);
    }

    #[test]
    fn test_bind_address() {
        let config = AppConfig {
            server: ServerConfig {
                host: "127.0.0.1".to_string(),
                port: 3000,
                max_frame_size: 20480,
                heartbeat_interval_secs: 10,
                max_missed_heartbeats: 3,
            },
            database: DatabaseConfig {
                url: "postgres://localhost/test".to_string(),
                max_connections: 10,
            },
            redis: RedisConfig {
                url: "redis://localhost".to_string(),
                pool_size: 10,
            },
            auth: AuthConfig {
                jwt_secret: "secret".to_string(),
                jwt_expiration_secs: 3600,
                enable_frame_hmac: false,
            },
            llm: LlmConfig {
                provider: "anthropic".to_string(),
                openai_api_key: None,
                openai_model: "gpt-4o-realtime-preview".to_string(),
                anthropic_api_key: None,
                anthropic_model: "claude-sonnet-4-20250514".to_string(),
                llm_timeout_secs: 30,
                earcon_threshold_ms: 800,
            },
            rate_limit: RateLimitConfig {
                turns_per_minute: 3,
                burst_capacity: 6,
            },
            timeouts: TimeoutConfig {
                max_input_duration_secs: 60,
                max_output_duration_secs: 90,
                barge_in_stop_target_ms: 50,
            },
            versions: VersionConfig {
                guardrail_policy_version: "v1".to_string(),
                prompt_version: "v1".to_string(),
            },
            features: FeatureFlags {
                openai_realtime: false,
                oss_llm_security: false,
                oss_path_security: false,
                oss_blockchain_runtime: false,
                oss_worker_capabilities: false,
                oss_module_registry: false,
                oss_threat_intel: false,
                oss_quantum_shield: false,
                guardrails_kws_enabled: true,
                guardrails_embeddings_enabled: true,
            },
            guardrails: GuardrailsConfig::default(),
        };

        assert_eq!(config.bind_address(), "127.0.0.1:3000");
    }
}

