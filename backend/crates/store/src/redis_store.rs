//! Redis-backed session repository

use crate::traits::*;
use anyhow::{Context, Result};
use async_trait::async_trait;
use chrono::Utc;
use redis::{aio::ConnectionManager, AsyncCommands};
use uuid::Uuid;

/// Redis session repository
#[derive(Clone)]
pub struct RedisSessionRepo {
    pool: ConnectionManager,
}

impl RedisSessionRepo {
    pub fn new(pool: ConnectionManager) -> Self {
        Self { pool }
    }

    fn session_key(&self, session_id: &str) -> String {
        format!("session:{}", session_id)
    }

    fn turn_key(&self, turn_id: &str) -> String {
        format!("turn:{}", turn_id)
    }

    fn connection_key(&self, session_id: &str) -> String {
        format!("connection:{}", session_id)
    }

    fn rate_limit_key(&self, session_id: &str) -> String {
        format!("ratelimit:{}", session_id)
    }
}

#[async_trait]
impl SessionRepo for RedisSessionRepo {
    async fn create_session(&self, user_id: &str, home_id: &str) -> Result<Session> {
        let session = Session {
            session_id: format!("sess-{}", Uuid::new_v4()),
            user_id: user_id.to_string(),
            home_id: home_id.to_string(),
            state: SessionState::Idle,
            created_at: Utc::now(),
            updated_at: Utc::now(),
        };

        let json = serde_json::to_string(&session)?;
        let key = self.session_key(&session.session_id);

        let mut conn = self.pool.clone();
        conn.set_ex::<_, _, ()>(&key, json, 3600)
            .await
            .context("Failed to store session in Redis")?;

        Ok(session)
    }

    async fn get_session(&self, session_id: &str) -> Result<Option<Session>> {
        let key = self.session_key(session_id);
        let mut conn = self.pool.clone();

        let json: Option<String> = conn.get(&key).await?;

        match json {
            Some(data) => {
                let session: Session = serde_json::from_str(&data)?;
                Ok(Some(session))
            }
            None => Ok(None),
        }
    }

    async fn update_session_state(&self, session_id: &str, state: SessionState) -> Result<()> {
        if let Some(mut session) = self.get_session(session_id).await? {
            session.state = state;
            session.updated_at = Utc::now();

            let json = serde_json::to_string(&session)?;
            let key = self.session_key(session_id);

            let mut conn = self.pool.clone();
            conn.set_ex::<_, _, ()>(&key, json, 3600).await?;
        }

        Ok(())
    }

    async fn has_active_connection(&self, session_id: &str) -> Result<bool> {
        let key = self.connection_key(session_id);
        let mut conn = self.pool.clone();

        let exists: bool = conn.exists(&key).await?;
        Ok(exists)
    }

    async fn mark_connected(&self, session_id: &str) -> Result<()> {
        let key = self.connection_key(session_id);
        let mut conn = self.pool.clone();

        conn.set_ex::<_, _, ()>(&key, "1", 300).await?; // 5 min TTL

        Ok(())
    }

    async fn mark_disconnected(&self, session_id: &str) -> Result<()> {
        let key = self.connection_key(session_id);
        let mut conn = self.pool.clone();

        conn.del::<_, ()>(&key).await?;

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

        let json = serde_json::to_string(&turn)?;
        let key = self.turn_key(&turn.turn_id);

        let mut conn = self.pool.clone();
        conn.set_ex::<_, _, ()>(&key, json, 1800)
            .await
            .context("Failed to store turn in Redis")?; // 30 min TTL

        Ok(turn)
    }

    async fn get_turn(&self, turn_id: &str) -> Result<Option<Turn>> {
        let key = self.turn_key(turn_id);
        let mut conn = self.pool.clone();

        let json: Option<String> = conn.get(&key).await?;

        match json {
            Some(data) => {
                let turn: Turn = serde_json::from_str(&data)?;
                Ok(Some(turn))
            }
            None => Ok(None),
        }
    }

    async fn update_turn_state(&self, turn_id: &str, state: SessionState) -> Result<()> {
        if let Some(mut turn) = self.get_turn(turn_id).await? {
            turn.state = state;

            let json = serde_json::to_string(&turn)?;
            let key = self.turn_key(turn_id);

            let mut conn = self.pool.clone();
            conn.set_ex::<_, _, ()>(&key, json, 1800).await?;
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

        let key = self.rate_limit_key(session_id);
        let mut conn = self.pool.clone();

        // Remove old entries
        let _: () = conn
            .zrembyscore(&key, "-inf", window_start)
            .await?;

        // Count entries in window
        let count: u32 = conn.zcard(&key).await?;

        let remaining = limit.saturating_sub(count);

        Ok(remaining)
    }

    async fn increment_rate_limit(&self, session_id: &str, window_secs: u64) -> Result<()> {
        let now = Utc::now().timestamp();
        let key = self.rate_limit_key(session_id);

        let mut conn = self.pool.clone();

        // Add current timestamp
        let _: () = conn.zadd(&key, now, now).await?;

        // Set expiry on the key
        let _: () = conn.expire(&key, window_secs as i64).await?;

        Ok(())
    }
}

