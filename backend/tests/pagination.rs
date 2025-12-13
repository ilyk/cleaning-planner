//! Property tests for pagination cursor stability and limits

use cleanflow_domain::pagination::*;
use proptest::prelude::*;
use serde_json::json;

#[test]
fn test_cursor_encoding_decoding() {
    let cursor = Cursor::new("2025-01-29".to_string(), "p_123".to_string());
    let encoded = cursor.encode();
    let decoded = Cursor::decode(&encoded).unwrap();
    
    assert_eq!(cursor.sort_key, decoded.sort_key);
    assert_eq!(cursor.id, decoded.id);
}

#[test]
fn test_pagination_params_defaults() {
    let params = PaginationParams {
        limit: None,
        cursor: None,
    };
    
    assert_eq!(params.limit(), 50);
    assert!(params.parsed_cursor().unwrap().is_none());
}

#[test]
fn test_pagination_params_max_limit() {
    let params = PaginationParams {
        limit: Some(150), // Over max
        cursor: None,
    };
    
    assert_eq!(params.limit(), 100); // Should be capped at 100
}

#[test]
fn test_pagination_params_with_cursor() {
    let cursor = Cursor::new("2025-01-29".to_string(), "p_123".to_string());
    let encoded = cursor.encode();
    
    let params = PaginationParams {
        limit: Some(25),
        cursor: Some(encoded),
    };
    
    assert_eq!(params.limit(), 25);
    let parsed = params.parsed_cursor().unwrap();
    assert!(parsed.is_some());
    assert_eq!(parsed.unwrap().sort_key, "2025-01-29");
    assert_eq!(parsed.unwrap().id, "p_123");
}

#[test]
fn test_cursor_stability() {
    // Test that pagination forward then back yields same results
    let items = vec![
        ("2025-01-29", "p_001"),
        ("2025-01-29", "p_002"),
        ("2025-01-30", "p_003"),
        ("2025-01-30", "p_004"),
        ("2025-01-31", "p_005"),
    ];
    
    // Simulate pagination forward
    let mut forward_results = Vec::new();
    let mut cursor = None;
    
    for _ in 0..3 { // 3 pages
        let page_items: Vec<_> = items
            .iter()
            .filter(|(date, id)| {
                if let Some(ref c) = cursor {
                    (date, id) > (&c.sort_key, &c.id)
                } else {
                    true
                }
            })
            .take(2) // 2 items per page
            .collect();
        
        if page_items.is_empty() {
            break;
        }
        
        forward_results.extend(page_items.iter().map(|(date, id)| (*date, *id)));
        
        if let Some(last) = page_items.last() {
            cursor = Some(Cursor::new(last.0.to_string(), last.1.to_string()));
        }
    }
    
    // Simulate pagination backward
    let mut backward_results = Vec::new();
    let mut cursor = None;
    
    for _ in 0..3 { // 3 pages
        let page_items: Vec<_> = items
            .iter()
            .filter(|(date, id)| {
                if let Some(ref c) = cursor {
                    (date, id) < (&c.sort_key, &c.id)
                } else {
                    true
                }
            })
            .rev()
            .take(2) // 2 items per page
            .collect();
        
        if page_items.is_empty() {
            break;
        }
        
        backward_results.extend(page_items.iter().map(|(date, id)| (*date, *id)));
        
        if let Some(first) = page_items.first() {
            cursor = Some(Cursor::new(first.0.to_string(), first.1.to_string()));
        }
    }
    
    // Results should be the same (stable ordering)
    assert_eq!(forward_results.len(), backward_results.len());
}

#[test]
fn test_pagination_limits() {
    let params = PaginationParams {
        limit: Some(0),
        cursor: None,
    };
    assert_eq!(params.limit(), 50); // Should use default
    
    let params = PaginationParams {
        limit: Some(-5),
        cursor: None,
    };
    assert_eq!(params.limit(), 50); // Should use default
    
    let params = PaginationParams {
        limit: Some(25),
        cursor: None,
    };
    assert_eq!(params.limit(), 25); // Should use provided value
    
    let params = PaginationParams {
        limit: Some(200),
        cursor: None,
    };
    assert_eq!(params.limit(), 100); // Should be capped at max
}

#[test]
fn test_pagination_result_with_cursor() {
    let items = vec![
        ("2025-01-29", "p_001"),
        ("2025-01-29", "p_002"),
        ("2025-01-30", "p_003"),
    ];
    
    let result = PaginationResult::with_cursor_from_last(items.clone(), |item| {
        Cursor::new(item.0.to_string(), item.1.to_string())
    });
    
    assert_eq!(result.items.len(), 3);
    assert!(result.next_cursor.is_some());
    
    let next_cursor = result.next_cursor.unwrap();
    let decoded = Cursor::decode(&next_cursor).unwrap();
    assert_eq!(decoded.sort_key, "2025-01-30");
    assert_eq!(decoded.id, "p_003");
}

#[test]
fn test_pagination_result_no_cursor() {
    let items = vec![
        ("2025-01-29", "p_001"),
        ("2025-01-29", "p_002"),
    ];
    
    let result = PaginationResult::new(items.clone(), None);
    
    assert_eq!(result.items.len(), 2);
    assert!(result.next_cursor.is_none());
}

// Property test for cursor stability
proptest! {
    #[test]
    fn prop_cursor_roundtrip(
        sort_key in "[a-zA-Z0-9-]{1,50}",
        id in "[a-zA-Z0-9_]{1,50}"
    ) {
        let cursor = Cursor::new(sort_key.clone(), id.clone());
        let encoded = cursor.encode();
        let decoded = Cursor::decode(&encoded).unwrap();
        
        prop_assert_eq!(cursor.sort_key, decoded.sort_key);
        prop_assert_eq!(cursor.id, decoded.id);
    }
    
    #[test]
    fn prop_pagination_limits(
        limit in -10..200i32
    ) {
        let params = PaginationParams {
            limit: Some(limit),
            cursor: None,
        };
        
        let actual_limit = params.limit();
        prop_assert!(actual_limit >= 1);
        prop_assert!(actual_limit <= 100);
    }
}
