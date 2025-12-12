//! Business logic services

pub mod plan_service;
pub mod printable_service;
pub mod idempotency_service;
pub mod telemetry_service;
pub mod idempotency_store;
pub mod real_plan_service;
pub mod real_printable_service;
pub mod real_lookup_service;
pub mod cleanflow_optimizer;
pub mod cleanflow_suggestion_service;

pub use plan_service::PlanService;
pub use printable_service::PrintableService;
pub use idempotency_service::IdempotencyService;
pub use telemetry_service::TelemetryService;
pub use idempotency_store::{IdempotencyStore, CachedResponse, DbIdempotencyStore, MemoryIdempotencyStore};
pub use real_plan_service::{RealPlanService, DbPlanService};
pub use real_printable_service::RealPrintableService;
pub use real_lookup_service::{RealLookupService, HomeProfile};
pub use cleanflow_optimizer::{CleanFlowOptimizer, OptimizationMode, DbCleanFlowOptimizer};
pub use cleanflow_suggestion_service::{
    CleanFlowSuggestionService, LlmSuggestionService, Suggestion as DomainSuggestion,
};
