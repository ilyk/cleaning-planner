//! Data storage layer for Clara backend
//!
//! Provides traits for data access and implementations for Postgres, Redis,
//! and in-memory stores (for testing).

pub mod memory;
pub mod postgres;
pub mod redis_store;
pub mod traits;

pub use traits::{Plan, PlanRepo, Session, SessionRepo, SessionState, TelemetryRepo, Turn, TurnMetrics};

use anyhow::Result;
use sqlx::postgres::PgPoolOptions;
use std::sync::Arc;
use std::time::Duration;

/// Database connection pool
pub type DbPool = sqlx::PgPool;

/// Redis connection manager
pub type RedisPool = redis::aio::ConnectionManager;

/// Initialize Postgres connection pool
pub async fn init_postgres(database_url: &str, max_connections: u32) -> Result<DbPool> {
    let pool = PgPoolOptions::new()
        .max_connections(max_connections)
        .acquire_timeout(Duration::from_secs(5))
        .connect(database_url)
        .await?;

    // Run migrations
    sqlx::migrate!("./migrations")
        .run(&pool)
        .await
        .ok(); // Ignore migration errors for now

    Ok(pool)
}

/// Initialize Redis connection manager
pub async fn init_redis(redis_url: &str) -> Result<RedisPool> {
    let client = redis::Client::open(redis_url)?;
    let manager = redis::aio::ConnectionManager::new(client).await?;
    Ok(manager)
}

/// Store bundle containing all repositories
#[derive(Clone)]
pub struct Store {
    pub session: Arc<dyn SessionRepo>,
    pub plan: Arc<dyn PlanRepo>,
    pub telemetry: Arc<dyn TelemetryRepo>,
}

impl Store {
    /// Create store with Postgres and Redis backends
    pub fn new(
        session: Arc<dyn SessionRepo>,
        plan: Arc<dyn PlanRepo>,
        telemetry: Arc<dyn TelemetryRepo>,
    ) -> Self {
        Self {
            session,
            plan,
            telemetry,
        }
    }

    /// Create in-memory store for testing
    pub fn memory() -> Self {
        Self {
            session: Arc::new(memory::MemorySessionRepo::new()),
            plan: Arc::new(memory::MemoryPlanRepo::new()),
            telemetry: Arc::new(memory::MemoryTelemetryRepo::new()),
        }
    }
}

