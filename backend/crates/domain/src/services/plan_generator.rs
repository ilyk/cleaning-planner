//! LLM-based plan generation service
//!
//! Generates personalized cleaning plans using Claude based on home profile,
//! mode selection, and historical behavior patterns.

use crate::models::*;
use crate::services::real_lookup_service::HomeProfile;
use anyhow::{Context, Result};
use async_trait::async_trait;
use reqwest::Client;
use serde::{Deserialize, Serialize};
use serde_json::json;
use tracing::{info, warn, error};

/// Generated plan from LLM
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GeneratedPlan {
    pub sections: Vec<PlanSection>,
    pub tasks: Vec<PlanTask>,
}

/// Request to generate a plan
#[derive(Debug, Clone)]
pub struct PlanGenerationRequest {
    pub home_profile: HomeProfile,
    pub mode: PlanMode,
    pub date: chrono::NaiveDate,
    /// Optional constraints from user preferences
    pub constraints: Option<serde_json::Value>,
    /// Optional history summary for personalization
    pub history_summary: Option<String>,
}

#[async_trait]
pub trait PlanGenerator: Send + Sync {
    /// Generate a cleaning plan for a home
    async fn generate(&self, request: PlanGenerationRequest) -> Result<GeneratedPlan>;
}

/// LLM-backed plan generator using Claude
pub struct LlmPlanGenerator {
    http_client: Client,
    anthropic_api_key: Option<String>,
    model: String,
}

impl LlmPlanGenerator {
    pub fn new(anthropic_api_key: Option<String>) -> Self {
        Self {
            http_client: Client::new(),
            anthropic_api_key,
            model: "claude-sonnet-4-20250514".to_string(),
        }
    }

    pub fn with_model(mut self, model: String) -> Self {
        self.model = model;
        self
    }

    fn build_generation_prompt(&self, request: &PlanGenerationRequest) -> String {
        let mode_description = match request.mode {
            PlanMode::Focus => "Focus Mode: Quick wins, 15-30 minutes total. Prioritize visible, high-impact tasks. Perfect for busy days or low motivation.",
            PlanMode::FullReset => "Full Reset Mode: Deep clean, 2-4 hours. Cover all rooms systematically. For weekend deep cleans or guests coming.",
            PlanMode::LowEnergy => "Low Energy Mode: Minimal effort tasks only, 10-20 minutes. Simple maintenance tasks that don't require much physical effort.",
            PlanMode::Pet => "Pet Mode: Focus on pet-related cleaning - fur, dander, odors. Include tasks like vacuuming pet areas, cleaning food bowls, freshening pet beds.",
        };

        let rooms_json = serde_json::to_string_pretty(&request.home_profile.rooms).unwrap_or_default();
        let members_json = serde_json::to_string_pretty(&request.home_profile.members).unwrap_or_default();

        // Check for pets in home metadata
        let pets_info = request.home_profile.home.metadata
            .as_ref()
            .and_then(|m| m.get("pets"))
            .map(|p| serde_json::to_string_pretty(p).unwrap_or_default())
            .unwrap_or_else(|| "No pets".to_string());

        let preferences_info = request.home_profile.home.metadata
            .as_ref()
            .map(|m| serde_json::to_string_pretty(m).unwrap_or_default())
            .unwrap_or_else(|| "No specific preferences".to_string());

        let history_info = request.history_summary
            .as_ref()
            .map(|h| format!("User History:\n{}", h))
            .unwrap_or_else(|| "No history available (new user)".to_string());

        format!(
            r#"Generate a personalized cleaning plan for today ({date}).

## Mode
{mode}

## Home Profile
Rooms:
{rooms}

Household Members:
{members}

Pets:
{pets}

Preferences/Constraints:
{preferences}

## History
{history}

## Output Format
Return a JSON object with this exact structure:
{{
  "sections": [
    {{
      "id": "s_now",
      "title": "Now",
      "tasks": ["task_id_1", "task_id_2"]
    }},
    {{
      "id": "s_next",
      "title": "Next",
      "tasks": ["task_id_3"]
    }},
    {{
      "id": "s_later",
      "title": "Later",
      "tasks": ["task_id_4"]
    }}
  ],
  "tasks": [
    {{
      "task_id": "task_001",
      "room_id": "actual_room_id_from_rooms_list",
      "title": "Task description",
      "estimate_min": 5,
      "priority": 1,
      "section_id": "s_now"
    }}
  ]
}}

## Rules
1. Use actual room IDs from the rooms list above
2. task_id should be unique (format: task_XXX where XXX is 001, 002, etc.)
3. section_id must match one of the sections (s_now, s_next, s_later)
4. priority: 1 = highest, 3 = lowest
5. estimate_min: realistic time in minutes
6. Distribute tasks appropriately for the selected mode
7. Consider pet-related tasks if pets exist and mode is Pet or FullReset
8. Match section.tasks array to actual task_ids in tasks array
9. Return ONLY valid JSON, no other text"#,
            date = request.date,
            mode = mode_description,
            rooms = rooms_json,
            members = members_json,
            pets = pets_info,
            preferences = preferences_info,
            history = history_info,
        )
    }

