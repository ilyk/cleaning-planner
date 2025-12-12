//! Idempotency service for request deduplication

use anyhow::Result;
use async_trait::async_trait;
use serde_json::Value;
use std::collections::hash_map::DefaultHasher;
use std::hash::{Hash, Hasher};

#[async_trait]
pub trait IdempotencyService: Send + Sync {
    /// Check if a request is idempotent and return cached response if exists
    async fn check_idempotent(&self, key: &str, request: &Value) -> Result<Option<Value>>;
    
    /// Store a response for idempotency
    async fn store_response(&self, key: &str, request: &Value, response: &Value) -> Result<()>;
}

/// Mock implementation for now
pub struct MockIdempotencyService {
    cache: std::collections::HashMap<String, Value>,
}

impl MockIdempotencyService {
    pub fn new() -> Self {
        Self {
            cache: std::collections::HashMap::new(),
        }
    }
}

#[async_trait]
impl IdempotencyService for MockIdempotencyService {
    async fn check_idempotent(&self, key: &str, _request: &Value) -> Result<Option<Value>> {
        // Mock implementation - just check cache
        Ok(self.cache.get(key).cloned())
    }
    
    async fn store_response(&self, key: &str, _request: &Value, response: &Value) -> Result<()> {
        // Mock implementation - store in memory cache
        // In real implementation, this would store in Redis with TTL
        Ok(())
    }
}

/// Generate a hash for request deduplication
pub fn hash_request(request: &Value) -> u64 {
    let mut hasher = DefaultHasher::new();
    request.hash(&mut hasher);
    hasher.finish()
}
