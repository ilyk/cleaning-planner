//! Prometheus metrics definitions

use once_cell::sync::Lazy;
use prometheus::{
    register_histogram, register_int_counter, register_int_counter_vec, register_int_gauge,
    Encoder, Histogram, IntCounter, IntCounterVec, IntGauge, Registry, TextEncoder,
};
use std::sync::Arc;

/// Global metrics registry
static REGISTRY: Lazy<Registry> = Lazy::new(Registry::new);

/// Metrics collector for Clara
#[derive(Clone)]
pub struct Metrics {
    // Connections
    pub ws_connections: IntGauge,

    // Turns
    pub turns_started: IntCounter,
    pub turns_finished: IntCounter,

    // Guardrails
    pub guardrail_hits: IntCounterVec,

    // Tokens
    pub tokens_in: IntCounter,
    pub tokens_out: IntCounter,

    // Latency
    pub ttft_ms: Histogram,
    pub barge_in_stop_ms: Histogram,

    // Errors
    pub errors_total: IntCounterVec,

    // Audio bytes
    pub audio_bytes_in: IntCounter,
    pub audio_bytes_out: IntCounter,
}

impl Metrics {
    /// Create and register all metrics
    pub fn new() -> anyhow::Result<Arc<Self>> {
        let ws_connections = register_int_gauge!(
            "clara_ws_connections",
            "Number of active WebSocket connections"
        )?;
        REGISTRY.register(Box::new(ws_connections.clone()))?;

        let turns_started = register_int_counter!(
            "clara_turns_started_total",
            "Total number of turns started"
        )?;
        REGISTRY.register(Box::new(turns_started.clone()))?;

        let turns_finished = register_int_counter!(
            "clara_turns_finished_total",
            "Total number of turns finished"
        )?;
        REGISTRY.register(Box::new(turns_finished.clone()))?;

        let guardrail_hits = register_int_counter_vec!(
            "clara_guardrail_hits_total",
            "Total number of guardrail hits by category",
            &["category"]
        )?;
        REGISTRY.register(Box::new(guardrail_hits.clone()))?;

        let tokens_in = register_int_counter!(
            "clara_tokens_in_total",
            "Total input tokens processed"
        )?;
        REGISTRY.register(Box::new(tokens_in.clone()))?;

        let tokens_out = register_int_counter!(
            "clara_tokens_out_total",
            "Total output tokens generated"
        )?;
        REGISTRY.register(Box::new(tokens_out.clone()))?;

        let ttft_ms = register_histogram!(
            "clara_ttft_ms",
            "Time to first token in milliseconds",
            vec![50.0, 100.0, 200.0, 350.0, 500.0, 800.0, 1000.0, 2000.0]
        )?;
        REGISTRY.register(Box::new(ttft_ms.clone()))?;

        let barge_in_stop_ms = register_histogram!(
            "clara_barge_in_stop_ms",
            "Barge-in stop latency in milliseconds",
            vec![10.0, 25.0, 50.0, 75.0, 100.0, 150.0, 200.0]
        )?;
        REGISTRY.register(Box::new(barge_in_stop_ms.clone()))?;

        let errors_total = register_int_counter_vec!(
            "clara_errors_total",
            "Total errors by code",
            &["code"]
        )?;
        REGISTRY.register(Box::new(errors_total.clone()))?;

        let audio_bytes_in = register_int_counter!(
            "clara_audio_bytes_in_total",
            "Total audio bytes received"
        )?;
        REGISTRY.register(Box::new(audio_bytes_in.clone()))?;

        let audio_bytes_out = register_int_counter!(
            "clara_audio_bytes_out_total",
            "Total audio bytes sent"
        )?;
        REGISTRY.register(Box::new(audio_bytes_out.clone()))?;

        Ok(Arc::new(Self {
            ws_connections,
            turns_started,
            turns_finished,
            guardrail_hits,
            tokens_in,
            tokens_out,
            ttft_ms,
            barge_in_stop_ms,
            errors_total,
            audio_bytes_in,
            audio_bytes_out,
        }))
    }

    /// Export metrics in Prometheus text format
    pub fn export() -> anyhow::Result<String> {
        let encoder = TextEncoder::new();
        let metric_families = REGISTRY.gather();
        let mut buffer = Vec::new();
        encoder.encode(&metric_families, &mut buffer)?;
        Ok(String::from_utf8(buffer)?)
    }
}

// Note: Default not implemented because new() returns Arc<Metrics>