    async fn call_anthropic(&self, prompt: &str) -> Result<String> {
        let api_key = self
            .anthropic_api_key
            .as_ref()
            .ok_or_else(|| anyhow::anyhow!("Anthropic API key not configured"))?;

        let request_body = json!({
            "model": self.model,
            "max_tokens": 4096,
            "messages": [
                {
                    "role": "user",
                    "content": prompt
                }
            ]
        });

        let response = self
            .http_client
            .post("https://api.anthropic.com/v1/messages")
            .header("x-api-key", api_key)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .json(&request_body)
            .send()
            .await
            .context("Failed to call Anthropic API")?;

        if !response.status().is_success() {
            let error_text = response.text().await.unwrap_or_else(|_| "Unknown error".to_string());
            return Err(anyhow::anyhow!("Anthropic API error: {}", error_text));
        }

        let result: serde_json::Value = response
            .json()
            .await
            .context("Failed to parse Anthropic response")?;

        let content = result
            .get("content")
            .and_then(|c| c.get(0))
            .and_then(|c| c.get("text"))
            .and_then(|t| t.as_str())
            .ok_or_else(|| anyhow::anyhow!("Invalid Anthropic response format"))?;

        Ok(content.to_string())
    }

    fn parse_generated_plan(&self, content: &str) -> Result<GeneratedPlan> {
        // Extract JSON from response (may be wrapped in markdown code blocks)
        let json_str = if content.trim().starts_with("```") {
            content
                .lines()
                .skip_while(|l| !l.starts_with("{"))
                .take_while(|l| !l.starts_with("```") || l.starts_with("{"))
                .collect::<Vec<_>>()
                .join("\n")
        } else if let Some(start) = content.find('{') {
            if let Some(end) = content.rfind('}') {
                content[start..=end].to_string()
            } else {
                content.to_string()
            }
        } else {
            content.to_string()
        };

        // Parse the raw structure
        let raw: serde_json::Value = serde_json::from_str(&json_str)
            .context("Failed to parse generated plan JSON")?;

        // Extract sections
        let sections_raw = raw.get("sections")
            .and_then(|s| s.as_array())
            .ok_or_else(|| anyhow::anyhow!("Missing sections in generated plan"))?;

        let sections: Vec<PlanSection> = sections_raw
            .iter()
            .map(|s| PlanSection {
                id: s.get("id").and_then(|v| v.as_str()).unwrap_or("s_unknown").to_string(),
                title: s.get("title").and_then(|v| v.as_str()).unwrap_or("Unknown").to_string(),
                tasks: s.get("tasks")
                    .and_then(|v| v.as_array())
                    .map(|arr| arr.iter().filter_map(|t| t.as_str().map(String::from)).collect())
                    .unwrap_or_default(),
            })
            .collect();

        // Extract tasks
        let tasks_raw = raw.get("tasks")
            .and_then(|t| t.as_array())
            .ok_or_else(|| anyhow::anyhow!("Missing tasks in generated plan"))?;

        let tasks: Vec<PlanTask> = tasks_raw
            .iter()
            .map(|t| PlanTask {
                task_id: t.get("task_id").and_then(|v| v.as_str()).unwrap_or("unknown").to_string(),
                template_id: t.get("template_id").and_then(|v| v.as_str()).map(String::from),
                room_id: t.get("room_id").and_then(|v| v.as_str()).unwrap_or("unknown").to_string(),
                title: t.get("title").and_then(|v| v.as_str()).unwrap_or("Untitled task").to_string(),
                estimate_min: t.get("estimate_min").and_then(|v| v.as_i64()).unwrap_or(5) as i32,
                state: TaskState::Pending,
                priority: t.get("priority").and_then(|v| v.as_i64()).unwrap_or(2) as i32,
                section_id: t.get("section_id").and_then(|v| v.as_str()).unwrap_or("s_now").to_string(),
                assignee: None, // Will be assigned later if family assignment is requested
                metadata: t.get("metadata").cloned(),
            })
            .collect();

        Ok(GeneratedPlan { sections, tasks })
    }

