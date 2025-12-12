//! Real database-backed plan service implementation

use crate::models::*;
use anyhow::Result;
use async_trait::async_trait;
use chrono::NaiveDate;
use serde_json;
use sqlx::{PgPool, Row};
use std::collections::HashMap;

#[async_trait]
pub trait RealPlanService: Send + Sync {
	/// Generate or fetch a plan for a home/date/mode
	async fn generate_plan(&self, request: GeneratePlanRequest, prompt_version: Option<String>, policy_version: Option<String>) -> Result<GeneratePlanResponse>;
    
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
    
    /// Update a plan with new sections and tasks (used by optimizer)
    async fn update_plan(&self, plan: &Plan) -> Result<()>;
}

/// Database-backed plan service
pub struct DbPlanService {
    pool: PgPool,
}

impl DbPlanService {
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }
    
    async fn get_plan_tasks(&self, plan_id: &str) -> Result<Vec<PlanTask>> {
        let rows = sqlx::query!(
            "SELECT id, template_id, room_id, title, estimate_min, state::text as \"state!\", priority, section_id, assignee_member_id, metadata
             FROM plan_tasks
             WHERE plan_id = $1
             ORDER BY section_id, priority",
            plan_id
        )
        .fetch_all(&self.pool)
        .await?;
        
        let mut tasks = Vec::new();
        for row in rows {
            // Get member name for assignee
            let assignee = if let Some(member_id) = &row.assignee_member_id {
                let member = sqlx::query!(
                    "SELECT name FROM members WHERE id = $1",
                    member_id
                )
                .fetch_optional(&self.pool)
                .await?;
                
                member.map(|m| TaskAssignee {
                    member_id: member_id.clone(),
                    name: m.name,
                })
            } else {
                None
            };
            
            tasks.push(PlanTask {
                task_id: row.id,
                template_id: row.template_id,
                room_id: row.room_id,
                title: row.title,
                estimate_min: row.estimate_min,
                state: row.state.parse().unwrap_or(TaskState::Pending),
                priority: row.priority,
                section_id: row.section_id,
                assignee,
                metadata: row.metadata,
            });
        }
        
        Ok(tasks)
    }
}

#[async_trait]
impl RealPlanService for DbPlanService {
    async fn generate_plan(&self, request: GeneratePlanRequest, prompt_version: Option<String>, policy_version: Option<String>) -> Result<GeneratePlanResponse> {
        // Check if plan exists for (home_id, date, mode) - using runtime query to handle enum cast
        let existing_plan = sqlx::query(
            "SELECT id, version, sections, prompt_version, policy_version, cached
             FROM plans
             WHERE home_id = $1 AND date = $2 AND mode = $3::plan_mode"
        )
        .bind(&request.home_id)
        .bind(&request.date)
        .bind(request.mode.to_string())
        .fetch_optional(&self.pool)
        .await?;
        
        if let Some(ref plan_row) = existing_plan {
            // Return existing plan if cached
            let cached: bool = plan_row.get("cached");
            if cached {
                let sections_json: serde_json::Value = plan_row.get("sections");
                let sections: Vec<PlanSection> = serde_json::from_value(sections_json)?;
                let plan_id: String = plan_row.get("id");
                let tasks = self.get_plan_tasks(&plan_id).await?;

                return Ok(GeneratePlanResponse {
                    plan_id,
                    home_id: request.home_id,
                    date: request.date,
                    mode: request.mode,
                    sections,
                    tasks,
                    version: plan_row.get("version"),
                    prompt_version: plan_row.get("prompt_version"),
                    policy_version: plan_row.get("policy_version"),
                    cached: true,
                });
            }
        }

        // Generate new plan
        let plan_id = generate_plan_id();
        let version = existing_plan.as_ref().map(|p| p.get::<i32, _>("version") + 1).unwrap_or(1);
        
        // Mock plan generation (in real implementation, this would call LLM)
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
        
        // Store version strings for reuse
        let prompt_ver = prompt_version.unwrap_or_else(|| "2025-10-28.3".to_string());
        let policy_ver = policy_version.unwrap_or_else(|| "2025-10-27.1".to_string());

        // Upsert plan - using runtime query to handle enum cast
        sqlx::query(
            "INSERT INTO plans (id, home_id, date, mode, sections, version, prompt_version, policy_version, cached)
             VALUES ($1, $2, $3, $4::plan_mode, $5, $6, $7, $8, $9)
             ON CONFLICT (home_id, date, mode)
             DO UPDATE SET
                 sections = EXCLUDED.sections,
                 version = EXCLUDED.version,
                 prompt_version = EXCLUDED.prompt_version,
                 policy_version = EXCLUDED.policy_version,
                 cached = EXCLUDED.cached,
                 updated_at = NOW()"
        )
        .bind(&plan_id)
        .bind(&request.home_id)
        .bind(&request.date)
        .bind(request.mode.to_string())
        .bind(serde_json::to_value(&sections)?)
        .bind(version)
        .bind(&prompt_ver)
        .bind(&policy_ver)
        .bind(false)
        .execute(&self.pool)
        .await?;
        
        // Insert/update plan tasks - using runtime query to handle enum cast
        for task in &tasks {
            sqlx::query(
                "INSERT INTO plan_tasks (id, plan_id, template_id, room_id, title, estimate_min, state, priority, section_id, assignee_member_id, metadata)
                 VALUES ($1, $2, $3, $4, $5, $6, $7::task_state, $8, $9, $10, $11)
                 ON CONFLICT (id)
                 DO UPDATE SET
                     template_id = EXCLUDED.template_id,
                     room_id = EXCLUDED.room_id,
                     title = EXCLUDED.title,
                     estimate_min = EXCLUDED.estimate_min,
                     state = EXCLUDED.state,
                     priority = EXCLUDED.priority,
                     section_id = EXCLUDED.section_id,
                     assignee_member_id = EXCLUDED.assignee_member_id,
                     metadata = EXCLUDED.metadata"
            )
            .bind(&task.task_id)
            .bind(&plan_id)
            .bind(&task.template_id)
            .bind(&task.room_id)
            .bind(&task.title)
            .bind(task.estimate_min)
            .bind(task.state.to_string())
            .bind(task.priority)
            .bind(&task.section_id)
            .bind(task.assignee.as_ref().map(|a| &a.member_id))
            .bind(task.metadata.as_ref().unwrap_or(&serde_json::Value::Null))
            .execute(&self.pool)
            .await?;
        }
        
        Ok(GeneratePlanResponse {
            plan_id,
            home_id: request.home_id,
            date: request.date,
            mode: request.mode,
            sections,
            tasks,
            version,
            prompt_version: prompt_ver,
            policy_version: policy_ver,
            cached: false,
        })
    }

