//! Pagination utilities for cursor-based pagination

use base64::{engine::general_purpose, Engine as _};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;

/// Cursor for pagination
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Cursor {
    pub sort_key: String,
    pub id: String,
}

impl Cursor {
    /// Create a new cursor
    pub fn new(sort_key: String, id: String) -> Self {
        Self { sort_key, id }
    }
    
    /// Encode cursor to base64 string
    pub fn encode(&self) -> String {
        let json = serde_json::to_string(self).unwrap_or_default();
        general_purpose::STANDARD.encode(json)
    }
    
    /// Decode cursor from base64 string
    pub fn decode(encoded: &str) -> Result<Self, String> {
        let decoded = general_purpose::STANDARD
            .decode(encoded)
            .map_err(|e| format!("Failed to decode cursor: {}", e))?;
        
        let json = String::from_utf8(decoded)
            .map_err(|e| format!("Failed to convert cursor to string: {}", e))?;
        
        serde_json::from_str(&json)
            .map_err(|e| format!("Failed to parse cursor: {}", e))
    }
}

/// Pagination parameters
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PaginationParams {
    pub limit: Option<i32>,
    pub cursor: Option<String>,
}

impl PaginationParams {
    /// Get the limit with default
    pub fn limit(&self) -> i32 {
        self.limit.unwrap_or(50).min(100) // Cap at 100
    }
    
    /// Parse cursor if provided
    pub fn parsed_cursor(&self) -> Result<Option<Cursor>, String> {
        match &self.cursor {
            Some(cursor_str) => {
                let cursor = Cursor::decode(cursor_str)?;
                Ok(Some(cursor))
            }
            None => Ok(None),
        }
    }
}

/// Pagination result
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PaginationResult<T> {
    pub items: Vec<T>,
    pub next_cursor: Option<String>,
}

impl<T> PaginationResult<T> {
    /// Create a new pagination result
    pub fn new(items: Vec<T>, next_cursor: Option<String>) -> Self {
        Self { items, next_cursor }
    }
    
    /// Create a pagination result with cursor from last item
    pub fn with_cursor_from_last<F>(items: Vec<T>, get_cursor: F) -> Self
    where
        F: Fn(&T) -> Cursor,
    {
        let next_cursor = items.last().map(|item| get_cursor(item).encode());
        Self { items, next_cursor }
    }
}

/// Helper to build pagination query
pub fn build_pagination_query(
    base_query: &str,
    sort_column: &str,
    cursor: Option<&Cursor>,
    limit: i32,
) -> (String, Vec<serde_json::Value>) {
    let mut query = base_query.to_string();
    let mut params = Vec::new();
    
    if let Some(cursor) = cursor {
        // Add WHERE clause for cursor-based pagination
        query.push_str(&format!(" AND {} > $1", sort_column));
        params.push(serde_json::Value::String(cursor.sort_key.clone()));
    }
    
    // Add ORDER BY and LIMIT
    query.push_str(&format!(" ORDER BY {} ASC LIMIT ${}", sort_column, params.len() + 1));
    params.push(serde_json::Value::Number(serde_json::Number::from(limit + 1))); // +1 to check if there are more items
    
    (query, params)
}

/// Helper to process pagination results
pub fn process_pagination_results<T, F>(
    mut items: Vec<T>,
    limit: i32,
    get_cursor: F,
) -> PaginationResult<T>
where
    F: Fn(&T) -> Cursor,
{
    let has_more = items.len() > limit as usize;
    
    if has_more {
        items.truncate(limit as usize);
    }
    
    let next_cursor = if has_more {
        items.last().map(|item| get_cursor(item).encode())
    } else {
        None
    };
    
    PaginationResult::new(items, next_cursor)
}

#[cfg(test)]
mod tests {
    use super::*;
    
    #[test]
    fn test_cursor_encoding() {
        let cursor = Cursor::new("2025-01-29".to_string(), "p_123".to_string());
        let encoded = cursor.encode();
        let decoded = Cursor::decode(&encoded).unwrap();
        
        assert_eq!(cursor.sort_key, decoded.sort_key);
        assert_eq!(cursor.id, decoded.id);
    }
    
    #[test]
    fn test_pagination_params() {
        let params = PaginationParams {
            limit: Some(25),
            cursor: Some("eyJzb3J0X2tleSI6IjIwMjUtMDEtMjkiLCJpZCI6InBfMTIzIn0=".to_string()),
        };
        
        assert_eq!(params.limit(), 25);
        assert!(params.parsed_cursor().is_ok());
    }
}
