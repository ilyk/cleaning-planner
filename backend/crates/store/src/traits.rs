//! Repository traits for data access

use anyhow::Result;
use async_trait::async_trait;
use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

/// Session state
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub enum SessionState {
    Idle,
    Capturing,
    Forwarding,
    Finished,
    Blocked,
}

/// Session data
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Session {
    pub session_id: String,
    pub user_id: String,
    pub home_id: String,
    pub state: SessionState,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
}

/// Turn data
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Turn {
    pub turn_id: String,
    pub session_id: String,
    pub state: SessionState,
    pub started_at: DateTime<Utc>,
    pub finished_at: Option<DateTime<Utc>>,
}

/// Session repository trait
#[async_trait]
pub trait SessionRepo: Send + Sync {
    /// Create a new session
    async fn create_session(&self, user_id: &str, home_id: &str) -> Result<Session>;

    /// Get session by ID
    async fn get_session(&self, session_id: &str) -> Result<Option<Session>>;

    /// Update session state
    async fn update_session_state(
        &self,
        session_id: &str,
        state: SessionState,
    ) -> Result<()>;

    /// Check if session has active connection
    async fn has_active_connection(&self, session_id: &str) -> Result<bool>;

    /// Mark session as connected
    async fn mark_connected(&self, session_id: &str) -> Result<()>;

    /// Mark session as disconnected
    async fn mark_disconnected(&self, session_id: &str) -> Result<()>;

    /// Create a new turn
    async fn create_turn(&self, session_id: &str) -> Result<Turn>;

    /// Get turn by ID
    async fn get_turn(&self, turn_id: &str) -> Result<Option<Turn>>;

    /// Update turn state
    async fn update_turn_state(&self, turn_id: &str, state: SessionState) -> Result<()>;

    /// Check rate limit for session (returns remaining capacity)
    async fn check_rate_limit(&self, session_id: &str, limit: u32, window_secs: u64)
        -> Result<u32>;

    /// Increment rate limit counter
    async fn increment_rate_limit(&self, session_id: &str, window_secs: u64) -> Result<()>;
}

/// Plan data (simplified)
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Plan {
    pub plan_id: Uuid,
    pub home_id: String,
    pub title: String,
    pub content: serde_json::Value,
    pub created_at: DateTime<Utc>,
}

/// Plan repository trait
#[async_trait]
pub trait PlanRepo: Send + Sync {
    /// Create a new plan
    async fn create_plan(&self, home_id: &str, title: &str, content: serde_json::Value)
        -> Result<Plan>;

    /// Get plan by ID
    async fn get_plan(&self, plan_id: Uuid) -> Result<Option<Plan>>;

    /// Update plan
    async fn update_plan(&self, plan_id: Uuid, content: serde_json::Value) -> Result<()>;

    /// List plans for home
    async fn list_plans(&self, home_id: &str) -> Result<Vec<Plan>>;
}

/// Turn metrics
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TurnMetrics {
    pub turn_id: String,
    pub session_id: String,
    pub tokens_in: u32,
    pub tokens_out: u32,
    pub audio_in_seconds: f32,
    pub audio_out_seconds: f32,
    pub latency_ms: u64,
    pub ttft_ms: Option<u64>,
    pub policy_version: String,
    pub prompt_version: String,
    pub guardrail_hits: Vec<String>,
    pub recorded_at: DateTime<Utc>,
}

/// Telemetry repository trait
#[async_trait]
pub trait TelemetryRepo: Send + Sync {
    /// Record turn metrics
    async fn record_turn_metrics(&self, metrics: TurnMetrics) -> Result<()>;

    /// Get metrics for session
    async fn get_session_metrics(&self, session_id: &str) -> Result<Vec<TurnMetrics>>;
}

