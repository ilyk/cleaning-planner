//! Snapshot tests for error envelopes and canonical JSON shapes

use clara_domain::models::*;
use serde_json::json;

#[test]
fn test_error_envelope_snapshot() {
    let error = ApiError {
        error: ErrorDetails {
            code: "PLAN_NOT_FOUND".to_string(),
            message: "Plan p_123 not found".to_string(),
            details: Some(json!({"planId": "p_123"})),
            request_id: "req_9Yt123".to_string(),
        },
    };
    
    let json = serde_json::to_string_pretty(&error).unwrap();
    
    // This would be compared against a stored snapshot in a real implementation
    // For now, we just verify the structure is correct
    assert!(json.contains("\"error\""));
    assert!(json.contains("\"code\""));
    assert!(json.contains("\"message\""));
    assert!(json.contains("\"details\""));
    assert!(json.contains("\"requestId\""));
    assert!(json.contains("PLAN_NOT_FOUND"));
    assert!(json.contains("p_123"));
}

#[test]
fn test_error_codes_snapshot() {
    let error_codes = vec![
        ErrorCode::Unauthorized,
        ErrorCode::Forbidden,
        ErrorCode::RateLimited,
        ErrorCode::ValidationFailed,
        ErrorCode::Conflict,
        ErrorCode::NotFound,
        ErrorCode::Internal,
        ErrorCode::PlanNotFound,
        ErrorCode::SessionNotFound,
        ErrorCode::SessionAlreadyConnected,
        ErrorCode::InvalidRequest,
    ];
    
    let mut codes = Vec::new();
    for code in error_codes {
        codes.push(code.as_str());
    }
    
    // Verify all expected error codes are present
    assert!(codes.contains(&"UNAUTHORIZED"));
    assert!(codes.contains(&"FORBIDDEN"));
    assert!(codes.contains(&"RATE_LIMITED"));
    assert!(codes.contains(&"VALIDATION_FAILED"));
    assert!(codes.contains(&"CONFLICT"));
    assert!(codes.contains(&"NOT_FOUND"));
    assert!(codes.contains(&"INTERNAL"));
    assert!(codes.contains(&"PLAN_NOT_FOUND"));
    assert!(codes.contains(&"SESSION_NOT_FOUND"));
    assert!(codes.contains(&"SESSION_ALREADY_CONNECTED"));
    assert!(codes.contains(&"INVALID_REQUEST"));
}

#[test]
fn test_dto_deny_unknown_fields() {
    // Test that DTOs reject unknown fields
    let json_with_unknown = json!({
        "home_id": "h_test123",
        "date": "2025-01-29",
        "mode": "focus",
        "unknown_field": "should_be_rejected"
    });
    
    // This should fail with serde(deny_unknown_fields)
    let result: Result<GeneratePlanRequest, _> = serde_json::from_value(json_with_unknown);
    assert!(result.is_err());
    
    // Valid JSON should work
    let valid_json = json!({
        "home_id": "h_test123",
        "date": "2025-01-29",
        "mode": "focus"
    });
    
    let result: Result<GeneratePlanRequest, _> = serde_json::from_value(valid_json);
    assert!(result.is_ok());
}

#[test]
fn test_pagination_response_snapshot() {
    let response = PaginatedResponse {
        items: vec![
            Plan {
                id: "p_001".to_string(),
                home_id: "h_test123".to_string(),
                date: chrono::NaiveDate::from_ymd_opt(2025, 1, 29).unwrap(),
                mode: PlanMode::Focus,
                sections: vec![],
                tasks: vec![],
                version: 1,
                prompt_version: "2025-10-28.3".to_string(),
                policy_version: "2025-10-27.1".to_string(),
                cached: false,
                created_at: chrono::Utc::now(),
                updated_at: chrono::Utc::now(),
            },
        ],
        next_cursor: Some("eyJkYXRlIjoiMjAyNS0wMS0yOSIsImlkIjoicF8wMDEifQ==".to_string()),
    };
    
    let json = serde_json::to_string_pretty(&response).unwrap();
    
    // Verify structure
    assert!(json.contains("\"items\""));
    assert!(json.contains("\"next_cursor\""));
    assert!(json.contains("p_001"));
    assert!(json.contains("h_test123"));
    assert!(json.contains("focus"));
}

#[test]
fn test_plan_edit_snapshot() {
    let edits = vec![
        PlanEdit::Reorder {
            task_id: "t_001".to_string(),
            after_task_id: "t_002".to_string(),
        },
        PlanEdit::Postpone {
            task_id: "t_003".to_string(),
            to_date: chrono::NaiveDate::from_ymd_opt(2025, 1, 30).unwrap(),
        },
        PlanEdit::Assign {
            task_id: "t_004".to_string(),
            member_id: "m_001".to_string(),
        },
    ];
    
    let json = serde_json::to_string_pretty(&edits).unwrap();
    
    // Verify structure
    assert!(json.contains("\"op\""));
    assert!(json.contains("\"reorder\""));
    assert!(json.contains("\"postpone\""));
    assert!(json.contains("\"assign\""));
    assert!(json.contains("t_001"));
    assert!(json.contains("t_002"));
    assert!(json.contains("t_003"));
    assert!(json.contains("t_004"));
    assert!(json.contains("m_001"));
}

#[test]
fn test_telemetry_request_snapshot() {
    let request = TelemetryCompleteRequest {
        task_id: "t_001".to_string(),
        status: "done".to_string(),
        duration_sec: Some(300),
        comment: Some("Task completed successfully".to_string()),
        source: "qr".to_string(),
    };
    
    let json = serde_json::to_string_pretty(&request).unwrap();
    
    // Verify structure
    assert!(json.contains("\"task_id\""));
    assert!(json.contains("\"status\""));
    assert!(json.contains("\"duration_sec\""));
    assert!(json.contains("\"comment\""));
    assert!(json.contains("\"source\""));
    assert!(json.contains("t_001"));
    assert!(json.contains("done"));
    assert!(json.contains("300"));
    assert!(json.contains("qr"));
}

#[test]
fn test_printable_response_snapshot() {
    let response = PrintableResponse {
        export_id: "x_001".to_string(),
        pdf_url: "https://cdn.cleanflow.app/exports/x_001.pdf".to_string(),
        qr: vec![
            QrMapping {
                task_id: "t_001".to_string(),
                qr_id: "qr_001".to_string(),
            },
            QrMapping {
                task_id: "t_002".to_string(),
                qr_id: "qr_002".to_string(),
            },
        ],
    };
    
    let json = serde_json::to_string_pretty(&response).unwrap();
    
    // Verify structure
    assert!(json.contains("\"export_id\""));
    assert!(json.contains("\"pdf_url\""));
    assert!(json.contains("\"qr\""));
    assert!(json.contains("x_001"));
    assert!(json.contains("https://cdn.cleanflow.app/exports/x_001.pdf"));
    assert!(json.contains("t_001"));
    assert!(json.contains("qr_001"));
}

#[test]
fn test_cursor_encoding_snapshot() {
    let cursor = Cursor::new("2025-01-29".to_string(), "p_123".to_string());
    let encoded = cursor.encode();
    
    // Verify the encoded cursor is base64 and can be decoded
    let decoded = Cursor::decode(&encoded).unwrap();
    assert_eq!(decoded.sort_key, "2025-01-29");
    assert_eq!(decoded.id, "p_123");
    
    // Verify the encoded string looks like base64
    assert!(encoded.chars().all(|c| c.is_ascii_alphanumeric() || c == '+' || c == '/' || c == '='));
}
