//! Postgres repository implementations

use crate::traits::*;
use anyhow::{Context, Result};
use async_trait::async_trait;
use sqlx::{PgPool, Row};
use uuid::Uuid;

/// Postgres session repository
/// Note: Sessions are better stored in Redis for fast access
/// This is a placeholder that redirects to memory implementation
#[derive(Clone)]
pub struct PostgresSessionRepo {
    _pool: PgPool,
}

impl PostgresSessionRepo {
    pub fn new(pool: PgPool) -> Self {
        Self { _pool: pool }
    }
}

#[async_trait]
impl SessionRepo for PostgresSessionRepo {
    async fn create_session(&self, _user_id: &str, _home_id: &str) -> Result<Session> {
        // Sessions should use Redis, not Postgres
        anyhow::bail!("Use Redis for session storage")
    }

    async fn get_session(&self, _session_id: &str) -> Result<Option<Session>> {
        anyhow::bail!("Use Redis for session storage")
    }

    async fn update_session_state(&self, _session_id: &str, _state: SessionState) -> Result<()> {
        anyhow::bail!("Use Redis for session storage")
    }

    async fn has_active_connection(&self, _session_id: &str) -> Result<bool> {
        anyhow::bail!("Use Redis for session storage")
    }

    async fn mark_connected(&self, _session_id: &str) -> Result<()> {
        anyhow::bail!("Use Redis for session storage")
    }

    async fn mark_disconnected(&self, _session_id: &str) -> Result<()> {
        anyhow::bail!("Use Redis for session storage")
    }

    async fn create_turn(&self, _session_id: &str) -> Result<Turn> {
        anyhow::bail!("Use Redis for session storage")
    }

    async fn get_turn(&self, _turn_id: &str) -> Result<Option<Turn>> {
        anyhow::bail!("Use Redis for session storage")
    }

    async fn update_turn_state(&self, _turn_id: &str, _state: SessionState) -> Result<()> {
        anyhow::bail!("Use Redis for session storage")
    }

    async fn check_rate_limit(
        &self,
        _session_id: &str,
        _limit: u32,
        _window_secs: u64,
    ) -> Result<u32> {
        anyhow::bail!("Use Redis for session storage")
    }

    async fn increment_rate_limit(&self, _session_id: &str, _window_secs: u64) -> Result<()> {
        anyhow::bail!("Use Redis for session storage")
    }
}

/// Postgres plan repository
#[derive(Clone)]
pub struct PostgresPlanRepo {
    pool: PgPool,
}

impl PostgresPlanRepo {
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }
}

#[async_trait]
impl PlanRepo for PostgresPlanRepo {
    async fn create_plan(
        &self,
        home_id: &str,
        title: &str,
        content: serde_json::Value,
    ) -> Result<Plan> {
        let plan_id = Uuid::new_v4();
        let now = chrono::Utc::now();

        sqlx::query(
            r#"
            INSERT INTO plans (plan_id, home_id, title, content, created_at, updated_at)
            VALUES ($1, $2, $3, $4, $5, $6)
            "#,
        )
        .bind(plan_id)
        .bind(home_id)
        .bind(title)
        .bind(&content)
        .bind(now)
        .bind(now)
        .execute(&self.pool)
        .await
        .context("Failed to insert plan")?;

        Ok(Plan {
            plan_id,
            home_id: home_id.to_string(),
            title: title.to_string(),
            content,
            created_at: now,
        })
    }

    async fn get_plan(&self, plan_id: Uuid) -> Result<Option<Plan>> {
        let row = sqlx::query(
            r#"
            SELECT plan_id, home_id, title, content, created_at
            FROM plans
            WHERE plan_id = $1
            "#,
        )
        .bind(plan_id)
        .fetch_optional(&self.pool)
        .await
        .context("Failed to fetch plan")?;

        match row {
            Some(row) => Ok(Some(Plan {
                plan_id: row.get("plan_id"),
                home_id: row.get("home_id"),
                title: row.get("title"),
                content: row.get("content"),
                created_at: row.get("created_at"),
            })),
            None => Ok(None),
        }
    }

