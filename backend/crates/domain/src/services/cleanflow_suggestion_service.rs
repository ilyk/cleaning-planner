//! CleanFlow LLM-based suggestion service

use crate::models::*;
use crate::services::{RealPlanService, TelemetryService};
use anyhow::{Context, Result};
use async_trait::async_trait;
use reqwest::Client;
use serde::{Deserialize, Serialize};
use serde_json::json;
use sqlx::PgPool;
use std::sync::Arc;
use tracing::{info, warn};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Suggestion {
    pub id: String,
    pub text: String,
    pub confidence: i32,
    pub action: String,
    pub source: String,
    pub target: Option<SuggestionTarget>,
    pub state: SuggestionState,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SuggestionTarget {
    pub task_id: Option<String>,
    pub field: Option<String>,
    pub proposed_value: Option<serde_json::Value>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SuggestionState {
    pub accepted: bool,
    pub dismissed: bool,
    pub applied_locally: bool,
}

#[async_trait]
pub trait CleanFlowSuggestionService: Send + Sync {
    /// Generate suggestions for a home/plan based on history and patterns
    async fn generate_suggestions(
        &self,
        home_id: &str,
        plan_id: Option<&str>,
    ) -> Result<Vec<Suggestion>>;
}

/// LLM-backed suggestion service using OpenAI Chat API
pub struct LlmSuggestionService {
    real_plan_service: Arc<dyn RealPlanService>,
    db_pool: Option<PgPool>,
    openai_client: Client,
    openai_api_key: Option<String>,
    model: String,
}

impl LlmSuggestionService {
    pub fn new(
        real_plan_service: Arc<dyn RealPlanService>,
        openai_api_key: Option<String>,
        model: String,
    ) -> Self {
        Self {
            real_plan_service,
            db_pool: None,
            openai_client: Client::new(),
            openai_api_key,
            model,
        }
    }

    pub fn with_db_pool(mut self, db_pool: PgPool) -> Self {
        self.db_pool = Some(db_pool);
        self
    }

    async fn fetch_history_summary(&self, home_id: &str) -> Result<String> {
        if let Some(ref pool) = self.db_pool {
            // Fetch recent telemetry events for this home - cast kind enum to text
            let events = sqlx::query!(
                r#"
                SELECT
                    task_id,
                    kind::text as "kind!",
                    duration_sec,
                    comment,
                    source,
                    te.created_at
                FROM telemetry_events te
                JOIN plans p ON te.task_id = ANY(
                    SELECT id FROM plan_tasks WHERE plan_id = p.id
                )
                WHERE p.home_id = $1
                ORDER BY te.created_at DESC
                LIMIT 50
                "#,
                home_id
            )
            .fetch_all(pool)
            .await?;

            if events.is_empty() {
                return Ok(format!(
                    "No history data available for home {}. This is a new home with no completed tasks yet.",
                    home_id
                ));
            }

            // Analyze patterns
            let completed_count = events.iter().filter(|e| e.kind == "done").count();
            let skipped_count = events.iter().filter(|e| e.kind == "skip").count();
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
                "History analysis for home {}:\n- Total events: {}\n- Completed: {}\n- Skipped: {}\n- Average duration: {} seconds\n- Recent activity shows patterns in task completion timing and duration.",
                home_id,
                events.len(),
                completed_count,
                skipped_count,
                avg_duration
            ))
        } else {
            // Fallback if no DB pool
            Ok(format!(
                "History for home {}: Recent task completions show patterns in timing and duration.",
                home_id
            ))
        }
    }

    async fn fetch_plan_context(&self, plan_id: Option<&str>) -> Result<String> {
        if let Some(pid) = plan_id {
            if let Ok(Some(plan)) = self.real_plan_service.get_plan(pid).await {
                return Ok(format!(
                    "Current plan has {} tasks across {} sections. Tasks range from {} to {} minutes.",
                    plan.tasks.len(),
                    plan.sections.len(),
                    plan.tasks.iter().map(|t| t.estimate_min).min().unwrap_or(0),
                    plan.tasks.iter().map(|t| t.estimate_min).max().unwrap_or(0),
                ));
            }
        }
        Ok("No specific plan context available.".to_string())
    }

    fn is_gpt5_model(&self) -> bool {
        self.model.starts_with("gpt-5") || self.model.starts_with("gpt5")
    }

    fn get_api_endpoint(&self) -> String {
        if self.is_gpt5_model() {
            // GPT-5/5.1 uses the newer API endpoint
            "https://api.openai.com/v1/chat/completions".to_string()
        } else {
            // Standard GPT-4 and earlier models
            "https://api.openai.com/v1/chat/completions".to_string()
        }
    }

    fn build_request_body(&self, system_prompt: &str, user_prompt: &str) -> serde_json::Value {
        if self.is_gpt5_model() {
            // GPT-5/5.1 API format - may have different parameters
            json!({
                "model": self.model,
                "messages": [
                    {
                        "role": "system",
                        "content": system_prompt
                    },
                    {
                        "role": "user",
                        "content": user_prompt
                    }
                ],
                "temperature": 0.7,
                "max_tokens": 1000,
                // GPT-5/5.1 specific parameters
                "response_format": { "type": "json_object" },
                "top_p": 0.95,
                "frequency_penalty": 0.0,
                "presence_penalty": 0.0
            })
        } else {
            // Standard GPT-4 API format
            json!({
                "model": self.model,
                "messages": [
                    {
                        "role": "system",
                        "content": system_prompt
                    },
                    {
                        "role": "user",
                        "content": user_prompt
                    }
                ],
                "temperature": 0.7,
                "max_tokens": 1000
            })
        }
    }

    async fn call_openai(&self, prompt: String) -> Result<String> {
        let api_key = self
            .openai_api_key
            .as_ref()
            .ok_or_else(|| anyhow::anyhow!("OpenAI API key not configured"))?;

        let system_prompt = "You are an AI assistant that analyzes cleaning task patterns and provides actionable suggestions to optimize cleaning plans. Return suggestions as a JSON object with a 'suggestions' array. Each suggestion must have: id, text, confidence (0-100), action (one of: adjust_schedule, merge_tasks, change_frequency, assign_to_member, split_task), source (always 'cloud_ai'), target (optional object with task_id, field, proposed_value), state (object with accepted: false, dismissed: false, applied_locally: false).";

        let request_body = self.build_request_body(system_prompt, &prompt);

        let response = self
            .openai_client
            .post(&self.get_api_endpoint())
            .header("Authorization", format!("Bearer {}", api_key))
            .header("Content-Type", "application/json")
            .json(&request_body)
            .send()
            .await
            .context("Failed to call OpenAI API")?;

        if !response.status().is_success() {
            let error_text = response.text().await.unwrap_or_else(|_| "Unknown error".to_string());
            return Err(anyhow::anyhow!("OpenAI API error: {}", error_text));
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

        Ok(content.to_string())
    }

    fn parse_suggestions(&self, content: String) -> Result<Vec<Suggestion>> {
        // GPT-5/5.1 returns JSON object with suggestions array, GPT-4 might return just array
        let json_str = if content.trim().starts_with("```") {
            // Extract from markdown code blocks
            let lines: Vec<&str> = content.lines().collect();
            lines
                .iter()
                .skip_while(|l| !l.contains("json") && !l.contains("```"))
                .skip(1)
                .take_while(|l| !l.contains("```"))
                .copied()
                .collect::<Vec<_>>()
                .join("\n")
        } else {
            content
        };

        // Try parsing as JSON object first (GPT-5/5.1 format)
        let suggestions: Vec<Suggestion> = if let Ok(json_obj) = serde_json::from_str::<serde_json::Value>(&json_str) {
            // Check if it's an object with "suggestions" key (GPT-5 format)
            if let Some(suggestions_array) = json_obj.get("suggestions").and_then(|v| v.as_array()) {
                serde_json::from_value(serde_json::Value::Array(suggestions_array.clone()))
                    .context("Failed to parse suggestions array from JSON object")?
            } else if json_obj.is_array() {
                // Direct array format
                serde_json::from_value(json_obj)
                    .context("Failed to parse suggestions array")?
            } else {
                return Err(anyhow::anyhow!("Unexpected JSON format in LLM response"));
            }
        } else {
            // Try parsing as direct array (GPT-4 format)
            // First try direct parse, then try to extract JSON array from text
            if let Ok(suggestions) = serde_json::from_str(&json_str) {
                suggestions
            } else {
                // If direct parse fails, try to find JSON array in the text
                let json_start = json_str.find('[')
                    .ok_or_else(|| anyhow::anyhow!("No JSON array found in LLM response"))?;
                let json_end = json_str.rfind(']')
                    .ok_or_else(|| anyhow::anyhow!("No closing bracket found in LLM response"))? + 1;
                serde_json::from_str(&json_str[json_start..json_end])
                    .context("Failed to parse extracted JSON array")?
            }
        };

        Ok(suggestions)
    }
}

#[async_trait]
impl CleanFlowSuggestionService for LlmSuggestionService {
    async fn generate_suggestions(
        &self,
        home_id: &str,
        plan_id: Option<&str>,
    ) -> Result<Vec<Suggestion>> {
        info!(
            home_id = home_id,
            plan_id = plan_id,
            "Generating LLM-based suggestions"
        );

        // If no API key, return empty suggestions
        if self.openai_api_key.is_none() {
            warn!("OpenAI API key not configured, returning empty suggestions");
            return Ok(vec![]);
        }

        // Build context from history and plan
        let history_summary = self.fetch_history_summary(home_id).await?;
        let plan_context = self.fetch_plan_context(plan_id).await?;

        let prompt = format!(
            r#"Analyze the following cleaning task data and provide 2-3 actionable suggestions to optimize the cleaning plan.

History Summary:
{}

Current Plan Context:
{}

Provide suggestions that:
1. Are specific and actionable
2. Have high confidence (80+)
3. Address real patterns you see in the data
4. Include target information when applicable

Return only a JSON array of suggestions, no other text."#,
            history_summary, plan_context
        );

        let response = self.call_openai(prompt).await?;

        let mut suggestions = self.parse_suggestions(response)?;

        // Ensure all suggestions have required fields
        for suggestion in &mut suggestions {
            if suggestion.id.is_empty() {
                suggestion.id = format!("sg_{}", uuid::Uuid::new_v4().to_string()[..8].to_string());
            }
            if suggestion.source.is_empty() {
                suggestion.source = "cloud_ai".to_string();
            }
            if suggestion.confidence < 0 || suggestion.confidence > 100 {
                suggestion.confidence = 75; // Default confidence
            }
        }

        info!(
            count = suggestions.len(),
            "Generated {} suggestions from LLM",
            suggestions.len()
        );

        Ok(suggestions)
    }
}
