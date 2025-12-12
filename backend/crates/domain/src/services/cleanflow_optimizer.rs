//! CleanFlow plan optimization service

use crate::models::*;
use crate::services::RealPlanService;
use anyhow::{Context, Result};
use async_trait::async_trait;
use reqwest::Client;
use serde_json::json;
use sqlx::PgPool;
use std::sync::Arc;
use tracing::{info, warn};

/// Optimization mode (e.g. local tweak vs full reset)
#[derive(Debug, Clone, Copy)]
pub enum OptimizationMode {
    Gentle,
    Aggressive,
}

#[async_trait]
pub trait CleanFlowOptimizer: Send + Sync {
    /// Optimize an existing plan for a home and return an updated view.
    async fn optimize_plan(
        &self,
        home_id: &str,
        plan_id: &str,
        mode: OptimizationMode,
        reasons: Option<String>,
    ) -> Result<GeneratePlanResponse>;
}

/// Database-backed optimizer that operates on existing plans using the real plan service.
/// Can optionally use LLM for intelligent adjustments when API key is provided.
pub struct DbCleanFlowOptimizer {
    real_plan_service: Arc<dyn RealPlanService>,
    db_pool: Option<PgPool>,
    openai_client: Option<Client>,
    openai_api_key: Option<String>,
    model: String,
}

impl DbCleanFlowOptimizer {
    pub fn new(real_plan_service: Arc<dyn RealPlanService>) -> Self {
        Self {
            real_plan_service,
            db_pool: None,
            openai_client: None,
            openai_api_key: None,
            model: "gpt-4o".to_string(),
        }
    }

    pub fn with_llm(
        mut self,
        db_pool: PgPool,
        openai_api_key: Option<String>,
        model: String,
    ) -> Self {
        self.db_pool = Some(db_pool);
        self.openai_api_key = openai_api_key.clone();
        self.model = model;
        if openai_api_key.is_some() {
            self.openai_client = Some(Client::new());
        }
        self
    }

    async fn get_llm_optimization_insights(
        &self,
        home_id: &str,
        plan: &Plan,
        mode: OptimizationMode,
        reasons: Option<&String>,
    ) -> Result<Option<String>> {
        if self.openai_client.is_none() || self.openai_api_key.is_none() {
            return Ok(None);
        }

        let client = self.openai_client.as_ref().unwrap();
        let api_key = self.openai_api_key.as_ref().unwrap();

        // Fetch recent history for context
        let history_context = if let Some(ref pool) = self.db_pool {
            self.fetch_history_for_optimization(home_id, pool).await?
        } else {
            "No history data available".to_string()
        };

        let prompt = format!(
            r#"You are an AI assistant that optimizes cleaning plans based on user behavior patterns.

Current Plan:
- {} tasks across {} sections
- Mode: {:?}
- Task durations: {} to {} minutes
- User request: {}

History Context:
{}

Based on this information, provide 2-3 specific optimization recommendations in JSON format:
{{
  "recommendations": [
    {{
      "action": "reorder" | "adjust_duration" | "merge" | "split",
      "task_id": "optional",
      "reason": "brief explanation",
      "suggested_change": "specific change"
    }}
  ]
}}

Return only valid JSON, no other text."#,
            plan.tasks.len(),
            plan.sections.len(),
            mode,
            plan.tasks.iter().map(|t| t.estimate_min).min().unwrap_or(0),
            plan.tasks.iter().map(|t| t.estimate_min).max().unwrap_or(0),
            reasons.unwrap_or(&"General optimization".to_string()),
            history_context
        );

        let is_gpt5 = self.model.starts_with("gpt-5") || self.model.starts_with("gpt5");
        
        let request_body = if is_gpt5 {
            // GPT-5/5.1 API format with JSON response format
            json!({
                "model": self.model,
                "messages": [
                    {
                        "role": "system",
                        "content": "You are an expert at optimizing cleaning task plans based on user behavior patterns. Provide concise, actionable recommendations in JSON format."
                    },
                    {
                        "role": "user",
                        "content": prompt
                    }
                ],
                "temperature": 0.7,
                "max_tokens": 500,
                "response_format": { "type": "json_object" },
                "top_p": 0.95
            })
        } else {
            // Standard GPT-4 API format
            json!({
                "model": self.model,
                "messages": [
                    {
                        "role": "system",
                        "content": "You are an expert at optimizing cleaning task plans based on user behavior patterns. Provide concise, actionable recommendations."
                    },
                    {
                        "role": "user",
                        "content": prompt
                    }
                ],
                "temperature": 0.7,
                "max_tokens": 500
            })
        };

        let response = client
            .post("https://api.openai.com/v1/chat/completions")
            .header("Authorization", format!("Bearer {}", api_key))
            .header("Content-Type", "application/json")
            .json(&request_body)
            .send()
            .await
            .context("Failed to call OpenAI API")?;

        if !response.status().is_success() {
            warn!("OpenAI API error during optimization, falling back to deterministic");
            return Ok(None);
        }

        let result: serde_json::Value = response
            .json()
            .await
            .context("Failed to parse OpenAI response")?;

        let content = result
            .get("choices")
            .and_then(|c| c.get(0))
            .and_then(|c| c.get("message"))
            .and_then(|m| m.get("content"))
            .and_then(|c| c.as_str())
            .ok_or_else(|| anyhow::anyhow!("Invalid OpenAI response format"))?;

        Ok(Some(content.to_string()))
    }

