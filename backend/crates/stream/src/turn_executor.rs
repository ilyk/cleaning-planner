//! Turn executor - orchestrates guardrails, LLM, and tools

use base64::Engine;
use cleanflow_guardrails::{Action, Verdict};
use cleanflow_llm::{LlmEvent, LlmRealtime};
use cleanflow_protocol::{AudioFormat, OutboundMessage};
use cleanflow_store::Store;
use cleanflow_telemetry::Metrics;
use cleanflow_tools::{CapabilityMask, ToolExecutor};
use std::sync::Arc;
use std::time::Instant;
use tokio::sync::mpsc;
use tracing;

/// Simple pass-through guardrails for text mode (no audio safety checks needed)
struct SimpleGuardrails;

impl SimpleGuardrails {
    fn evaluate(&self, _audio: &[u8], _format: &str) -> anyhow::Result<Verdict> {
        // For text-based chat, we don't need audio guardrails
        // Return an allow verdict
        Ok(Verdict {
            action: Action::Allow,
            categories: vec![],
            confidence: 1.0,
            redactions: vec![],
            risk_class: None,
            capabilities: vec![],
            events: vec![],
        })
    }
}

/// Turn executor state
pub struct TurnExecutor {
    turn_id: String,
    policy_version: String,
    prompt_version: String,
    guardrails: SimpleGuardrails,
    llm: Box<dyn LlmRealtime>,
    capabilities: CapabilityMask,
    _tool_executor: ToolExecutor,
    metrics: Arc<Metrics>,
    start_time: Instant,
    first_token_time: Option<Instant>,
    interrupted: bool,
}

impl TurnExecutor {
    pub fn new(
        turn_id: String,
        policy_version: String,
        prompt_version: String,
        llm: Box<dyn LlmRealtime>,
        store: Store,
        home_id: String,
        metrics: Arc<Metrics>,
    ) -> Self {
        let capabilities = CapabilityMask::full();
        let _tool_executor = ToolExecutor::new(store, capabilities.clone(), home_id);

        Self {
            turn_id: turn_id.clone(),
            policy_version,
            prompt_version,
            guardrails: SimpleGuardrails,
            llm,
            capabilities,
            _tool_executor,
            metrics,
            start_time: Instant::now(),
            first_token_time: None,
            interrupted: false,
        }
    }

    /// Start the turn
    pub fn start(&self) -> anyhow::Result<()> {
        self.llm
            .start_turn(&self.turn_id, &self.policy_version, &self.prompt_version)?;

        self.metrics.turns_started.inc();

        tracing::info!(turn_id = self.turn_id, "Turn started");

        Ok(())
    }

    /// Process audio input through guardrails and forward to LLM
    pub fn process_audio(&mut self, audio: &[u8], format: &str) -> anyhow::Result<()> {
        // Run guardrails
        let verdict = self.guardrails.evaluate(audio, format)?;

        tracing::debug!(
            turn_id = self.turn_id,
            action = ?verdict.action,
            confidence = verdict.confidence,
            "Guardrail verdict"
        );

        // Record guardrail hits
        for category in &verdict.categories {
            self.metrics
                .guardrail_hits
                .with_label_values(&[category])
                .inc();
        }

        // Update capabilities based on verdict
        self.capabilities = CapabilityMask::from_action(&verdict.action);

        match verdict.action {
            Action::Block => {
                tracing::warn!(turn_id = self.turn_id, "Audio blocked by guardrails");
                self.metrics
                    .errors_total
                    .with_label_values(&["GUARDRAIL_BLOCKED"])
                    .inc();
                return Ok(()); // Don't forward to LLM
            }
            Action::Allow | Action::Mask | Action::Downgrade => {
                // Forward to LLM
                self.llm.send_audio_chunk(audio, format)?;
                self.metrics.audio_bytes_in.inc_by(audio.len() as u64);
            }
        }

        Ok(())
    }

