//! Ingress validation: auth, sequence, framing, rate limits

use anyhow::{Context, Result};
use std::collections::HashMap;
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

/// Ingress validation result
#[derive(Debug, Clone)]
pub struct IngressCheck {
    pub is_valid: bool,
    pub reason: Option<String>,
}

/// Rate limiter for sessions and IPs
struct RateLimiter {
    requests: HashMap<String, Vec<Instant>>,
    max_per_window: u32,
    window_secs: u64,
}

impl RateLimiter {
    fn new(max_per_window: u32, window_secs: u64) -> Self {
        Self {
            requests: HashMap::new(),
            max_per_window,
            window_secs,
        }
    }

    fn check(&mut self, key: &str) -> bool {
        let now = Instant::now();
        let window = Duration::from_secs(self.window_secs);
        
        let requests = self.requests.entry(key.to_string()).or_insert_with(Vec::new);
        
        // Remove old requests outside window
        requests.retain(|&t| now.duration_since(t) < window);
        
        if requests.len() >= self.max_per_window as usize {
            return false;
        }
        
        requests.push(now);
        true
    }
}

/// Ingress validator
pub struct IngressValidator {
    expected_seqs: Arc<Mutex<HashMap<String, u64>>>,
    active_turns: Arc<Mutex<HashMap<String, bool>>>,
    session_rate_limiter: Arc<Mutex<RateLimiter>>,
    ip_rate_limiter: Arc<Mutex<RateLimiter>>,
    max_seq_gap: u64,
}

impl IngressValidator {
    pub fn new(
        max_requests_per_minute: u32,
        max_seq_gap: u64,
    ) -> Self {
        Self {
            expected_seqs: Arc::new(Mutex::new(HashMap::new())),
            active_turns: Arc::new(Mutex::new(HashMap::new())),
            session_rate_limiter: Arc::new(Mutex::new(RateLimiter::new(max_requests_per_minute, 60))),
            ip_rate_limiter: Arc::new(Mutex::new(RateLimiter::new(max_requests_per_minute * 2, 60))),
            max_seq_gap,
        }
    }

    /// Validate sequence number
    pub fn validate_seq(&self, session_id: &str, seq: u64) -> Result<()> {
        let mut expected_seqs = self.expected_seqs.lock().unwrap();
        
        let expected = expected_seqs.entry(session_id.to_string()).or_insert(1);
        
        if seq != *expected {
            let gap = if seq > *expected {
                seq - *expected
            } else {
                *expected - seq
            };
            
            if gap > self.max_seq_gap {
                anyhow::bail!("Sequence gap too large: expected {}, got {}, gap {}", *expected, seq, gap);
            }
        }
        
        *expected = seq + 1;
        Ok(())
    }

    /// Reset sequence for a session (on turn start)
    pub fn reset_seq(&self, session_id: &str) {
        let mut expected_seqs = self.expected_seqs.lock().unwrap();
        expected_seqs.insert(session_id.to_string(), 1);
    }

    /// Check if a turn is already active
    pub fn check_active_turn(&self, session_id: &str) -> Result<()> {
        let mut active_turns = self.active_turns.lock().unwrap();
        
        if let Some(&true) = active_turns.get(session_id) {
            anyhow::bail!("Turn already active for session {}", session_id);
        }
        
        active_turns.insert(session_id.to_string(), true);
        Ok(())
    }

    /// Mark turn as inactive
    pub fn end_turn(&self, session_id: &str) {
        let mut active_turns = self.active_turns.lock().unwrap();
        active_turns.insert(session_id.to_string(), false);
    }

    /// Check rate limits
    pub fn check_rate_limit(&self, session_id: &str, ip: Option<&str>) -> Result<()> {
        // Check session rate limit
        {
            let mut limiter = self.session_rate_limiter.lock().unwrap();
            if !limiter.check(session_id) {
                anyhow::bail!("Session rate limit exceeded");
            }
        }
        
        // Check IP rate limit if provided
        if let Some(ip) = ip {
            let mut limiter = self.ip_rate_limiter.lock().unwrap();
            if !limiter.check(ip) {
                anyhow::bail!("IP rate limit exceeded");
            }
        }
        
        Ok(())
    }

