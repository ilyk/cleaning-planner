//! Main guardrails pipeline integrating all components

use crate::deterministic::{DeterministicChecker, DeterministicResult};
use crate::embedding::{DefaultEmbeddingSafetyProvider, EmbeddingSafetyProvider, SafetyScores};
use crate::events::{CapabilityUpdate, GuardrailEvent, GuardrailInterrupt, GuardrailMask, GuardrailNotice};
use crate::keyword::{DefaultKwsProvider, KwsProvider, KeywordFinding};
use crate::output_filter::OutputFilter;
use crate::policy::{Capability, PolicyEngine, RiskClass};
use crate::quarantine::{AudioFrame, QuarantineBuffer};
use anyhow::{Context, Result};
use async_trait::async_trait;
use std::sync::Arc;
use tokio::sync::Mutex;

/// Audio span with metadata
#[derive(Debug, Clone)]
pub struct AudioSpan {
    pub data: Vec<u8>,
    pub format: String,
    pub seq: u64,
}

/// Span of audio to redact
#[derive(Debug, Clone)]
pub struct Span {
    pub start: usize,
    pub end: usize,
}

/// Action to take based on guardrail verdict
#[derive(Debug, Clone, PartialEq, Eq, Copy)]
pub enum Action {
    Allow,
    Mask,
    Downgrade,
    Block,
}

/// Guardrail verdict
#[derive(Debug, Clone)]
pub struct Verdict {
    pub action: Action,
    pub categories: Vec<String>,
    pub confidence: f32,
    pub redactions: Vec<Span>,
    pub risk_class: Option<RiskClass>,
    pub capabilities: Vec<Capability>,
    pub events: Vec<GuardrailEvent>,
}

/// Main guardrails pipeline
pub struct GuardrailPipeline {
    quarantine: Arc<Mutex<QuarantineBuffer>>,
    deterministic: Arc<DeterministicChecker>,
    kws: Arc<dyn KwsProvider>,
    embeddings: Arc<dyn EmbeddingSafetyProvider>,
    policy: Arc<PolicyEngine>,
    output_filter: Arc<Mutex<OutputFilter>>,
    config: PipelineConfig,
}

/// Pipeline configuration
#[derive(Debug, Clone)]
pub struct PipelineConfig {
    pub quarantine_span_ms: u64,
    pub quarantine_slide_ms: u64,
    pub sample_rate_hz: u32,
    pub kws_enabled: bool,
    pub embeddings_enabled: bool,
}

impl GuardrailPipeline {
    /// Create new pipeline with default providers
    pub fn new(config: PipelineConfig) -> Result<Self> {
        let quarantine = Arc::new(Mutex::new(QuarantineBuffer::new(
            config.quarantine_span_ms,
            config.quarantine_slide_ms,
            config.sample_rate_hz,
        )));
        
        let deterministic = Arc::new(DeterministicChecker::default());
        let kws: Arc<dyn KwsProvider> = Arc::new(DefaultKwsProvider::default());
        let embeddings: Arc<dyn EmbeddingSafetyProvider> = Arc::new(DefaultEmbeddingSafetyProvider::default());
        
        // Load policy from config
        let policy_path = std::path::Path::new("config/policy.guardrails.yaml");
        let policy = if policy_path.exists() {
            Arc::new(PolicyEngine::from_yaml(policy_path)?)
        } else {
            Arc::new(PolicyEngine::default())
        };
        
        let output_filter = Arc::new(Mutex::new(OutputFilter::default()));
        
        Ok(Self {
            quarantine,
            deterministic,
            kws,
            embeddings,
            policy,
            output_filter,
            config,
        })
    }

