//! Output filtering: PII redaction, token rate limiting

use regex::Regex;
use std::time::{Duration, Instant};

/// Output filter for text and audio
pub struct OutputFilter {
    pii_patterns: Vec<Regex>,
    max_tokens_per_sec: f32,
    max_duration_secs: u64,
    token_rate_window: Vec<Instant>,
    start_time: Option<Instant>,
}

impl OutputFilter {
    pub fn new(
        max_tokens_per_sec: f32,
        max_duration_secs: u64,
    ) -> Self {
        Self {
            pii_patterns: Self::compile_pii_patterns(),
            max_tokens_per_sec,
            max_duration_secs,
            token_rate_window: Vec::new(),
            start_time: None,
        }
    }

    /// Compile PII detection patterns
    fn compile_pii_patterns() -> Vec<Regex> {
        vec![
            // Email pattern
            Regex::new(r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b").unwrap(),
            // Phone number (US format)
            Regex::new(r"\b\d{3}[-.]?\d{3}[-.]?\d{4}\b").unwrap(),
            // SSN (US format)
            Regex::new(r"\b\d{3}-?\d{2}-?\d{4}\b").unwrap(),
            // Long digit sequences (likely credit card, etc.)
            Regex::new(r"\b\d{13,}\b").unwrap(),
            // IP address
            Regex::new(r"\b(?:\d{1,3}\.){3}\d{1,3}\b").unwrap(),
        ]
    }

    /// Filter text for PII
    pub fn filter_text(&self, text: &str) -> String {
        let mut filtered = text.to_string();
        
        for pattern in &self.pii_patterns {
            filtered = pattern.replace_all(&filtered, "[REDACTED]").to_string();
        }
        
        filtered
    }

    /// Check if token rate limit would be exceeded
    pub fn check_token_rate(&mut self, num_tokens: usize) -> bool {
        let now = Instant::now();
        
        // Initialize start time on first call
        if self.start_time.is_none() {
            self.start_time = Some(now);
        }
        
        // Remove old entries outside 1-second window
        let one_sec_ago = now - Duration::from_secs(1);
        self.token_rate_window.retain(|&t| t > one_sec_ago);
        
        // Track token rate by counting API calls (each call represents a token batch)
        for _ in 0..num_tokens {
            self.token_rate_window.push(now);
        }
        
        // Check if we exceed rate limit
        let current_rate = self.token_rate_window.len() as f32;
        
        if current_rate > self.max_tokens_per_sec {
            tracing::warn!(
                rate = current_rate,
                limit = self.max_tokens_per_sec,
                "Token rate limit exceeded"
            );
            return false; // Exceeded
        }
        
        true // Within limit
    }

    /// Check if output duration limit exceeded
    pub fn check_duration(&self) -> bool {
        if let Some(start) = self.start_time {
            let elapsed = start.elapsed();
            if elapsed > Duration::from_secs(self.max_duration_secs) {
                return false; // Exceeded
            }
        }
        true // Within limit
    }

    /// Reset filters for new output
    pub fn reset(&mut self) {
        self.token_rate_window.clear();
        self.start_time = None;
    }

    /// Estimate tokens from text (simple heuristic)
    pub fn estimate_tokens(&self, text: &str) -> usize {
        // Rough estimate: ~4 chars per token for English
        (text.len() / 4).max(1)
    }
}

impl Default for OutputFilter {
    fn default() -> Self {
        Self::new(50.0, 90) // 50 tokens/sec, 90s max duration
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_pii_redaction_email() {
        let filter = OutputFilter::default();
        let text = "Contact me at user@example.com";
        let filtered = filter.filter_text(text);
        assert!(filtered.contains("[REDACTED]"));
        assert!(!filtered.contains("@"));
    }

    #[test]
    fn test_pii_redaction_phone() {
        let filter = OutputFilter::default();
        let text = "Call 555-123-4567";
        let filtered = filter.filter_text(text);
        assert!(filtered.contains("[REDACTED]"));
    }

    #[test]
    fn test_pii_redaction_long_digits() {
        let filter = OutputFilter::default();
        let text = "Card: 1234567890123456";
        let filtered = filter.filter_text(text);
        assert!(filtered.contains("[REDACTED]"));
    }

    #[test]
    fn test_token_rate_limiting() {
        let mut filter = OutputFilter::new(10.0, 90);
        
        // Add tokens within limit
        for _ in 0..5 {
            assert!(filter.check_token_rate(1));
        }
        
        // Try to exceed limit
        for _ in 0..10 {
            filter.check_token_rate(1);
        }
    }

    #[test]
    fn test_estimate_tokens() {
        let filter = OutputFilter::default();
        
        let text = "This is a test sentence with multiple words.";
        let tokens = filter.estimate_tokens(text);
        assert!(tokens > 0);
        assert!(tokens <= text.len()); // Should be reasonable
    }
}
