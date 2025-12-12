//! Metrics and observability for guardrails

use prometheus::{Counter, Histogram, HistogramOpts, IntCounter, Registry};
use anyhow::Result;

/// Guardrails metrics
pub struct GuardrailMetrics {
    /// Time to first token (guardrail processing time)
    pub ttft_ms: Histogram,

    /// Quarantine buffer dwell time
    pub quarantine_dwell_ms: Histogram,

    /// R2 risk class count
    pub r2_count: Counter,

    /// R3 risk class count
    pub r3_count: Counter,

    /// Mask ranges applied
    pub mask_ranges: Counter,

    /// Interrupts triggered
    pub interrupts: IntCounter,

    /// Tool denials
    pub tool_denies: IntCounter,

    /// Total audio frames processed
    pub frames_processed: Counter,
}

impl GuardrailMetrics {
    pub fn new(registry: &Registry) -> Result<Self, prometheus::Error> {
        let ttft_ms = Histogram::with_opts(
            HistogramOpts::new(
                "clara_guardrail_ttft_ms",
                "Time to first token (guardrail processing overhead) in milliseconds",
            )
            .buckets(vec![10.0, 25.0, 50.0, 80.0, 100.0, 200.0, 500.0])
        )?;

        let quarantine_dwell_ms = Histogram::with_opts(
            HistogramOpts::new(
                "clara_guardrail_quarantine_dwell_ms",
                "Quarantine buffer dwell time in milliseconds",
            )
            .buckets(vec![100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0])
        )?;
        
        let r2_count = Counter::with_opts(
            prometheus::Opts::new(
                "guardrail_r2_count",
                "Total R2 risk class detections",
            )
            .namespace("clara")
        )?;
        
        let r3_count = Counter::with_opts(
            prometheus::Opts::new(
                "guardrail_r3_count",
                "Total R3 risk class detections",
            )
            .namespace("clara")
        )?;
        
        let mask_ranges = Counter::with_opts(
            prometheus::Opts::new(
                "guardrail_mask_ranges",
                "Total mask ranges applied",
            )
            .namespace("clara")
        )?;
        
        let interrupts = IntCounter::with_opts(
            prometheus::Opts::new(
                "guardrail_interrupts",
                "Total interrupts triggered",
            )
            .namespace("clara")
        )?;
        
        let tool_denies = IntCounter::with_opts(
            prometheus::Opts::new(
                "guardrail_tool_denies",
                "Total tool denials",
            )
            .namespace("clara")
        )?;
        
        let frames_processed = Counter::with_opts(
            prometheus::Opts::new(
                "guardrail_frames_processed",
                "Total audio frames processed",
            )
            .namespace("clara")
        )?;
        
        registry.register(Box::new(ttft_ms.clone()))?;
        registry.register(Box::new(quarantine_dwell_ms.clone()))?;
        registry.register(Box::new(r2_count.clone()))?;
        registry.register(Box::new(r3_count.clone()))?;
        registry.register(Box::new(mask_ranges.clone()))?;
        registry.register(Box::new(interrupts.clone()))?;
        registry.register(Box::new(tool_denies.clone()))?;
        registry.register(Box::new(frames_processed.clone()))?;
        
        Ok(Self {
            ttft_ms,
            quarantine_dwell_ms,
            r2_count,
            r3_count,
            mask_ranges,
            interrupts,
            tool_denies,
            frames_processed,
        })
    }
    
    /// Record processing time
    pub fn record_ttft(&self, ms: f64) {
        self.ttft_ms.observe(ms);
    }
    
    /// Record quarantine dwell time
    pub fn record_quarantine_dwell(&self, ms: f64) {
        self.quarantine_dwell_ms.observe(ms);
    }
    
    /// Record R2 detection
    pub fn record_r2(&self) {
        self.r2_count.inc();
    }
    
    /// Record R3 detection
    pub fn record_r3(&self) {
        self.r3_count.inc();
    }
    
    /// Record mask range
    pub fn record_mask_range(&self, count: f64) {
        self.mask_ranges.inc_by(count);
    }
    
    /// Record interrupt
    pub fn record_interrupt(&self) {
        self.interrupts.inc();
    }
    
    /// Record tool denial
    pub fn record_tool_deny(&self) {
        self.tool_denies.inc();
    }
    
    /// Record frame processed
    pub fn record_frame(&self) {
        self.frames_processed.inc();
    }
}

/// Create default metrics registry
pub fn create_registry() -> Registry {
    Registry::new()
}