    async fn fetch_history_for_optimization(&self, home_id: &str, pool: &PgPool) -> Result<String> {
        let events = sqlx::query!(
            r#"
            SELECT
                task_id,
                kind::text as "kind!",
                duration_sec,
                te.created_at
            FROM telemetry_events te
            JOIN plans p ON te.task_id = ANY(
                SELECT id FROM plan_tasks WHERE plan_id = p.id
            )
            WHERE p.home_id = $1
            ORDER BY te.created_at DESC
            LIMIT 30
            "#,
            home_id
        )
        .fetch_all(pool)
        .await?;

        if events.is_empty() {
            return Ok("No history data available".to_string());
        }

        let completed = events.iter().filter(|e| e.kind == "done").count();
        let total_duration: i32 = events
            .iter()
            .filter_map(|e| e.duration_sec)
            .sum();
        let duration_count = events.iter().filter(|e| e.duration_sec.is_some()).count();
        let avg_duration = if duration_count > 0 {
            total_duration / duration_count as i32
        } else {
            0
        };

        Ok(format!(
            "Recent {} completed tasks with average duration {} seconds",
            completed, avg_duration
        ))
    }
}

#[async_trait]
impl CleanFlowOptimizer for DbCleanFlowOptimizer {
    async fn optimize_plan(
        &self,
        home_id: &str,
        plan_id: &str,
        mode: OptimizationMode,
        reasons: Option<String>,
    ) -> Result<GeneratePlanResponse> {
        // Fetch the existing plan from the real plan service
        let mut plan = self
            .real_plan_service
            .get_plan(plan_id)
            .await?
            .ok_or_else(|| anyhow::anyhow!("Plan not found"))?;

        // Verify home_id matches
        if plan.home_id != home_id {
            return Err(anyhow::anyhow!("Plan does not belong to home"));
        }

        // Try to get LLM insights if available
        let llm_insights = self
            .get_llm_optimization_insights(home_id, &plan, mode, reasons.as_ref())
            .await?;

        if let Some(insights) = &llm_insights {
            info!("Received LLM optimization insights, applying intelligent adjustments");
            // TODO: Parse and apply LLM recommendations
            // For now, we'll still do deterministic optimization but could enhance based on insights
        }

        // Apply deterministic optimization: reorder tasks by estimated duration
        // (shorter tasks first in each section for "gentle", longer first for "aggressive")
        // This can be enhanced with LLM insights in the future
        let mut tasks = plan.tasks.clone();
        tasks.sort_by(|a, b| {
            match mode {
                OptimizationMode::Gentle => a.estimate_min.cmp(&b.estimate_min),
                OptimizationMode::Aggressive => b.estimate_min.cmp(&a.estimate_min),
            }
        });

        // Rebuild sections with reordered task IDs
        let mut section_map: std::collections::HashMap<String, Vec<String>> = std::collections::HashMap::new();
        for task in &tasks {
            section_map
                .entry(task.section_id.clone())
                .or_insert_with(Vec::new)
                .push(task.task_id.clone());
        }

        let optimized_sections: Vec<PlanSection> = plan
            .sections
            .iter()
            .map(|section| PlanSection {
                id: section.id.clone(),
                title: section.title.clone(),
                tasks: section_map
                    .get(&section.id)
                    .cloned()
                    .unwrap_or_else(|| section.tasks.clone()),
            })
            .collect();

        // Update priorities to reflect new order within each section
        // Group tasks by section and assign priorities within each section
        let mut section_priorities: std::collections::HashMap<String, i32> = std::collections::HashMap::new();
        let mut updated_tasks = tasks;
        for task in &mut updated_tasks {
            let priority = section_priorities.entry(task.section_id.clone()).or_insert(0);
            task.priority = *priority;
            *priority += 1;
        }

        // Update plan structure
        plan.sections = optimized_sections;
        plan.tasks = updated_tasks;
        plan.version += 1;

        // Persist optimized plan to database
        self.real_plan_service.update_plan(&plan).await?;

        // Convert back to GeneratePlanResponse
        Ok(GeneratePlanResponse {
            plan_id: plan.id,
            home_id: plan.home_id,
            date: plan.date,
            mode: plan.mode,
            sections: plan.sections,
            tasks: plan.tasks,
            version: plan.version,
            prompt_version: plan.prompt_version,
            policy_version: plan.policy_version,
            cached: false,
        })
    }
}