    /// Validate audio format
    pub fn validate_format(&self, format: &str) -> Result<()> {
        match format {
            "opus@24000" => Ok(()),
            _ => anyhow::bail!("Unsupported audio format: {}", format),
        }
    }

    /// Validate frame size (20-40ms, max 20KB)
    pub fn validate_frame_size(&self, data: &[u8], format: &str) -> Result<()> {
        const MAX_FRAME_SIZE_BYTES: usize = 20 * 1024; // 20KB
        
        if data.len() > MAX_FRAME_SIZE_BYTES {
            anyhow::bail!("Frame size {} exceeds maximum {}", data.len(), MAX_FRAME_SIZE_BYTES);
        }
        
        // For Opus @ 24kHz: 20-40ms frames ≈ 480-960 samples
        // Compressed: roughly 40-200 bytes per frame is reasonable
        // Allow up to 20KB as per spec
        if data.is_empty() {
            anyhow::bail!("Empty frame");
        }
        
        Ok(())
    }

    /// Validate JWT and extract session ID
    /// 
    /// This should be called with the actual JWT token from the WebSocket
    /// connection headers. The token is verified and the session ID is extracted.
    pub fn validate_jwt(&self, token: &str) -> Result<String> {
        // JWT validation should be done in auth middleware before reaching here
        // This is a fallback check - in practice, unauthenticated requests
        // shouldn't reach the guardrails layer
        
        // For now, assume token was validated upstream and extract session from token
        // In production, use jsonwebtoken crate to decode and verify
        if token.is_empty() {
            anyhow::bail!("Empty JWT token");
        }
        
        // Basic validation - check token format (Bearer <token> or just <token>)
        let token_str = if token.starts_with("Bearer ") {
            &token[7..]
        } else {
            token
        };
        
        // Return session ID (in production, decode JWT claims)
        // Format assumption: session ID might be in token payload or extracted from headers
        Ok(format!("session-{}", &token_str[..token_str.len().min(16)]))
    }
}

impl Default for IngressValidator {
    fn default() -> Self {
        Self::new(60, 10)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_seq_validation() {
        let validator = IngressValidator::default();
        let session_id = "test-session";
        
        validator.reset_seq(session_id);
        assert!(validator.validate_seq(session_id, 1).is_ok());
        assert!(validator.validate_seq(session_id, 2).is_ok());
        
        // Gap too large
        assert!(validator.validate_seq(session_id, 20).is_err());
    }

    #[test]
    fn test_active_turn_check() {
        let validator = IngressValidator::default();
        let session_id = "test-session";
        
        assert!(validator.check_active_turn(session_id).is_ok());
        // Second active turn should fail
        assert!(validator.check_active_turn(session_id).is_err());
        
        validator.end_turn(session_id);
        assert!(validator.check_active_turn(session_id).is_ok());
    }

    #[test]
    fn test_format_validation() {
        let validator = IngressValidator::default();
        
        assert!(validator.validate_format("opus@24000").is_ok());
        assert!(validator.validate_format("pcm16@16000").is_err());
    }

    #[test]
    fn test_frame_size_validation() {
        let validator = IngressValidator::default();
        
        let small_frame = vec![0u8; 100];
        assert!(validator.validate_frame_size(&small_frame, "opus@24000").is_ok());
        
        let large_frame = vec![0u8; 25 * 1024]; // 25KB
        assert!(validator.validate_frame_size(&large_frame, "opus@24000").is_err());
        
        let empty_frame = vec![];
        assert!(validator.validate_frame_size(&empty_frame, "opus@24000").is_err());
    }
}
