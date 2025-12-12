//! Guardrails pipeline for audio-first validation
//!
//! Implements comprehensive safety checks for Clara's realtime voice:
//! - Quarantine buffer with sliding window
//! - Deterministic checks (VAD, codec, LID, digit patterns)
//! - Keyword/phoneme spotting
//! - Audio embedding safety classification
//! - Policy engine with risk classes (R0-R3)
//! - Output filtering (PII redaction, rate limiting)
//! - Event generation for client notifications

pub mod deterministic;
pub mod embedding;
pub mod events;
pub mod ingress;
pub mod keyword;
pub mod lid;
pub mod output_filter;
pub mod pipeline;
pub mod policy;
pub mod quarantine;
pub mod vad;
pub mod metrics;

pub use events::{CapabilityUpdate, GuardrailEvent, GuardrailInterrupt, GuardrailMask, GuardrailNotice};
pub use pipeline::{Action, AudioSpan, GuardrailPipeline, Span, Verdict};
pub use policy::{Capability, RiskClass};
pub use quarantine::QuarantineBuffer;

use anyhow::Result;

/// Guardrails trait (legacy for backward compatibility)
pub trait Guardrails: Send + Sync {
    /// Evaluate an audio window and return verdict
    fn evaluate_window(&self, audio_window: &[u8], format: &str) -> Result<Verdict>;
}