    async fn update_plan(&self, plan_id: Uuid, content: serde_json::Value) -> Result<()> {
        let now = chrono::Utc::now();

        sqlx::query(
            r#"
            UPDATE plans
            SET content = $1, updated_at = $2
            WHERE plan_id = $3
            "#,
        )
        .bind(&content)
        .bind(now)
        .bind(plan_id)
        .execute(&self.pool)
        .await
        .context("Failed to update plan")?;

        Ok(())
    }

    async fn list_plans(&self, home_id: &str) -> Result<Vec<Plan>> {
        let rows = sqlx::query(
            r#"
            SELECT plan_id, home_id, title, content, created_at
            FROM plans
            WHERE home_id = $1
            ORDER BY created_at DESC
            LIMIT 100
            "#,
        )
        .bind(home_id)
        .fetch_all(&self.pool)
        .await
        .context("Failed to list plans")?;

        let plans = rows
            .into_iter()
            .map(|row| Plan {
                plan_id: row.get("plan_id"),
                home_id: row.get("home_id"),
                title: row.get("title"),
                content: row.get("content"),
                created_at: row.get("created_at"),
            })
            .collect();

        Ok(plans)
    }
}

/// Postgres telemetry repository
#[derive(Clone)]
pub struct PostgresTelemetryRepo {
    pool: PgPool,
}

impl PostgresTelemetryRepo {
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }
}

#[async_trait]
impl TelemetryRepo for PostgresTelemetryRepo {
    async fn record_turn_metrics(&self, metrics: TurnMetrics) -> Result<()> {
        let guardrail_hits_json = serde_json::to_value(&metrics.guardrail_hits)?;

        sqlx::query(
            r#"
            INSERT INTO turn_metrics (
                turn_id, session_id, tokens_in, tokens_out,
                audio_in_seconds, audio_out_seconds, latency_ms, ttft_ms,
                policy_version, prompt_version, guardrail_hits, recorded_at
            )
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12)
            "#,
        )
        .bind(&metrics.turn_id)
        .bind(&metrics.session_id)
        .bind(metrics.tokens_in as i32)
        .bind(metrics.tokens_out as i32)
        .bind(metrics.audio_in_seconds)
        .bind(metrics.audio_out_seconds)
        .bind(metrics.latency_ms as i64)
        .bind(metrics.ttft_ms.map(|t| t as i64))
        .bind(&metrics.policy_version)
        .bind(&metrics.prompt_version)
        .bind(&guardrail_hits_json)
        .bind(metrics.recorded_at)
        .execute(&self.pool)
        .await
        .context("Failed to record turn metrics")?;

        Ok(())
    }

    async fn get_session_metrics(&self, session_id: &str) -> Result<Vec<TurnMetrics>> {
        let rows = sqlx::query(
            r#"
            SELECT
                turn_id, session_id, tokens_in, tokens_out,
                audio_in_seconds, audio_out_seconds, latency_ms, ttft_ms,
                policy_version, prompt_version, guardrail_hits, recorded_at
            FROM turn_metrics
            WHERE session_id = $1
            ORDER BY recorded_at DESC
            LIMIT 100
            "#,
        )
        .bind(session_id)
        .fetch_all(&self.pool)
        .await
        .context("Failed to fetch session metrics")?;

        let metrics = rows
            .into_iter()
            .map(|row| {
                let guardrail_hits: Vec<String> =
                    serde_json::from_value(row.get::<serde_json::Value, _>("guardrail_hits"))
                        .unwrap_or_default();

                TurnMetrics {
                    turn_id: row.get("turn_id"),
                    session_id: row.get("session_id"),
                    tokens_in: row.get::<i32, _>("tokens_in") as u32,
                    tokens_out: row.get::<i32, _>("tokens_out") as u32,
                    audio_in_seconds: row.get("audio_in_seconds"),
                    audio_out_seconds: row.get("audio_out_seconds"),
                    latency_ms: row.get::<i64, _>("latency_ms") as u64,
                    ttft_ms: row.get::<Option<i64>, _>("ttft_ms").map(|t| t as u64),
                    policy_version: row.get("policy_version"),
                    prompt_version: row.get("prompt_version"),
                    guardrail_hits,
                    recorded_at: row.get("recorded_at"),
                }
            })
            .collect();

        Ok(metrics)
    }
}
