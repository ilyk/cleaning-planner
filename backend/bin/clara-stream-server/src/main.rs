//! Clara Stream Server
//!
//! Production-ready streaming backend for Clara voice assistant.

use anyhow::Result;
use clara_api::{create_router, AppState};
use clara_auth::JwtValidator;
use clara_config::AppConfig;
use clara_session::SessionManager;
use clara_store::Store;
use clara_telemetry::{setup_tracing, Metrics};
use std::sync::Arc;
use tokio::signal;
use tracing::{error, info};

#[tokio::main]
async fn main() -> Result<()> {
    // Setup tracing
    setup_tracing()?;

    info!("Clara Stream Server starting...");

    // Load configuration
    let config = AppConfig::from_env().map_err(|e| {
        error!(error = %e, "Failed to load configuration");
        e
    })?;

    info!(
        bind_address = config.bind_address(),
        "Configuration loaded"
    );

    // Initialize metrics
    let metrics = Metrics::new()?;
    info!("Metrics initialized");

    // Initialize stores
    let (store, db_pool) = if config.database.url.starts_with("memory://") || config.redis.url.starts_with("memory://") {
        // Use in-memory store for testing
        info!("Using in-memory store");
        // Create a dummy postgres pool that won't be used
        let pg_pool = clara_store::init_postgres("postgres://postgres:postgres@localhost/clara", 1).await?;
        (Store::memory(), pg_pool)
    } else {
        // Initialize Redis for sessions
        let redis_pool = clara_store::init_redis(&config.redis.url).await?;
        info!("Redis connected");

        // Initialize Postgres for plans and telemetry
        let pg_pool = clara_store::init_postgres(&config.database.url, config.database.max_connections).await?;
        info!("Postgres connected");

        // Create store with real implementations
        let store = Store::new(
            Arc::new(clara_store::redis_store::RedisSessionRepo::new(redis_pool)),
            Arc::new(clara_store::postgres::PostgresPlanRepo::new(pg_pool.clone())),
            Arc::new(clara_store::postgres::PostgresTelemetryRepo::new(pg_pool.clone())),
        );
        (store, pg_pool)
    };

    info!("Store initialized");

    // Create JWT validator
    let jwt_validator = JwtValidator::new(config.auth.jwt_secret.clone());
    info!("JWT validator initialized");

    // Create session manager
    let session_manager = SessionManager::new(
        store.session.clone(),
        config.rate_limit.turns_per_minute,
        config.rate_limit.burst_capacity,
        config.max_input_duration(),
        config.max_output_duration(),
    );
    info!("Session manager initialized");

    // Create application state
    let app_state = AppState::new(
        config.clone(),
        jwt_validator,
        session_manager,
        store,
        db_pool,
        metrics,
    );

    // Create router
    let app = create_router(app_state);

    // Start server
    let listener = tokio::net::TcpListener::bind(&config.bind_address()).await?;

    info!(
        bind_address = config.bind_address(),
        "Server listening"
    );

    // Serve with graceful shutdown
    axum::serve(listener, app)
        .with_graceful_shutdown(shutdown_signal())
        .await?;

    info!("Server stopped");

    Ok(())
}

/// Wait for shutdown signal
async fn shutdown_signal() {
    let ctrl_c = async {
        signal::ctrl_c()
            .await
            .expect("Failed to install Ctrl+C handler");
    };

    #[cfg(unix)]
    let terminate = async {
        signal::unix::signal(signal::unix::SignalKind::terminate())
            .expect("Failed to install signal handler")
            .recv()
            .await;
    };

    #[cfg(not(unix))]
    let terminate = std::future::pending::<()>();

    tokio::select! {
        _ = ctrl_c => {},
        _ = terminate => {},
    }

    info!("Shutdown signal received");
}