    /// Generate a fallback plan without LLM
    fn generate_fallback_plan(&self, request: &PlanGenerationRequest) -> GeneratedPlan {
        let rooms = &request.home_profile.rooms;

        // Generate basic tasks based on mode and available rooms
        let mut tasks = Vec::new();
        let mut task_counter = 1;

        // Type alias for clarity: (section_id, section_title)
        type SectionConfig<'a> = Vec<(&'a str, &'a str)>;

        let (task_configs, sections_config): (Vec<(&str, &str, i32, &str)>, SectionConfig) = match request.mode {
            PlanMode::Focus => {
                // Quick wins - one task per room, high visibility
                let configs: Vec<(&str, &str, i32, &str)> = rooms.iter()
                    .take(3) // Max 3 tasks for focus mode
                    .map(|room| {
                        let task_title = match room.kind {
                            Some(RoomKind::Kitchen) => "Quick wipe kitchen surfaces",
                            Some(RoomKind::Bathroom) => "Quick wipe bathroom surfaces",
                            Some(RoomKind::Bedroom) => "Make bed and tidy nightstand",
                            Some(RoomKind::Living) => "Quick tidy living room",
                            _ => "Quick tidy and organize",
                        };
                        (room.id.as_str(), task_title, 5, "s_now")
                    })
                    .collect();
                (configs, vec![("s_now", "Do Now")])
            }
            PlanMode::FullReset => {
                // Full clean - multiple tasks per room
                let mut configs: Vec<(&str, &str, i32, &str)> = Vec::new();
                for room in rooms {
                    match room.kind {
                        Some(RoomKind::Kitchen) => {
                            configs.push((room.id.as_str(), "Clean kitchen counters", 10, "s_now"));
                            configs.push((room.id.as_str(), "Clean stovetop", 15, "s_next"));
                            configs.push((room.id.as_str(), "Mop kitchen floor", 10, "s_later"));
                        }
                        Some(RoomKind::Bathroom) => {
                            configs.push((room.id.as_str(), "Clean toilet", 10, "s_now"));
                            configs.push((room.id.as_str(), "Clean shower/tub", 15, "s_next"));
                            configs.push((room.id.as_str(), "Mop bathroom floor", 10, "s_later"));
                        }
                        Some(RoomKind::Bedroom) => {
                            configs.push((room.id.as_str(), "Change bed sheets", 10, "s_now"));
                            configs.push((room.id.as_str(), "Dust surfaces", 10, "s_next"));
                            configs.push((room.id.as_str(), "Vacuum floor", 10, "s_later"));
                        }
                        Some(RoomKind::Living) => {
                            configs.push((room.id.as_str(), "Dust and organize", 15, "s_now"));
                            configs.push((room.id.as_str(), "Vacuum living room", 15, "s_next"));
                        }
                        _ => {
                            configs.push((room.id.as_str(), "General tidy up", 10, "s_next"));
                        }
                    }
                }
                (configs, vec![
                    ("s_now", "Start Here"),
                    ("s_next", "Then"),
                    ("s_later", "Finish With"),
                ])
            }
            PlanMode::LowEnergy => {
                // Minimal effort tasks
                let configs: Vec<(&str, &str, i32, &str)> = rooms.iter()
                    .take(2)
                    .map(|room| (room.id.as_str(), "Quick 5-minute tidy", 5, "s_now"))
                    .collect();
                (configs, vec![("s_now", "Easy Tasks")])
            }
            PlanMode::Pet => {
                // Pet-focused tasks
                let mut configs: Vec<(&str, &str, i32, &str)> = Vec::new();
                for room in rooms {
                    match room.kind {
                        Some(RoomKind::Living) => {
                            configs.push((room.id.as_str(), "Vacuum pet hair from furniture", 15, "s_now"));
                            configs.push((room.id.as_str(), "Clean pet toys", 10, "s_next"));
                        }
                        Some(RoomKind::Kitchen) => {
                            configs.push((room.id.as_str(), "Clean pet food bowls", 5, "s_now"));
                        }
                        Some(RoomKind::Bedroom) => {
                            configs.push((room.id.as_str(), "Change/wash pet bedding", 10, "s_next"));
                        }
                        _ => {}
                    }
                }
                if configs.is_empty() {
                    // Default pet tasks if no matching rooms
                    if let Some(room) = rooms.first() {
                        configs.push((room.id.as_str(), "Vacuum pet hair", 15, "s_now"));
                        configs.push((room.id.as_str(), "Clean pet items", 10, "s_next"));
                    }
                }
                (configs, vec![
                    ("s_now", "Pet Priority"),
                    ("s_next", "Additional"),
                ])
            }
        };

        // Build tasks from configs
        let mut section_tasks: std::collections::HashMap<String, Vec<String>> = std::collections::HashMap::new();

        for (room_id, title, estimate, section_id) in task_configs {
            let task_id = format!("task_{:03}", task_counter);
            task_counter += 1;

            tasks.push(PlanTask {
                task_id: task_id.clone(),
                template_id: None,
                room_id: room_id.to_string(),
                title: title.to_string(),
                estimate_min: estimate,
                state: TaskState::Pending,
                priority: task_counter as i32,
                section_id: section_id.to_string(),
                assignee: None,
                metadata: None,
            });

            section_tasks
                .entry(section_id.to_string())
                .or_default()
                .push(task_id);
        }

        // Build sections
        let sections: Vec<PlanSection> = sections_config
            .into_iter()
            .map(|(id, title)| PlanSection {
                id: id.to_string(),
                title: title.to_string(),
                tasks: section_tasks.get(id).cloned().unwrap_or_default(),
            })
            .filter(|s| !s.tasks.is_empty())
            .collect();

        GeneratedPlan { sections, tasks }
    }
}

