//! Session and turn lifecycle management

use anyhow::Result;
use cleanflow_protocol::ProtocolError;
use cleanflow_store::{SessionRepo, SessionState};
use std::sync::Arc;
use std::time::Duration;
use thiserror::Error;

/// Session manager
#[derive(Clone)]
pub struct SessionManager {
    repo: Arc<dyn SessionRepo>,
    rate_limit_turns_per_minute: u32,
    rate_limit_burst: u32,
    max_input_duration: Duration,
    max_output_duration: Duration,
}

impl SessionManager {
    pub fn new(
        repo: Arc<dyn SessionRepo>,
        rate_limit_turns_per_minute: u32,
        rate_limit_burst: u32,
        max_input_duration: Duration,
        max_output_duration: Duration,
    ) -> Self {
        Self {
            repo,
            rate_limit_turns_per_minute,
            rate_limit_burst,
            max_input_duration,
            max_output_duration,
        }
    }

    /// Create a new session
    pub async fn create_session(&self, user_id: &str, home_id: &str) -> Result<String> {
        let session = self.repo.create_session(user_id, home_id).await?;
        Ok(session.session_id)
    }

    /// Check if session exists and is valid
    pub async fn validate_session(&self, session_id: &str) -> Result<(), SessionError> {
        let session = self
            .repo
            .get_session(session_id)
            .await?
            .ok_or_else(|| SessionError::SessionNotFound(session_id.to_string()))?;

        // Check if session has active connection
        if self.repo.has_active_connection(session_id).await? {
            return Err(SessionError::SessionAlreadyConnected(
                session.session_id.clone(),
            ));
        }

        Ok(())
    }

    /// Start a new turn (idempotent)
    pub async fn start_turn(&self, session_id: &str) -> Result<String, SessionError> {
        // Validate session exists
        self.repo
            .get_session(session_id)
            .await?
            .ok_or_else(|| SessionError::SessionNotFound(session_id.to_string()))?;

        // Check rate limit
        let remaining = self
            .repo
            .check_rate_limit(session_id, self.rate_limit_turns_per_minute, 60)
            .await?;

        if remaining == 0 {
            return Err(SessionError::RateLimited(format!(
                "Maximum {} turns per minute exceeded",
                self.rate_limit_turns_per_minute
            )));
        }

        // Create turn
        let turn = self.repo.create_turn(session_id).await?;

        // Increment rate limit counter
        self.repo.increment_rate_limit(session_id, 60).await?;

        // Update session state
        self.repo
            .update_session_state(session_id, SessionState::Capturing)
            .await?;

        tracing::info!(
            session_id = session_id,
            turn_id = turn.turn_id,
            "Turn started"
        );

        Ok(turn.turn_id)
    }

    /// Mark session as connected
    pub async fn mark_connected(&self, session_id: &str) -> Result<()> {
        self.repo.mark_connected(session_id).await
    }

    /// Mark session as disconnected
    pub async fn mark_disconnected(&self, session_id: &str) -> Result<()> {
        self.repo.mark_disconnected(session_id).await
    }

    /// Update turn state
    pub async fn update_turn_state(&self, turn_id: &str, state: SessionState) -> Result<()> {
        self.repo.update_turn_state(turn_id, state).await
    }

    /// Update session state
    pub async fn update_session_state(&self, session_id: &str, state: SessionState) -> Result<()> {
        self.repo.update_session_state(session_id, state).await
    }

    /// Get max input duration
    pub fn max_input_duration(&self) -> Duration {
        self.max_input_duration
    }

    /// Get max output duration
    pub fn max_output_duration(&self) -> Duration {
        self.max_output_duration
    }
}

/// Session-related errors
#[derive(Debug, Error)]
pub enum SessionError {
    #[error("Session not found: {0}")]
    SessionNotFound(String),

    #[error("Session already connected: {0}")]
    SessionAlreadyConnected(String),

    #[error("Rate limited: {0}")]
    RateLimited(String),

    #[error("Invalid state transition: {0}")]
    InvalidStateTransition(String),

    #[error("Internal error: {0}")]
    Internal(#[from] anyhow::Error),
}

impl From<SessionError> for ProtocolError {
    fn from(err: SessionError) -> Self {
        match err {
            SessionError::SessionNotFound(msg) => ProtocolError::SessionNotFound(msg),
            SessionError::SessionAlreadyConnected(msg) => {
                ProtocolError::SessionAlreadyConnected(msg)
            }
            SessionError::RateLimited(msg) => ProtocolError::RateLimited(msg),
            SessionError::InvalidStateTransition(msg) => ProtocolError::InvalidRequest(msg),
            SessionError::Internal(err) => ProtocolError::InternalError(err.to_string()),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use cleanflow_store::Store;

    #[tokio::test]
    async fn test_create_session() {
        let store = Store::memory();
        let manager = SessionManager::new(
            store.session,
            3,
            6,
            Duration::from_secs(60),
            Duration::from_secs(90),
        );

        let session_id = manager.create_session("user-1", "home-1").await.unwrap();
        assert!(session_id.starts_with("sess-"));
    }

    #[tokio::test]
    async fn test_start_turn() {
        let store = Store::memory();
        let manager = SessionManager::new(
            store.session,
            3,
            6,
            Duration::from_secs(60),
            Duration::from_secs(90),
        );

        let session_id = manager.create_session("user-1", "home-1").await.unwrap();
        let turn_id = manager.start_turn(&session_id).await.unwrap();
        assert!(turn_id.starts_with("turn-"));
    }

    #[tokio::test]
    async fn test_rate_limit() {
        let store = Store::memory();
        let manager = SessionManager::new(
            store.session.clone(),
            3,
            6,
            Duration::from_secs(60),
            Duration::from_secs(90),
        );

        let session_id = manager.create_session("user-1", "home-1").await.unwrap();

        // Should succeed for first 3 turns
        for _ in 0..3 {
            manager.start_turn(&session_id).await.unwrap();
        }

        // 4th turn should fail
        let result = manager.start_turn(&session_id).await;
        assert!(matches!(result, Err(SessionError::RateLimited(_))));
    }
}

