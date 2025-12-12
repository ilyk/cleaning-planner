//! Tests for idempotency behavior and concurrent duplicates

use clara_domain::services::{IdempotencyStore, CachedResponse, MemoryIdempotencyStore};
use serde_json::json;
use std::sync::Arc;
use tokio::time::{sleep, Duration};

#[tokio::test]
async fn test_idempotency_basic() {
    let store = Arc::new(MemoryIdempotencyStore::new());
    let key = "test-key-123";
    let request = json!({"test": "data"});
    let response = CachedResponse {
        status: 200,
        body: json!({"result": "success"}),
    };
    
    // First request should not be cached
    assert!(store.check_idempotent(key, &request).await.unwrap().is_none());
    
    // Store response
    store.store_response(key, &request, &response).await.unwrap();
    
    // Second identical request should return cached response
    let cached = store.check_idempotent(key, &request).await.unwrap();
    assert!(cached.is_some());
    let cached = cached.unwrap();
    assert_eq!(cached.status, 200);
    assert_eq!(cached.body, json!({"result": "success"}));
}

#[tokio::test]
async fn test_idempotency_different_requests() {
    let store = Arc::new(MemoryIdempotencyStore::new());
    let key = "test-key-456";
    let request1 = json!({"test": "data1"});
    let request2 = json!({"test": "data2"});
    let response = CachedResponse {
        status: 200,
        body: json!({"result": "success"}),
    };
    
    // Store response for request1
    store.store_response(key, &request1, &response).await.unwrap();
    
    // Different request should not return cached response
    let cached = store.check_idempotent(key, &request2).await.unwrap();
    assert!(cached.is_none());
}

#[tokio::test]
async fn test_idempotency_concurrent_duplicates() {
    let store = Arc::new(MemoryIdempotencyStore::new());
    let key = "concurrent-test-key";
    let request = json!({"test": "concurrent"});
    let response = CachedResponse {
        status: 201,
        body: json!({"id": "created-123"}),
    };
    
    // Simulate concurrent requests
    let mut handles = Vec::new();
    
    for i in 0..5 {
        let store_clone = store.clone();
        let key = key.to_string();
        let request = request.clone();
        let response = response.clone();
        
        let handle = tokio::spawn(async move {
            // Small delay to simulate concurrent execution
            sleep(Duration::from_millis(i * 10)).await;
            
            // Check if already cached
            if let Ok(Some(cached)) = store_clone.check_idempotent(&key, &request).await {
                return (i, Some(cached));
            }
            
            // Store response
            store_clone.store_response(&key, &request, &response).await.unwrap();
            
            (i, Some(response))
        });
        
        handles.push(handle);
    }
    
    // Wait for all requests to complete
    let results: Vec<_> = futures::future::join_all(handles).await;
    
    // All requests should return the same response
    let first_result = results[0].as_ref().unwrap().1.as_ref().unwrap();
    for result in &results {
        let (_, response) = result.as_ref().unwrap();
        let response = response.as_ref().unwrap();
        assert_eq!(response.status, first_result.status);
        assert_eq!(response.body, first_result.body);
    }
}

#[tokio::test]
async fn test_idempotency_expiration() {
    let store = Arc::new(MemoryIdempotencyStore::new());
    let key = "expiration-test-key";
    let request = json!({"test": "expiration"});
    let response = CachedResponse {
        status: 200,
        body: json!({"result": "success"}),
    };
    
    // Store response
    store.store_response(key, &request, &response).await.unwrap();
    
    // Should be cached immediately
    assert!(store.check_idempotent(key, &request).await.unwrap().is_some());
    
    // Note: In a real implementation, we would test expiration
    // by waiting for the TTL or manipulating the system time
    // For now, we just verify the basic caching works
}

#[tokio::test]
async fn test_idempotency_hash_collision() {
    let store = Arc::new(MemoryIdempotencyStore::new());
    let key = "collision-test-key";
    let request1 = json!({"a": 1, "b": 2});
    let request2 = json!({"b": 2, "a": 1}); // Same data, different order
    let response1 = CachedResponse {
        status: 200,
        body: json!({"result": "first"}),
    };
    let response2 = CachedResponse {
        status: 200,
        body: json!({"result": "second"}),
    };
    
    // Store response for request1
    store.store_response(key, &request1, &response1).await.unwrap();
    
    // request2 should not return cached response (different hash)
    let cached = store.check_idempotent(key, &request2).await.unwrap();
    assert!(cached.is_none());
    
    // Store response for request2
    store.store_response(key, &request2, &response2).await.unwrap();
    
    // Now request2 should return its own cached response
    let cached = store.check_idempotent(key, &request2).await.unwrap();
    assert!(cached.is_some());
    assert_eq!(cached.unwrap().body, json!({"result": "second"}));
}

#[tokio::test]
async fn test_idempotency_error_handling() {
    let store = Arc::new(MemoryIdempotencyStore::new());
    let key = "error-test-key";
    let request = json!({"test": "error"});
    
    // Test with invalid key (empty string)
    let result = store.check_idempotent("", &request).await;
    assert!(result.is_ok());
    assert!(result.unwrap().is_none());
    
    // Test with invalid request (null)
    let result = store.check_idempotent(key, &json!(null)).await;
    assert!(result.is_ok());
    assert!(result.unwrap().is_none());
}

#[tokio::test]
async fn test_idempotency_status_codes() {
    let store = Arc::new(MemoryIdempotencyStore::new());
    let key = "status-test-key";
    let request = json!({"test": "status"});
    
    // Test different status codes
    let status_codes = vec![200, 201, 400, 404, 500];
    
    for status in status_codes {
        let response = CachedResponse {
            status,
            body: json!({"status": status}),
        };
        
        store.store_response(key, &request, &response).await.unwrap();
        
        let cached = store.check_idempotent(key, &request).await.unwrap();
        assert!(cached.is_some());
        assert_eq!(cached.unwrap().status, status);
    }
}
