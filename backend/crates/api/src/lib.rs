//! API layer - REST endpoints and WebSocket upgrade

pub mod errors;
pub mod handlers;
pub mod idempotency;
pub mod lookup;
pub mod onboarding;
pub mod pagination;
pub mod plans;
pub mod routes;
pub mod state;
pub mod cleanflow;
pub mod cleanflow_history;

pub use errors::CleanFlowError;
pub use routes::create_router;
pub use state::AppState;

