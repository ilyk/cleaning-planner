//! Telemetry, metrics, and tracing for Clara backend

pub mod metrics;
pub mod tracing_setup;

pub use metrics::Metrics;
pub use tracing_setup::setup_tracing;

