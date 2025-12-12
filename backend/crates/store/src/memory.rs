//! In-memory implementations for testing

use crate::traits::*;
use anyhow::Result;
use async_trait::async_trait;
use chrono::Utc;
use std::collections::HashMap;
use std::sync::{Arc, Mutex};
use uuid::Uuid;

/// In-memory session repository
#[derive(Clone)]
pub struct MemorySessionRepo {
    sessions: Arc<Mutex<HashMap<String, Session>>>,
    turns: Arc<Mutex<HashMap<String, Turn>>>,
    connections: Arc<Mutex<HashMap<String, bool>>>,
    rate_limits: Arc<Mutex<HashMap<String, Vec<i64>>>>,
}

impl MemorySessionRepo {
    pub fn new() -> Self {
        Self {
            sessions: Arc::new(Mutex::new(HashMap::new())),
            turns: Arc::new(Mutex::new(HashMap::new())),
            connections: Arc::new(Mutex::new(HashMap::new())),
            rate_limits: Arc::new(Mutex::new(HashMap::new())),
        }
    }
}

impl Default for MemorySessionRepo {
    fn default() -> Self {
        Self::new()
    }
}

#[async_trait]
impl SessionRepo for MemorySessionRepo {
    async fn create_session(&self, user_id: &str, home_id: &str) -> Result<Session> {
        let session = Session {
            session_id: format!("sess-{}", Uuid::new_v4()),
            user_id: user_id.to_string(),
            home_id: home_id.to_string(),
            state: SessionState::Idle,
            created_at: Utc::now(),
            updated_at: Utc::now(),
        };

        self.sessions
            .lock()
            .unwrap()
            .insert(session.session_id.clone(), session.clone());

        Ok(session)
    }

    async fn get_session(&self, session_id: &str) -> Result<Option<Session>> {
        Ok(self.sessions.lock().unwrap().get(session_id).cloned())
    }

    async fn update_session_state(&self, session_id: &str, state: SessionState) -> Result<()> {
        if let Some(session) = self.sessions.lock().unwrap().get_mut(session_id) {
            session.state = state;
            session.updated_at = Utc::now();
        }
        Ok(())
    }

    async fn has_active_connection(&self, session_id: &str) -> Result<bool> {
        Ok(*self
            .connections
            .lock()
            .unwrap()
            .get(session_id)
            .unwrap_or(&false))
    }

    async fn mark_connected(&self, session_id: &str) -> Result<()> {
        self.connections
            .lock()
            .unwrap()
            .insert(session_id.to_string(), true);
        Ok(())
    }

    async fn mark_disconnected(&self, session_id: &str) -> Result<()> {
        self.connections
            .lock()
            .unwrap()
            .insert(session_id.to_string(), false);
        Ok(())
    }

    async fn create_turn(&self, session_id: &str) -> Result<Turn> {
        let turn = Turn {
            turn_id: format!("turn-{}", Uuid::new_v4()),
            session_id: session_id.to_string(),
            state: SessionState::Idle,
            started_at: Utc::now(),
            finished_at: None,
        };

        self.turns
            .lock()
            .unwrap()
            .insert(turn.turn_id.clone(), turn.clone());

        Ok(turn)
    }

    async fn get_turn(&self, turn_id: &str) -> Result<Option<Turn>> {
        Ok(self.turns.lock().unwrap().get(turn_id).cloned())
    }

    async fn update_turn_state(&self, turn_id: &str, state: SessionState) -> Result<()> {
        if let Some(turn) = self.turns.lock().unwrap().get_mut(turn_id) {
            turn.state = state;
        }
        Ok(())
    }

    async fn check_rate_limit(
        &self,
        session_id: &str,
        limit: u32,
        window_secs: u64,
    ) -> Result<u32> {
        let now = Utc::now().timestamp();
        let window_start = now - window_secs as i64;

        let mut limits = self.rate_limits.lock().unwrap();
        let timestamps = limits.entry(session_id.to_string()).or_default();

        // Remove expired timestamps
        timestamps.retain(|&ts| ts > window_start);

        let count = timestamps.len() as u32;
        let remaining = limit.saturating_sub(count);

        Ok(remaining)
    }

    async fn increment_rate_limit(&self, session_id: &str, _window_secs: u64) -> Result<()> {
        let now = Utc::now().timestamp();
        let mut limits = self.rate_limits.lock().unwrap();
        limits
            .entry(session_id.to_string())
            .or_default()
            .push(now);
        Ok(())
    }
}

/// In-memory plan repository
#[derive(Clone)]
pub struct MemoryPlanRepo {
    plans: Arc<Mutex<HashMap<Uuid, Plan>>>,
}

impl MemoryPlanRepo {
    pub fn new() -> Self {
        Self {
            plans: Arc::new(Mutex::new(HashMap::new())),
        }
    }
}

impl Default for MemoryPlanRepo {
    fn default() -> Self {
        Self::new()
    }
}

#[async_trait]
impl PlanRepo for MemoryPlanRepo {
    async fn create_plan(
        &self,
        home_id: &str,
        title: &str,
        content: serde_json::Value,
    ) -> Result<Plan> {
        let plan = Plan {
            plan_id: Uuid::new_v4(),
            home_id: home_id.to_string(),
            title: title.to_string(),
            content,
            created_at: Utc::now(),
        };

        self.plans
            .lock()
            .unwrap()
            .insert(plan.plan_id, plan.clone());

        Ok(plan)
    }

    async fn get_plan(&self, plan_id: Uuid) -> Result<Option<Plan>> {
        Ok(self.plans.lock().unwrap().get(&plan_id).cloned())
    }

    async fn update_plan(&self, plan_id: Uuid, content: serde_json::Value) -> Result<()> {
        if let Some(plan) = self.plans.lock().unwrap().get_mut(&plan_id) {
            plan.content = content;
        }
        Ok(())
    }

    async fn list_plans(&self, home_id: &str) -> Result<Vec<Plan>> {
        Ok(self
            .plans
            .lock()
            .unwrap()
            .values()
            .filter(|p| p.home_id == home_id)
            .cloned()
            .collect())
    }
}

/// In-memory telemetry repository
#[derive(Clone)]
pub struct MemoryTelemetryRepo {
    metrics: Arc<Mutex<Vec<TurnMetrics>>>,
}

impl MemoryTelemetryRepo {
    pub fn new() -> Self {
        Self {
            metrics: Arc::new(Mutex::new(Vec::new())),
        }
    }
}

impl Default for MemoryTelemetryRepo {
    fn default() -> Self {
        Self::new()
    }
}

#[async_trait]
impl TelemetryRepo for MemoryTelemetryRepo {
    async fn record_turn_metrics(&self, metrics: TurnMetrics) -> Result<()> {
        self.metrics.lock().unwrap().push(metrics);
        Ok(())
    }

    async fn get_session_metrics(&self, session_id: &str) -> Result<Vec<TurnMetrics>> {
        Ok(self
            .metrics
            .lock()
            .unwrap()
            .iter()
            .filter(|m| m.session_id == session_id)
            .cloned()
            .collect())
    }
}