#[async_trait]
impl PlanGenerator for LlmPlanGenerator {
    async fn generate(&self, request: PlanGenerationRequest) -> Result<GeneratedPlan> {
        info!(
            home_id = %request.home_profile.home.id,
            mode = ?request.mode,
            rooms = request.home_profile.rooms.len(),
            "Generating cleaning plan"
        );

        // Check for API key
        if self.anthropic_api_key.is_none() {
            warn!("Anthropic API key not configured, using fallback plan generation");
            return Ok(self.generate_fallback_plan(&request));
        }

        // Build prompt and call LLM
        let prompt = self.build_generation_prompt(&request);

        match self.call_anthropic(&prompt).await {
            Ok(response) => {
                match self.parse_generated_plan(&response) {
                    Ok(plan) => {
                        info!(
                            sections = plan.sections.len(),
                            tasks = plan.tasks.len(),
                            "Generated plan from LLM"
                        );
                        Ok(plan)
                    }
                    Err(e) => {
                        error!(error = %e, "Failed to parse LLM response, using fallback");
                        Ok(self.generate_fallback_plan(&request))
                    }
                }
            }
            Err(e) => {
                error!(error = %e, "LLM call failed, using fallback");
                Ok(self.generate_fallback_plan(&request))
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_fallback_plan_focus_mode() {
        let generator = LlmPlanGenerator::new(None);

        let home_profile = HomeProfile {
            home: Home {
                id: "home_test".to_string(),
                owner_user_id: "user_test".to_string(),
                name: "Test Home".to_string(),
                tz: "UTC".to_string(),
                locale: "en-US".to_string(),
                created_at: chrono::Utc::now(),
                updated_at: chrono::Utc::now(),
                metadata: None,
            },
            members: vec![],
            rooms: vec![
                Room {
                    id: "r_kitchen".to_string(),
                    home_id: "home_test".to_string(),
                    name: "Kitchen".to_string(),
                    kind: Some(RoomKind::Kitchen),
                    metadata: None,
                },
                Room {
                    id: "r_living".to_string(),
                    home_id: "home_test".to_string(),
                    name: "Living Room".to_string(),
                    kind: Some(RoomKind::Living),
                    metadata: None,
                },
            ],
        };

        let request = PlanGenerationRequest {
            home_profile,
            mode: PlanMode::Focus,
            date: chrono::NaiveDate::from_ymd_opt(2025, 1, 1).unwrap(),
            constraints: None,
            history_summary: None,
        };

        let plan = generator.generate_fallback_plan(&request);

        assert!(!plan.sections.is_empty());
        assert!(!plan.tasks.is_empty());
        assert!(plan.tasks.len() <= 3); // Focus mode should have max 3 tasks
    }

    #[test]
    fn test_fallback_plan_full_reset_mode() {
        let generator = LlmPlanGenerator::new(None);

        let home_profile = HomeProfile {
            home: Home {
                id: "home_test".to_string(),
                owner_user_id: "user_test".to_string(),
                name: "Test Home".to_string(),
                tz: "UTC".to_string(),
                locale: "en-US".to_string(),
                created_at: chrono::Utc::now(),
                updated_at: chrono::Utc::now(),
                metadata: None,
            },
            members: vec![],
            rooms: vec![
                Room {
                    id: "r_kitchen".to_string(),
                    home_id: "home_test".to_string(),
                    name: "Kitchen".to_string(),
                    kind: Some(RoomKind::Kitchen),
                    metadata: None,
                },
            ],
        };

        let request = PlanGenerationRequest {
            home_profile,
            mode: PlanMode::FullReset,
            date: chrono::NaiveDate::from_ymd_opt(2025, 1, 1).unwrap(),
            constraints: None,
            history_summary: None,
        };

        let plan = generator.generate_fallback_plan(&request);

        // Full reset should have multiple sections
        assert!(plan.sections.len() >= 1);
        // Kitchen should have multiple tasks in full reset
        assert!(plan.tasks.len() >= 2);
    }
}
