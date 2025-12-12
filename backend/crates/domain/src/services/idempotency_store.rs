//! Persistent idempotency store for request deduplication

use anyhow::Result;
use async_trait::async_trait;
use serde_json::Value;
use std::collections::hash_map::DefaultHasher;
use std::hash::{Hash, Hasher};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

#[async_trait]
pub trait IdempotencyStore: Send + Sync {
    /// Check if a request is idempotent and return cached response if exists
    async fn check_idempotent(&self, key: &str, request: &Value) -> Result<Option<CachedResponse>>;
    
    /// Store a response for idempotency
    async fn store_response(&self, key: &str, request: &Value, response: &CachedResponse) -> Result<()>;
}

/// Cached response with status and body
#[derive(Debug, Clone)]
pub struct CachedResponse {
    pub status: u16,
    pub body: Value,
}

/// Database-backed idempotency store
pub struct DbIdempotencyStore {
    pool: sqlx::PgPool,
}

impl DbIdempotencyStore {
    pub fn new(pool: sqlx::PgPool) -> Self {
        Self { pool }
    }
}

#[async_trait]
impl IdempotencyStore for DbIdempotencyStore {
    async fn check_idempotent(&self, key: &str, request: &Value) -> Result<Option<CachedResponse>> {
        let request_hash = hash_request(request);

        // Query idempotency_keys table
        // Note: status is stored inside the response JSONB as {"status": N, "body": {...}}
        let row = sqlx::query!(
            "SELECT response FROM idempotency_keys
             WHERE key = $1 AND request_hash = $2 AND expires_at > NOW()",
            key,
            request_hash.to_string()
        )
        .fetch_optional(&self.pool)
        .await?;

        if let Some(row) = row {
            // Response JSONB contains {"status": N, "body": {...}}
            let response_obj: Value = row.response;
            let status = response_obj.get("status")
                .and_then(|v| v.as_u64())
                .unwrap_or(200) as u16;
            let body = response_obj.get("body")
                .cloned()
                .unwrap_or(Value::Null);
            Ok(Some(CachedResponse { status, body }))
        } else {
            Ok(None)
        }
    }

    async fn store_response(&self, key: &str, request: &Value, response: &CachedResponse) -> Result<()> {
        let request_hash = hash_request(request);
        let expires_at = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_secs() + 24 * 60 * 60; // 24 hours from now

        // Store status and body together in response JSONB
        let response_obj = serde_json::json!({
            "status": response.status,
            "body": response.body
        });

        // Insert into idempotency_keys table
        sqlx::query!(
            "INSERT INTO idempotency_keys (key, request_hash, response, expires_at)
             VALUES ($1, $2, $3, to_timestamp($4))
             ON CONFLICT (key) DO UPDATE SET
                 request_hash = EXCLUDED.request_hash,
                 response = EXCLUDED.response,
                 expires_at = EXCLUDED.expires_at",
            key,
            request_hash.to_string(),
            response_obj,
            expires_at as i64
        )
        .execute(&self.pool)
        .await?;

        Ok(())
    }
}

/// Generate a hash for request deduplication
pub fn hash_request(request: &Value) -> u64 {
    let mut hasher = DefaultHasher::new();
    request.hash(&mut hasher);
    hasher.finish()
}

/// In-memory implementation for testing
pub struct MemoryIdempotencyStore {
    cache: std::collections::HashMap<String, (u64, CachedResponse, u64)>, // key -> (req_hash, response, expires_at)
}

impl MemoryIdempotencyStore {
    pub fn new() -> Self {
        Self {
            cache: std::collections::HashMap::new(),
        }
    }
}

#[async_trait]
impl IdempotencyStore for MemoryIdempotencyStore {
    async fn check_idempotent(&self, key: &str, request: &Value) -> Result<Option<CachedResponse>> {
        let request_hash = hash_request(request);
        let now = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_secs();
        
        if let Some((cached_hash, response, expires_at)) = self.cache.get(key) {
            if *cached_hash == request_hash && *expires_at > now {
                return Ok(Some(response.clone()));
            }
        }
        
        Ok(None)
    }
    
    async fn store_response(&self, key: &str, request: &Value, response: &CachedResponse) -> Result<()> {
        let request_hash = hash_request(request);
        let expires_at = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_secs() + 24 * 60 * 60; // 24 hours from now
        
        // Note: This is not thread-safe, but it's for testing only
        // In production, use proper synchronization or database
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[tokio::test]
    async fn test_idempotency_store() {
        let store = MemoryIdempotencyStore::new();
        let request = json!({"test": "data"});
        let response = CachedResponse {
            status: 200,
            body: json!({"result": "success"}),
        };
        
        // First request should not be cached
        assert!(store.check_idempotent("key1", &request).await.unwrap().is_none());
        
        // Store response
        store.store_response("key1", &request, &response).await.unwrap();
        
        // Second identical request should return cached response
        let cached = store.check_idempotent("key1", &request).await.unwrap();
        assert!(cached.is_some());
        assert_eq!(cached.unwrap().status, 200);
    }
}
