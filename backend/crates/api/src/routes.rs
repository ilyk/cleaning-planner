//! Router configuration

use crate::{handlers, plans, lookup, cleanflow, cleanflow_history, onboarding, idempotency, state::AppState};
use axum::{
    middleware,
    routing::{get, post},
    Router,
};
use cleanflow_auth;
use tower_http::{
    cors::{Any, CorsLayer},
    trace::TraceLayer,
};

/// Create the application router
pub fn create_router(state: AppState) -> Router {
    // Public routes (no auth)
    let public_routes = Router::new()
        .route("/health", get(handlers::health_check))
        .route("/health/details", get(handlers::health_details_check))
        .route("/metrics", get(handlers::metrics_handler));

    // Plan API routes (require auth, with idempotency for mutating operations)
    let plan_routes = Router::new()
        .route("/v1/plan/generate", post(plans::generate_plan))
        .route("/v1/plan/revise", post(plans::revise_plan))
        .route("/v1/plan/:plan_id", get(plans::get_plan))
        .route("/v1/plan/printable", post(plans::generate_printable))
        .route("/v1/family/assign", post(plans::assign_family))
        .route("/v1/telemetry/complete", post(plans::record_telemetry))
        .route("/v1/plans", get(plans::list_plans))
        .route_layer(middleware::from_fn_with_state(
            state.clone(),
            idempotency::idempotency_middleware,
        ));

    // Lookup & CleanFlow API routes (require auth)
    let lookup_routes = Router::new()
        .route("/v1/homes/:home_id", get(lookup::get_home))
        .route("/v1/task-templates", get(lookup::get_task_templates))
        .route("/v1/cleanflow/home", post(cleanflow::register_home))
        .route("/v1/cleanflow/home/initial-plan", post(cleanflow::generate_initial_plan))
        .route("/v1/cleanflow/plan/optimize", post(cleanflow::optimize_plan))
        .route("/v1/cleanflow/plan/suggestions", get(cleanflow::get_suggestions))
        .route("/v1/cleanflow/history/batch", post(cleanflow_history::ingest_history_batch))
        .route("/v1/cleanflow/history/summary", get(cleanflow_history::get_history_summary));

    // Clara Voice API routes (require auth)
    let cleanflow_clara_routes = Router::new()
        .route("/v1/clara/session", post(handlers::create_session))
        .route("/v1/clara/session/turn", post(handlers::start_turn))
        .route("/v1/clara/stream", get(handlers::websocket_handler))
        .route("/v1/clara/cancel", post(handlers::cancel_turn));

    // Onboarding API routes (require auth)
    let onboarding_routes = Router::new()
        .route("/v1/onboarding/extract", post(onboarding::extract_from_conversation))
        .route("/v1/onboarding/setup", post(onboarding::manual_home_setup));

    // All protected routes
    let protected_routes = Router::new()
        .merge(plan_routes)
        .merge(lookup_routes)
        .merge(cleanflow_clara_routes)
        .merge(onboarding_routes)
        .route_layer(middleware::from_fn_with_state(
            state.jwt_validator.clone(),
            cleanflow_auth::middleware::auth_middleware,
        ));

    // Combine routes
    Router::new()
        .merge(public_routes)
        .merge(protected_routes)
        .layer(
            CorsLayer::new()
                .allow_origin(Any)
                .allow_methods(Any)
                .allow_headers(Any),
        )
        .layer(TraceLayer::new_for_http())
        .with_state(state)
}

