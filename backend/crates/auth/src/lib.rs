//! Authentication and authorization for Clara backend
//!
//! Provides JWT token validation, claims extraction, and middleware
//! for both REST and WebSocket endpoints.

pub mod claims;
pub mod middleware;

pub use claims::{Claims, ClaimsError};
pub use middleware::AuthExtension;

use cleanflow_protocol::ProtocolError;
use jsonwebtoken::{decode, encode, Algorithm, DecodingKey, EncodingKey, Header, Validation};
use std::sync::Arc;

/// JWT validator
#[derive(Clone)]
pub struct JwtValidator {
    secret: Arc<String>,
}

impl JwtValidator {
    /// Create a new JWT validator with the given secret
    pub fn new(secret: String) -> Self {
        Self {
            secret: Arc::new(secret),
        }
    }

    /// Validate a JWT token and extract claims
    pub fn validate(&self, token: &str) -> Result<Claims, ClaimsError> {
        let key = DecodingKey::from_secret(self.secret.as_bytes());
        let mut validation = Validation::new(Algorithm::HS256);
        validation.validate_exp = true;

        let token_data = decode::<Claims>(token, &key, &validation)
            .map_err(|e| ClaimsError::InvalidToken(e.to_string()))?;

        Ok(token_data.claims)
    }

    /// Generate a JWT token from claims
    pub fn generate(&self, claims: &Claims) -> Result<String, ClaimsError> {
        let key = EncodingKey::from_secret(self.secret.as_bytes());
        let header = Header::new(Algorithm::HS256);

        encode(&header, claims, &key).map_err(|e| ClaimsError::EncodingFailed(e.to_string()))
    }

    /// Extract bearer token from Authorization header
    pub fn extract_bearer_token(auth_header: &str) -> Option<&str> {
        auth_header.strip_prefix("Bearer ")
    }
}

/// Frame HMAC validator (optional feature for frame-level validation)
#[derive(Clone)]
pub struct FrameHmacValidator {
    _secret: Arc<Vec<u8>>,
}

impl FrameHmacValidator {
    /// Create a new HMAC validator
    pub fn new(secret: Vec<u8>) -> Self {
        Self {
            _secret: Arc::new(secret),
        }
    }

    /// Validate frame HMAC (stub for now)
    pub fn validate_frame(&self, _frame: &[u8], _hmac: &[u8]) -> Result<(), ProtocolError> {
        // TODO: Implement actual HMAC validation
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use chrono::Utc;

    #[test]
    fn test_jwt_round_trip() {
        let validator = JwtValidator::new("test-secret".to_string());

        let claims = Claims {
            sub: "user-123".to_string(),
            sid: "session-456".to_string(),
            home_id: "home-789".to_string(),
            exp: (Utc::now().timestamp() + 3600) as usize,
        };

        let token = validator.generate(&claims).unwrap();
        let decoded = validator.validate(&token).unwrap();

        assert_eq!(decoded.sub, claims.sub);
        assert_eq!(decoded.sid, claims.sid);
        assert_eq!(decoded.home_id, claims.home_id);
    }

    #[test]
    fn test_expired_token() {
        let validator = JwtValidator::new("test-secret".to_string());

        let claims = Claims {
            sub: "user-123".to_string(),
            sid: "session-456".to_string(),
            home_id: "home-789".to_string(),
            exp: (Utc::now().timestamp() - 3600) as usize, // Expired 1 hour ago
        };

        let token = validator.generate(&claims).unwrap();
        let result = validator.validate(&token);

        assert!(result.is_err());
    }

    #[test]
    fn test_extract_bearer_token() {
        let header = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test";
        let token = JwtValidator::extract_bearer_token(header).unwrap();
        assert_eq!(token, "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test");

        let invalid = "InvalidHeader";
        assert!(JwtValidator::extract_bearer_token(invalid).is_none());
    }
}

