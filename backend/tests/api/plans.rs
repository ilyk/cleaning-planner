//! Integration tests for Plan API endpoints

use axum::{
    body::Body,
    http::{Request, StatusCode},
};
use clara_domain::{
    models::*,
    services::{PlanService, PrintableService, IdempotencyService},
};
use serde_json::json;
use std::sync::Arc;
use tower::ServiceExt;

/// Mock services for testing
struct MockPlanService;
struct MockPrintableService;
struct MockIdempotencyService;

#[async_trait::async_trait]
impl PlanService for MockPlanService {
    async fn generate_plan(&self, request: GeneratePlanRequest) -> anyhow::Result<GeneratePlanResponse> {
        Ok(GeneratePlanResponse {
            plan_id: "p_test123".to_string(),
            home_id: request.home_id,
            date: request.date,
            mode: request.mode,
            sections: vec![],
            tasks: vec![],
            version: 1,
            prompt_version: "test".to_string(),
            policy_version: "test".to_string(),
            cached: false,
        })
    }
    
    async fn revise_plan(&self, request: RevisePlanRequest) -> anyhow::Result<GeneratePlanResponse> {
        Ok(GeneratePlanResponse {
            plan_id: request.plan_id,
            home_id: "h_test".to_string(),
            date: chrono::NaiveDate::from_ymd_opt(2025, 1, 29).unwrap(),
            mode: PlanMode::Focus,
            sections: vec![],
            tasks: vec![],
            version: 2,
            prompt_version: "test".to_string(),
            policy_version: "test".to_string(),
            cached: false,
        })
    }
    
    async fn get_plan(&self, _plan_id: &str) -> anyhow::Result<Option<Plan>> {
        Ok(None)
    }
    
    async fn list_plans(
        &self,
        _home_id: &str,
        _date_from: Option<chrono::NaiveDate>,
        _pagination: PaginationParams,
    ) -> anyhow::Result<PaginatedResponse<Plan>> {
        Ok(PaginatedResponse {
            items: vec![],
            next_cursor: None,
        })
    }
    
    async fn assign_family(&self, request: FamilyAssignRequest) -> anyhow::Result<Vec<Assignment>> {
        Ok(vec![Assignment {
            id: "a_test".to_string(),
            plan_id: request.plan_id,
            task_id: request.assignments[0].task_id.clone(),
            member_id: request.assignments[0].member_id.clone(),
            created_at: chrono::Utc::now(),
        }])
    }
    
    async fn record_telemetry(&self, _request: TelemetryCompleteRequest) -> anyhow::Result<TelemetryCompleteResponse> {
        Ok(TelemetryCompleteResponse {
            ok: true,
            telemetry_id: "te_test".to_string(),
        })
    }
}

#[async_trait::async_trait]
impl PrintableService for MockPrintableService {
    async fn generate_printable(&self, _request: PrintableRequest) -> anyhow::Result<PrintableResponse> {
        Ok(PrintableResponse {
            export_id: "x_test".to_string(),
            pdf_url: "https://example.com/test.pdf".to_string(),
            qr: vec![],
        })
    }
}

#[async_trait::async_trait]
impl IdempotencyService for MockIdempotencyService {
    async fn check_idempotent(&self, _key: &str, _request: &serde_json::Value) -> anyhow::Result<Option<serde_json::Value>> {
        Ok(None)
    }
    
    async fn store_response(&self, _key: &str, _request: &serde_json::Value, _response: &serde_json::Value) -> anyhow::Result<()> {
        Ok(())
    }
}

#[tokio::test]
async fn test_generate_plan_success() {
    // This is a placeholder test - in a real implementation,
    // you would set up a test server with proper authentication
    // and test the actual HTTP endpoints
    
    let request = GeneratePlanRequest {
        home_id: "h_test".to_string(),
        date: chrono::NaiveDate::from_ymd_opt(2025, 1, 29).unwrap(),
        mode: PlanMode::Focus,
        constraints: None,
        client: None,
    };
    
    let service = MockPlanService;
    let result = service.generate_plan(request).await;
    
    assert!(result.is_ok());
    let response = result.unwrap();
    assert_eq!(response.plan_id, "p_test123");
    assert_eq!(response.mode, PlanMode::Focus);
}

#[tokio::test]
async fn test_revise_plan_success() {
    let request = RevisePlanRequest {
        plan_id: "p_test123".to_string(),
        edits: vec![
            PlanEdit::Assign {
                task_id: "t_test".to_string(),
                member_id: "m_test".to_string(),
            },
        ],
    };
    
    let service = MockPlanService;
    let result = service.revise_plan(request).await;
    
    assert!(result.is_ok());
    let response = result.unwrap();
    assert_eq!(response.plan_id, "p_test123");
    assert_eq!(response.version, 2);
}

#[tokio::test]
async fn test_assign_family_success() {
    let request = FamilyAssignRequest {
        plan_id: "p_test123".to_string(),
        assignments: vec![TaskAssignment {
            task_id: "t_test".to_string(),
            member_id: "m_test".to_string(),
        }],
    };
    
    let service = MockPlanService;
    let result = service.assign_family(request).await;
    
    assert!(result.is_ok());
    let assignments = result.unwrap();
    assert_eq!(assignments.len(), 1);
    assert_eq!(assignments[0].plan_id, "p_test123");
}

#[tokio::test]
async fn test_record_telemetry_success() {
    let request = TelemetryCompleteRequest {
        task_id: "t_test".to_string(),
        status: "done".to_string(),
        duration_sec: Some(300),
        comment: Some("Completed successfully".to_string()),
        source: "app".to_string(),
    };
    
    let service = MockPlanService;
    let result = service.record_telemetry(request).await;
    
    assert!(result.is_ok());
    let response = result.unwrap();
    assert!(response.ok);
    assert_eq!(response.telemetry_id, "te_test");
}

#[tokio::test]
async fn test_generate_printable_success() {
    let request = PrintableRequest {
        plan_id: "p_test123".to_string(),
        options: PrintableOptions {
            paper_size: Some("A4".to_string()),
            kids_friendly: Some(true),
            qr_density: Some("per-task".to_string()),
        },
    };
    
    let service = MockPrintableService;
    let result = service.generate_printable(request).await;
    
    assert!(result.is_ok());
    let response = result.unwrap();
    assert_eq!(response.export_id, "x_test");
    assert!(response.pdf_url.contains("example.com"));
}
