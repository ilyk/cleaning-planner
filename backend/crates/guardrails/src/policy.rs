//! Policy engine with risk classes (R0-R3) and capability gating

use crate::pipeline::{Action, Span, Verdict};
use anyhow::{Context, Result};
use once_cell::sync::Lazy;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::path::Path;
use std::sync::{Arc, Mutex};
use std::time::{Duration, SystemTime};

/// Risk class (R0 = clean, R1 = mild, R2 = moderate, R3 = severe)
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "UPPERCASE")]
pub enum RiskClass {
    R0,
    R1,
    R2,
    R3,
}

/// Capability flags
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum Capability {
    AllowChat,
    AllowPlanRead,
    AllowPlanWrite,
    DenyTools,
    HardBlock,
}

/// Policy configuration loaded from YAML
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PolicyConfig {
    pub version: String,
    pub inputs: PolicyInputs,
    pub actions: PolicyActions,
    pub capabilities: PolicyCapabilities,
    pub safe_script: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PolicyInputs {
    pub vad: bool,
    pub lid: Vec<String>,
    #[serde(rename = "keyword_threshold")]
    pub keyword_threshold: f32,
    #[serde(rename = "embed_thresholds")]
    pub embed_thresholds: EmbedThresholds,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EmbedThresholds {
    pub harassment: f32,
    pub sexual: f32,
    pub violence: f32,
    #[serde(rename = "self_harm")]
    pub self_harm: f32,
    pub hate: f32,
    pub pii: f32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PolicyActions {
    #[serde(rename = "R3")]
    pub r3: Vec<String>,
    #[serde(rename = "R2")]
    pub r2: Vec<String>,
    #[serde(rename = "R1")]
    pub r1: Vec<String>,
    #[serde(rename = "R0")]
    pub r0: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PolicyCapabilities {
    pub allowed: Vec<String>,
    #[serde(rename = "denied_on_r2")]
    pub denied_on_r2: Vec<String>,
    #[serde(rename = "denied_on_r3")]
    pub denied_on_r3: Vec<String>,
}

/// Policy engine with hot-reload support
pub struct PolicyEngine {
    config: Arc<Mutex<PolicyConfig>>,
    config_path: String,
    last_reload: Arc<Mutex<SystemTime>>,
    block_threshold: f32,
    downgrade_threshold: f32,
    mask_threshold: f32,
    #[cfg(feature = "use-llm-security")]
    llm_security: Option<Box<dyn clara_oss_integrations::llm_security_adapter::LlmSecurityProvider>>,
}

impl PolicyEngine {
    /// Create policy engine from YAML file
    pub fn from_yaml(path: impl AsRef<Path>) -> Result<Self> {
        let path_str = path.as_ref().to_string_lossy().to_string();
        let config = Self::load_config(path.as_ref())?;
        
        Ok(Self {
            config: Arc::new(Mutex::new(config)),
            config_path: path_str,
            last_reload: Arc::new(Mutex::new(SystemTime::now())),
            block_threshold: 0.9,
            downgrade_threshold: 0.7,
            mask_threshold: 0.6,
            #[cfg(feature = "use-llm-security")]
            llm_security: None,
        })
    }

    /// Load config from YAML file
    fn load_config(path: &Path) -> Result<PolicyConfig> {
        let content = std::fs::read_to_string(path)
            .with_context(|| format!("Failed to read policy config from {}", path.display()))?;
        
        let config: PolicyConfig = serde_yaml::from_str(&content)
            .context("Failed to parse policy YAML")?;
        
        tracing::info!(
            version = %config.version,
            "Loaded guardrail policy config"
        );
        
        Ok(config)
    }

    /// Hot-reload policy on SIGHUP or manual trigger
    pub fn reload(&mut self) -> Result<()> {
        let path = Path::new(&self.config_path);
        let new_config = Self::load_config(path)?;
        
        *self.config.lock().unwrap() = new_config;
        *self.last_reload.lock().unwrap() = SystemTime::now();
        
        tracing::info!("Policy config reloaded");
        Ok(())
    }

    /// Get current policy version
    pub fn version(&self) -> String {
        self.config.lock().unwrap().version.clone()
    }

    /// Evaluate verdict and determine risk class + action
    pub fn evaluate(&self, verdict: &Verdict, categories: &[String], confidence: f32) -> (RiskClass, Action, Vec<Capability>) {
        let config = self.config.lock().unwrap();
        
        // Determine risk class from categories and confidence
        let risk_class = self.classify_risk(&config, categories, confidence);
        
        // Map risk class to action
        let action = self.risk_to_action(&config, risk_class);
        
        // Determine capabilities based on risk class
        let capabilities = self.risk_to_capabilities(&config, risk_class);
        
        (risk_class, action, capabilities)
    }

    /// Classify risk level (R0-R3)
    fn classify_risk(&self, config: &PolicyConfig, categories: &[String], confidence: f32) -> RiskClass {
        // R3: Hate, sexual minors, self-harm instructions, explicit violence
        if categories.iter().any(|c| {
            c.contains("hate") || 
            c.contains("sexual_minor") || 
            c.contains("self_harm") ||
            c.contains("violence_grave")
        }) && confidence >= config.inputs.embed_thresholds.hate.max(0.7) {
            return RiskClass::R3;
        }

        // R2: PII digits, adult sexual innuendo, moderate harassment
        if categories.iter().any(|c| {
            c.contains("pii") || 
            c.contains("digits") ||
            c.contains("sexual") ||
            (c.contains("harassment") && confidence >= config.inputs.embed_thresholds.harassment)
        }) && confidence >= config.inputs.embed_thresholds.pii.min(0.6) {
            return RiskClass::R2;
        }

        // R1: Mild harassment, heated tone
        if categories.iter().any(|c| c.contains("harassment")) && 
           confidence >= config.inputs.embed_thresholds.harassment * 0.8 {
            return RiskClass::R1;
        }

        // R0: Clean
        RiskClass::R0
    }

    /// Map risk class to action
    fn risk_to_action(&self, config: &PolicyConfig, risk: RiskClass) -> Action {
        match risk {
            RiskClass::R3 => {
                // Check if config specifies hard_block
                if config.actions.r3.contains(&"hard_block".to_string()) {
                    Action::Block
                } else {
                    Action::Block // Default for R3
                }
            }
            RiskClass::R2 => {
                if config.actions.r2.contains(&"mask_terms".to_string()) {
                    Action::Mask
                } else {
                    Action::Downgrade
                }
            }
            RiskClass::R1 => Action::Downgrade,
            RiskClass::R0 => Action::Allow,
        }
    }

    /// Map risk class to capabilities
    fn risk_to_capabilities(&self, config: &PolicyConfig, risk: RiskClass) -> Vec<Capability> {
        let mut caps = Vec::new();
        
        match risk {
            RiskClass::R0 => {
                // All capabilities allowed
                caps.push(Capability::AllowChat);
                caps.push(Capability::AllowPlanRead);
                caps.push(Capability::AllowPlanWrite);
            }
            RiskClass::R1 => {
                // Read allowed, write throttled
                caps.push(Capability::AllowChat);
                caps.push(Capability::AllowPlanRead);
            }
            RiskClass::R2 => {
                // Deny tools, allow read
                caps.push(Capability::AllowChat);
                caps.push(Capability::AllowPlanRead);
                caps.push(Capability::DenyTools);
            }
            RiskClass::R3 => {
                // Hard block
                caps.push(Capability::HardBlock);
                caps.push(Capability::DenyTools);
            }
        }
        
        caps
    }

    /// Get safe script message for R3 blocks
    pub fn safe_script(&self) -> String {
        self.config.lock().unwrap().safe_script.clone()
    }

    /// Legacy evaluate method (for backward compatibility)
    pub fn evaluate_legacy(&self, categories: &[String], confidence: f32) -> Action {
        let verdict = Verdict {
            action: Action::Allow,
            categories: categories.to_vec(),
            confidence,
            redactions: vec![],
            risk_class: None,
            capabilities: vec![],
            events: vec![],
        };

        let (_, action, _) = self.evaluate(&verdict, categories, confidence);
        action
    }

    #[cfg(feature = "use-llm-security")]
    pub fn with_llm_security(mut self, provider: Box<dyn clara_oss_integrations::llm_security_adapter::LlmSecurityProvider>) -> Self {
        self.llm_security = Some(provider);
        self
    }

    #[cfg(feature = "use-llm-security")]
    pub fn record_verdict(&self, verdict: &Verdict) -> anyhow::Result<()> {
        if let Some(ref provider) = self.llm_security {
            provider.record_verdict_telemetry(verdict)?;
        }
        Ok(())
    }
}

impl Default for PolicyEngine {
    fn default() -> Self {
        // Try to load from default path, fall back to hardcoded defaults
        let default_path = "config/policy.guardrails.yaml";
        
        if Path::new(default_path).exists() {
            Self::from_yaml(default_path).unwrap_or_else(|e| {
                tracing::warn!(error = %e, "Failed to load policy config, using defaults");
                Self::with_defaults()
            })
        } else {
            Self::with_defaults()
        }
    }
}

impl PolicyEngine {
    /// Create with hardcoded defaults (fallback)
    fn with_defaults() -> Self {
        let config = PolicyConfig {
            version: "2025-10-28.3".to_string(),
            inputs: PolicyInputs {
                vad: true,
                lid: vec!["en".to_string(), "uk".to_string(), "cs".to_string()],
                keyword_threshold: 0.65,
                embed_thresholds: EmbedThresholds {
                    harassment: 0.72,
                    sexual: 0.68,
                    violence: 0.70,
                    self_harm: 0.60,
                    hate: 0.65,
                    pii: 0.62,
                },
            },
            actions: PolicyActions {
                r3: vec!["hard_block".to_string(), "safe_script".to_string()],
                r2: vec!["mask_terms".to_string(), "deny_tools".to_string()],
                r1: vec!["tone_mitigate".to_string()],
                r0: vec!["allow".to_string()],
            },
            capabilities: PolicyCapabilities {
                allowed: vec!["ALLOW_CHAT".to_string(), "ALLOW_PLAN_READ".to_string(), "ALLOW_PLAN_WRITE".to_string()],
                denied_on_r2: vec!["DENY_TOOLS".to_string()],
                denied_on_r3: vec!["HARD_BLOCK".to_string(), "DENY_TOOLS".to_string()],
            },
            safe_script: "I can't process that request. How else can I help you today?".to_string(),
        };
        
        Self {
            config: Arc::new(Mutex::new(config)),
            config_path: "default".to_string(),
            last_reload: Arc::new(Mutex::new(SystemTime::now())),
            block_threshold: 0.9,
            downgrade_threshold: 0.7,
            mask_threshold: 0.6,
            #[cfg(feature = "use-llm-security")]
            llm_security: None,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_policy_r0() {
        let policy = PolicyEngine::default();
        let categories = vec![];
        let (risk, action, caps) = policy.evaluate(&Verdict {
            action: Action::Allow,
            categories: vec![],
            confidence: 0.0,
            redactions: vec![],
        }, &categories, 0.0);
        
        assert_eq!(risk, RiskClass::R0);
        assert_eq!(action, Action::Allow);
        assert!(caps.contains(&Capability::AllowChat));
    }

    #[test]
    fn test_policy_r3() {
        let policy = PolicyEngine::default();
        let categories = vec!["hate:slur".to_string()];
        let (risk, action, caps) = policy.evaluate(&Verdict {
            action: Action::Block,
            categories: categories.clone(),
            confidence: 0.8,
            redactions: vec![],
        }, &categories, 0.8);
        
        assert_eq!(risk, RiskClass::R3);
        assert_eq!(action, Action::Block);
        assert!(caps.contains(&Capability::HardBlock));
    }

    #[test]
    fn test_policy_r2() {
        let policy = PolicyEngine::default();
        let categories = vec!["pattern:digits".to_string()];
        let (risk, action, caps) = policy.evaluate(&Verdict {
            action: Action::Mask,
            categories: categories.clone(),
            confidence: 0.7,
            redactions: vec![],
        }, &categories, 0.7);
        
        assert_eq!(risk, RiskClass::R2);
        assert!(action == Action::Mask || action == Action::Downgrade);
        assert!(caps.contains(&Capability::DenyTools));
    }
}