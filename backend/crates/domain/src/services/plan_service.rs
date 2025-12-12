//! Plan management service

use crate::models::*;
use anyhow::Result;
use async_trait::async_trait;
use chrono::NaiveDate;
use std::collections::HashMap;

#[async_trait]
pub trait PlanService: Send + Sync {
    /// Generate or fetch a plan for a home/date/mode
	async fn generate_plan(&self, request: GeneratePlanRequest) -> Result<GeneratePlanResponse>;
    
    /// Revise a plan with user edits
    async fn revise_plan(&self, request: RevisePlanRequest) -> Result<GeneratePlanResponse>;
    
    /// Get a plan by ID
    async fn get_plan(&self, plan_id: &str) -> Result<Option<Plan>>;
    
    /// List plans with pagination
    async fn list_plans(
        &self,
        home_id: &str,
        date_from: Option<NaiveDate>,
        pagination: PaginationParams,
    ) -> Result<PaginatedResponse<Plan>>;
    
    /// Assign tasks to family members
    async fn assign_family(&self, request: FamilyAssignRequest) -> Result<Vec<Assignment>>;
    
    /// Record task completion/skip
    async fn record_telemetry(&self, request: TelemetryCompleteRequest) -> Result<TelemetryCompleteResponse>;
}

/// Mock implementation for now
pub struct MockPlanService;

#[async_trait]
impl PlanService for MockPlanService {
    async fn generate_plan(&self, request: GeneratePlanRequest) -> Result<GeneratePlanResponse> {
        // Mock implementation - generate a simple plan
        let plan_id = generate_plan_id();
        
        let sections = vec![
            PlanSection {
                id: "s_now".to_string(),
                title: "Now".to_string(),
                tasks: vec!["t_8x1".to_string(), "t_8x2".to_string()],
            },
            PlanSection {
                id: "s_next".to_string(),
                title: "Next".to_string(),
                tasks: vec!["t_9p1".to_string()],
            },
        ];
        
        let tasks = vec![
            PlanTask {
                task_id: "t_8x1".to_string(),
                template_id: Some("tmpl_wipe_counters".to_string()),
                room_id: "r_kitchen".to_string(),
                title: "Wipe kitchen counters".to_string(),
                estimate_min: 7,
                state: TaskState::Pending,
                priority: 1,
                section_id: "s_now".to_string(),
                assignee: Some(TaskAssignee {
                    member_id: "m_dad".to_string(),
                    name: "Alex".to_string(),
                }),
                metadata: None,
            },
        ];
        
        Ok(GeneratePlanResponse {
            plan_id,
            home_id: request.home_id,
            date: request.date,
            mode: request.mode,
            sections,
            tasks,
            version: 1,
            prompt_version: "2025-10-28.3".to_string(),
            policy_version: "2025-10-27.1".to_string(),
            cached: false,
        })
    }
    
    async fn revise_plan(&self, request: RevisePlanRequest) -> Result<GeneratePlanResponse> {
        // Mock implementation - just return the same plan with incremented version
        let plan = self.get_plan(&request.plan_id).await?;
        let plan = plan.ok_or_else(|| anyhow::anyhow!("Plan not found"))?;
        
        Ok(GeneratePlanResponse {
            plan_id: plan.id,
            home_id: plan.home_id,
            date: plan.date,
            mode: plan.mode,
            sections: plan.sections,
            tasks: plan.tasks,
            version: plan.version + 1,
            prompt_version: plan.prompt_version,
            policy_version: plan.policy_version,
            cached: false,
        })
    }
    
    async fn get_plan(&self, plan_id: &str) -> Result<Option<Plan>> {
        // Mock implementation - return None for now
        Ok(None)
    }
    
    async fn list_plans(
        &self,
        _home_id: &str,
        _date_from: Option<NaiveDate>,
        _pagination: PaginationParams,
    ) -> Result<PaginatedResponse<Plan>> {
        // Mock implementation - return empty list
        Ok(PaginatedResponse {
            items: vec![],
            next_cursor: None,
        })
    }
    
    async fn assign_family(&self, request: FamilyAssignRequest) -> Result<Vec<Assignment>> {
        // Mock implementation - create assignments
        let assignments = request
            .assignments
            .into_iter()
            .map(|assignment| Assignment {
                id: generate_assignment_id(),
                plan_id: request.plan_id.clone(),
                task_id: assignment.task_id,
                member_id: assignment.member_id,
                created_at: chrono::Utc::now(),
            })
            .collect();
        
        Ok(assignments)
    }
    
    async fn record_telemetry(&self, request: TelemetryCompleteRequest) -> Result<TelemetryCompleteResponse> {
        // Mock implementation - just return success
        Ok(TelemetryCompleteResponse {
            ok: true,
            telemetry_id: generate_telemetry_id(),
        })
    }
}