    /// Process audio frame through pipeline
    pub async fn process(&self, span: AudioSpan) -> Result<Verdict> {
        // Push to quarantine buffer
        {
            let mut q = self.quarantine.lock().await;
            let frame = AudioFrame {
                seq: span.seq,
                data: span.data.clone(),
                timestamp: std::time::Instant::now(),
                format: span.format.clone(),
            };
            q.push(frame);
        }
        
        // Check if we have enough data for analysis
        let quarantine_guard = self.quarantine.lock().await;
        if !quarantine_guard.has_span_ready() {
            return Ok(Verdict {
                action: Action::Allow,
                categories: vec![],
                confidence: 0.0,
                redactions: vec![],
                risk_class: Some(RiskClass::R0),
                capabilities: vec![Capability::AllowChat, Capability::AllowPlanRead, Capability::AllowPlanWrite],
                events: vec![],
            });
        }
        
        let audio_span = quarantine_guard.peek_span();
        drop(quarantine_guard);
        
        // Step 1: Deterministic checks
        let det_result = self.deterministic.check(&audio_span, &span.format);
        
        if det_result.block {
            return Ok(self.create_block_verdict(
                det_result.reason.as_deref().unwrap_or("Deterministic check failed"),
                RiskClass::R3,
            ));
        }
        
        // Step 2: Keyword spotting (if enabled)
        let mut keyword_findings = Vec::new();
        if self.config.kws_enabled {
            keyword_findings = self.kws.analyze(&audio_span).await
                .context("KWS analysis failed")?;
        }
        
        // Step 3: Embedding classification (if enabled)
        let mut embedding_scores = SafetyScores {
            harassment: 0.0,
            sexual: 0.0,
            violence: 0.0,
            self_harm: 0.0,
            hate: 0.0,
            pii: 0.0,
        };
        
        if self.config.embeddings_enabled {
            embedding_scores = self.embeddings.scores(&audio_span).await
                .context("Embedding analysis failed")?;
        }
        
        // Step 4: Build categories from findings
        let mut categories = Vec::new();
        let mut redactions = Vec::new();
        let mut max_confidence = det_result.vad_score;
        
        for finding in &keyword_findings {
            categories.push(format!("keyword:{}", finding.keyword));
            redactions.push(Span {
                start: finding.span.start,
                end: finding.span.end,
            });
            max_confidence = max_confidence.max(finding.confidence);
        }
        
        // Add embedding categories if thresholds exceeded
        if embedding_scores.harassment >= 0.72 {
            categories.push("embedding:harassment".to_string());
            max_confidence = max_confidence.max(embedding_scores.harassment);
        }
        if embedding_scores.sexual >= 0.68 {
            categories.push("embedding:sexual".to_string());
            max_confidence = max_confidence.max(embedding_scores.sexual);
        }
        if embedding_scores.violence >= 0.70 {
            categories.push("embedding:violence".to_string());
            max_confidence = max_confidence.max(embedding_scores.violence);
        }
        if embedding_scores.self_harm >= 0.60 {
            categories.push("embedding:self_harm".to_string());
            max_confidence = max_confidence.max(embedding_scores.self_harm);
        }
        if embedding_scores.hate >= 0.65 {
            categories.push("embedding:hate".to_string());
            max_confidence = max_confidence.max(embedding_scores.hate);
        }
        if embedding_scores.pii >= 0.62 {
            categories.push("embedding:pii".to_string());
            max_confidence = max_confidence.max(embedding_scores.pii);
        }
        
        // Add digit pattern
        if det_result.digit_count >= 10 {
            categories.push("pattern:digits".to_string());
        }
        
        // Step 5: Policy engine evaluation
        let temp_verdict = Verdict {
            action: Action::Allow,
            categories: categories.clone(),
            confidence: max_confidence,
            redactions: redactions.clone(),
            risk_class: None,
            capabilities: vec![],
            events: vec![],
        };
        
        let (risk_class, action, capabilities) = self.policy.evaluate(&temp_verdict, &categories, max_confidence);
        
        // Step 6: Generate events
        let events = self.generate_events(risk_class, &categories, &redactions, &capabilities);
        
        // Step 7: Apply actions (mask ranges if needed)
        if action == Action::Mask {
            let mut q = self.quarantine.lock().await;
            for redaction in &redactions {
                // Estimate time ranges from byte positions (rough approximation for Opus)
                let start_ms = (redaction.start * 20) as u64; // Rough estimate
                let end_ms = (redaction.end * 20) as u64;
                q.mask_range(start_ms, end_ms);
            }
        }
        
        // Step 8: Consume processed span
        if action == Action::Allow || action == Action::Downgrade {
            let mut q = self.quarantine.lock().await;
            q.consume_slide();
        }
        
        Ok(Verdict {
            action,
            categories,
            confidence: max_confidence,
            redactions,
            risk_class: Some(risk_class),
            capabilities,
            events,
        })
    }