    /// Commit audio input
    pub fn commit_input(&self) -> anyhow::Result<()> {
        self.llm.commit_input()?;
        tracing::info!(turn_id = self.turn_id, "Input committed");
        Ok(())
    }

    /// Interrupt (barge-in)
    pub fn interrupt(&mut self) -> anyhow::Result<Instant> {
        let interrupt_start = Instant::now();
        self.llm.interrupt()?;
        self.interrupted = true;

        tracing::info!(turn_id = self.turn_id, "Turn interrupted");

        Ok(interrupt_start)
    }

    /// Subscribe to LLM events and convert to outbound messages
    pub fn subscribe_llm_events(&mut self) -> mpsc::UnboundedReceiver<OutboundMessage> {
        let mut llm_rx = self.llm.subscribe();
        let (tx, rx) = mpsc::unbounded_channel();

        let turn_id = self.turn_id.clone();
        let policy_version = self.policy_version.clone();
        let prompt_version = self.prompt_version.clone();
        let metrics = self.metrics.clone();
        let start_time = self.start_time;

        tokio::spawn(async move {
            while let Some(event) = llm_rx.recv().await {
                match event {
                    LlmEvent::OutputAudioStart => {
                        let msg = OutboundMessage::OutputAudioStart {
                            format: AudioFormat::Opus24k,
                        };
                        tx.send(msg).ok();
                    }
                    LlmEvent::OutputAudioDelta { seq, data, format } => {
                        // Record TTFT for first token
                        if seq == 1 {
                            let ttft = start_time.elapsed().as_millis() as u64;
                            metrics.ttft_ms.observe(ttft as f64);
                            tracing::info!(turn_id = turn_id, ttft_ms = ttft, "First token");
                        }

                        let data_b64 = base64::engine::general_purpose::STANDARD.encode(&data);
                        let format = if format == "opus@24000" {
                            AudioFormat::Opus24k
                        } else {
                            AudioFormat::Pcm16k
                        };

                        metrics.audio_bytes_out.inc_by(data.len() as u64);

                        let msg = OutboundMessage::OutputAudioDelta {
                            seq,
                            format,
                            data: data_b64,
                        };
                        tx.send(msg).ok();
                    }
                    LlmEvent::OutputAudioCommit => {
                        let msg = OutboundMessage::OutputAudioCommit;
                        tx.send(msg).ok();
                    }
                    LlmEvent::OutputTextDelta { text } => {
                        let msg = OutboundMessage::OutputTextDelta { text };
                        tx.send(msg).ok();
                    }
                    LlmEvent::ToolCall { .. } => {
                        // TODO: Execute tool calls
                    }
                    LlmEvent::Error { message } => {
                        tracing::error!(turn_id = turn_id, error = message, "LLM error");
                        metrics
                            .errors_total
                            .with_label_values(&["LLM_ERROR"])
                            .inc();
                    }
                    LlmEvent::Finished {
                        usage_in,
                        usage_out,
                    } => {
                        metrics.tokens_in.inc_by(usage_in as u64);
                        metrics.tokens_out.inc_by(usage_out as u64);
                        metrics.turns_finished.inc();

                        let latency_ms = start_time.elapsed().as_millis() as u64;

                        let msg = OutboundMessage::TurnFinish {
                            turn_id: turn_id.clone(),
                            metadata: cleanflow_protocol::TurnFinishMetadata {
                                usage: cleanflow_protocol::UsageMetadata {
                                    tokens_in: usage_in,
                                    tokens_out: usage_out,
                                    audio_in_seconds: 0.0, // TODO: Track
                                    audio_out_seconds: 0.0, // TODO: Track
                                },
                                latency_ms,
                                ttft_ms: None, // TODO: Track
                                policy_version: policy_version.clone(),
                                prompt_version: prompt_version.clone(),
                            },
                        };
                        tx.send(msg).ok();

                        tracing::info!(
                            turn_id = turn_id,
                            latency_ms = latency_ms,
                            "Turn finished"
                        );

                        break;
                    }
                }
            }
        });

        rx
    }
}

