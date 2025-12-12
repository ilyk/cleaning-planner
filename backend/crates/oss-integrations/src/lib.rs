//! OSS Security Library Integrations
//!
//! This crate provides trait-based adapters for optional security libraries.
//! Each integration is behind a feature flag and provides a mock implementation
//! when the actual library is not available.
//!
//! ## Real Integrations
//! - `llm-security` - Red Asgard's LLM security library (crates.io)
//!
//! ## Future Integrations (stubbed)
//! - path-security, blockchain-runtime, worker-capabilities, etc.

pub mod llm_security_adapter;
pub mod path_security_adapter;
pub mod blockchain_adapter;
pub mod capabilities_adapter;
pub mod module_registry_adapter;
pub mod threat_intel_adapter;
pub mod quantum_shield_adapter;

// LLM Security - real integration available
pub use llm_security_adapter::{
    LlmSecurityProvider,
    SecurityAnalysis,
    SecurityConfig,
    MockLlmSecurityProvider,
    create_provider as create_llm_security_provider,
};

#[cfg(feature = "use-llm-security")]
pub use llm_security_adapter::RealLlmSecurityProvider;

// Future integrations (stubbed)
pub use path_security_adapter::PathSecurityProvider;
pub use blockchain_adapter::BlockchainAuditor;
pub use capabilities_adapter::CapabilitiesProvider;
pub use module_registry_adapter::ModuleRegistry;
pub use threat_intel_adapter::ThreatIntelProvider;
pub use quantum_shield_adapter::QuantumShieldProvider;