    async fn revise_plan(&self, request: RevisePlanRequest) -> Result<GeneratePlanResponse> {
        // TODO: Implement atomic revision
        // 1. Start transaction
        // 2. Get current plan
        // 3. Apply edits atomically:
        //    - Reorder: update plan_tasks.priority
        //    - Postpone: move task to different date (create new plan)
        //    - Assign: update plan_tasks.assignee_member_id
        // 4. Bump plans.version
        // 5. Update sections JSON
        // 6. Commit transaction
        
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
		let row = sqlx::query!(
			"SELECT id, home_id, date, mode::text as \"mode!\", sections, version, prompt_version, policy_version, cached, created_at, updated_at \
			 FROM plans WHERE id = $1",
			plan_id
		)
		.fetch_optional(&self.pool)
		.await?;
		
		let Some(plan_row) = row else {
			return Ok(None);
		};
		
		let sections: Vec<PlanSection> = serde_json::from_value(plan_row.sections)?;
		let tasks = self.get_plan_tasks(&plan_row.id).await?;
		
		let plan = Plan {
			id: plan_row.id,
			home_id: plan_row.home_id,
			date: plan_row.date,
			mode: plan_row
				.mode
				.parse()
				.unwrap_or(PlanMode::Focus),
			sections,
			tasks,
			version: plan_row.version,
			prompt_version: plan_row.prompt_version,
			policy_version: plan_row.policy_version,
			cached: plan_row.cached,
			created_at: plan_row.created_at,
			updated_at: plan_row.updated_at,
		};
		
		Ok(Some(plan))
    }
    
    async fn list_plans(
        &self,
        _home_id: &str,
        _date_from: Option<NaiveDate>,
        _pagination: PaginationParams,
    ) -> Result<PaginatedResponse<Plan>> {
        // TODO: Implement stable pagination
        // SELECT * FROM plans 
        // WHERE home_id = ? AND date >= ?
        // ORDER BY date ASC, id ASC
        // LIMIT ? OFFSET ?
        
        Ok(PaginatedResponse {
            items: vec![],
            next_cursor: None,
        })
    }
    
    async fn assign_family(&self, request: FamilyAssignRequest) -> Result<Vec<Assignment>> {
        // TODO: Validate member belongs to home
        // TODO: Insert/update assignments table
        // TODO: Update plan_tasks.assignee_member_id
        
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
        // TODO: Insert into telemetry_events table
        // TODO: Update plan_tasks.state if needed
        
        Ok(TelemetryCompleteResponse {
            ok: true,
            telemetry_id: generate_telemetry_id(),
        })
    }
    
    async fn update_plan(&self, plan: &Plan) -> Result<()> {
        // Update plan metadata
        sqlx::query!(
            "UPDATE plans SET sections = $1, version = $2, updated_at = NOW() WHERE id = $3",
            serde_json::to_value(&plan.sections)?,
            plan.version,
            plan.id
        )
        .execute(&self.pool)
        .await?;
        
        // Update/insert plan tasks - using runtime query to handle enum cast
        for task in &plan.tasks {
            sqlx::query(
                "INSERT INTO plan_tasks (id, plan_id, template_id, room_id, title, estimate_min, state, priority, section_id, assignee_member_id, metadata)
                 VALUES ($1, $2, $3, $4, $5, $6, $7::task_state, $8, $9, $10, $11)
                 ON CONFLICT (id)
                 DO UPDATE SET
                     template_id = EXCLUDED.template_id,
                     room_id = EXCLUDED.room_id,
                     title = EXCLUDED.title,
                     estimate_min = EXCLUDED.estimate_min,
                     state = EXCLUDED.state,
                     priority = EXCLUDED.priority,
                     section_id = EXCLUDED.section_id,
                     assignee_member_id = EXCLUDED.assignee_member_id,
                     metadata = EXCLUDED.metadata"
            )
            .bind(&task.task_id)
            .bind(&plan.id)
            .bind(&task.template_id)
            .bind(&task.room_id)
            .bind(&task.title)
            .bind(task.estimate_min)
            .bind(task.state.to_string())
            .bind(task.priority)
            .bind(&task.section_id)
            .bind(task.assignee.as_ref().map(|a| &a.member_id))
            .bind(task.metadata.as_ref().unwrap_or(&serde_json::Value::Null))
            .execute(&self.pool)
            .await?;
        }
        
        Ok(())
    }
}
