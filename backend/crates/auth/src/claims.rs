//! JWT claims structure and validation

use serde::{Deserialize, Serialize};
use thiserror::Error;

/// JWT claims for Clara sessions
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct Claims {
    /// Subject (user ID)
    pub sub: String,

    /// Session ID
    pub sid: String,

    /// Home ID (family/household identifier)
    pub home_id: String,

    /// Expiration timestamp (Unix timestamp)
    pub exp: usize,
}

/// Claims validation errors
#[derive(Debug, Error)]
pub enum ClaimsError {
    #[error("Invalid token: {0}")]
    InvalidToken(String),

    #[error("Token expired")]
    TokenExpired,

    #[error("Missing authorization header")]
    MissingAuthHeader,

    #[error("Invalid authorization header format")]
    InvalidAuthHeader,

    #[error("Encoding failed: {0}")]
    EncodingFailed(String),
}

impl Claims {
    /// Check if the claims are expired
    pub fn is_expired(&self) -> bool {
        let now = chrono::Utc::now().timestamp() as usize;
        self.exp < now
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use chrono::Utc;

    #[test]
    fn test_claims_expiration() {
        let future_exp = (Utc::now().timestamp() + 3600) as usize;
        let claims = Claims {
            sub: "user".to_string(),
            sid: "session".to_string(),
            home_id: "home".to_string(),
            exp: future_exp,
        };
        assert!(!claims.is_expired());

        let past_exp = (Utc::now().timestamp() - 3600) as usize;
        let expired_claims = Claims {
            sub: "user".to_string(),
            sid: "session".to_string(),
            home_id: "home".to_string(),
            exp: past_exp,
        };
        assert!(expired_claims.is_expired());
    }
}