    /// Generate events based on verdict
    fn generate_events(
        &self,
        risk_class: RiskClass,
        categories: &[String],
        redactions: &[Span],
        capabilities: &[Capability],
    ) -> Vec<GuardrailEvent> {
        let mut events = Vec::new();
        
        match risk_class {
            RiskClass::R3 => {
                // Send interrupt and notice
                events.push(GuardrailEvent::Interrupt(GuardrailInterrupt::new("policy_block")));
                
                let reason = categories.first()
                    .map(|c| c.clone())
                    .unwrap_or_else(|| "unsafe_content".to_string());
                
                events.push(GuardrailEvent::Notice(GuardrailNotice::new(
                    "R3",
                    reason,
                    self.policy.safe_script(),
                )));
            }
            RiskClass::R2 => {
                // Send notice and capability update
                let reason = categories.iter()
                    .find(|c| c.contains("digits") || c.contains("pii"))
                    .map(|c| c.clone())
                    .unwrap_or_else(|| "sensitive_content".to_string());
                
                events.push(GuardrailEvent::Notice(GuardrailNotice::new(
                    "R2",
                    reason,
                    "I can't process personal numbers. Let's continue without them.".to_string(),
                )));
                
                // Send mask events for redactions
                for redaction in redactions {
                    let start_ms = (redaction.start * 20) as u64;
                    let end_ms = (redaction.end * 20) as u64;
                    events.push(GuardrailEvent::Mask(GuardrailMask::new(start_ms, end_ms)));
                }
                
                // Capability update
                let allow: Vec<String> = capabilities.iter()
                    .filter_map(|c| match c {
                        Capability::AllowChat => Some("ALLOW_CHAT".to_string()),
                        Capability::AllowPlanRead => Some("ALLOW_PLAN_READ".to_string()),
                        _ => None,
                    })
                    .collect();
                let deny: Vec<String> = capabilities.iter()
                    .filter_map(|c| match c {
                        Capability::DenyTools => Some("DENY_TOOLS".to_string()),
                        _ => None,
                    })
                    .collect();
                
                if !allow.is_empty() || !deny.is_empty() {
                    events.push(GuardrailEvent::CapabilityUpdate(CapabilityUpdate::new(allow, deny)));
                }
            }
            RiskClass::R1 => {
                // Capability update for downgrade
                let allow: Vec<String> = capabilities.iter()
                    .filter_map(|c| match c {
                        Capability::AllowChat => Some("ALLOW_CHAT".to_string()),
                        Capability::AllowPlanRead => Some("ALLOW_PLAN_READ".to_string()),
                        _ => None,
                    })
                    .collect();
                
                if !allow.is_empty() {
                    events.push(GuardrailEvent::CapabilityUpdate(CapabilityUpdate::new(allow, vec![])));
                }
            }
            RiskClass::R0 => {
                // No events for clean content
            }
        }
        
        events
    }

    /// Create block verdict
    fn create_block_verdict(&self, reason: &str, risk_class: RiskClass) -> Verdict {
        Verdict {
            action: Action::Block,
            categories: vec![reason.to_string()],
            confidence: 1.0,
            redactions: vec![],
            risk_class: Some(risk_class),
            capabilities: vec![Capability::HardBlock, Capability::DenyTools],
            events: vec![
                GuardrailEvent::Interrupt(GuardrailInterrupt::new("policy_block")),
                GuardrailEvent::Notice(GuardrailNotice::new(
                    "R3",
                    reason,
                    self.policy.safe_script(),
                )),
            ],
        }
    }

    /// Filter output text for PII
    pub async fn filter_output(&self, text: &str) -> Result<String> {
        let filter = self.output_filter.lock().await;
        Ok(filter.filter_text(text))
    }

    /// Check output token rate
    pub async fn check_output_rate(&self, num_tokens: usize) -> bool {
        let mut filter = self.output_filter.lock().await;
        filter.check_token_rate(num_tokens)
    }

    /// Reset output filter
    pub async fn reset_output_filter(&self) {
        let mut filter = self.output_filter.lock().await;
        filter.reset();
    }
}

impl Default for GuardrailPipeline {
    fn default() -> Self {
        Self::new(PipelineConfig {
            quarantine_span_ms: 1200,
            quarantine_slide_ms: 200,
            sample_rate_hz: 24000,
            kws_enabled: true,
            embeddings_enabled: true,
        }).unwrap()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_pipeline_clean_audio() {
        let pipeline = GuardrailPipeline::default();
        let span = AudioSpan {
            data: vec![0x01; 1000],
            format: "opus@24000".to_string(),
            seq: 1,
        };
        
        // Push multiple spans to trigger analysis
        for i in 0..10 {
            let mut s = span.clone();
            s.seq = i as u64;
            let _ = pipeline.process(s).await;
        }
        
        // Should eventually allow
        let verdict = pipeline.process(span).await.unwrap();
        assert!(matches!(verdict.action, Action::Allow | Action::Downgrade));
    }
}