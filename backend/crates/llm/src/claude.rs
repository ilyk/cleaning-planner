//! Claude/Anthropic Messages API adapter (text-only streaming)
//!
//! Implements LlmRealtime trait using Anthropic's Messages API with SSE streaming.
//! Designed for text-based conversational onboarding with "guided discovery" style.

use crate::traits::{LlmEvent, LlmRealtime};
use anyhow::{Context, Result};
use bytes::Bytes;
use futures::StreamExt;
use reqwest::Client;
use serde::{Deserialize, Serialize};
use std::sync::Arc;
use tokio::sync::mpsc::{unbounded_channel, UnboundedReceiver, UnboundedSender};
use tokio::sync::Mutex;
use tracing::{debug, error, info, warn};

/// Clara's guided discovery system prompt for onboarding
const CLARA_SYSTEM_PROMPT: &str = r#"You are Clara, a friendly AI assistant helping families set up their personalized cleaning plan in the CleanFlow app.

## Your Role
You're having a warm, conversational chat to learn about their home and cleaning needs. Your goal is to understand their situation so you can create a cleaning plan that actually works for them.

## Communication Style
- Be warm, encouraging, and conversational - like a helpful friend
- Use short, focused messages (2-3 sentences max per response)
- Listen actively - acknowledge what they share before asking anything new
- Never interrogate - gently guide the conversation
- If they mention something on your checklist, acknowledge it and build on it
- Use gentle nudges, not direct questions: "I'd love to hear about..." not "How many...?"

## Mental Checklist (cover naturally, not in order)
Track which topics have been covered:
[ ] Rooms in the home (which rooms need cleaning attention)
[ ] People in household (adults, kids, their ages/needs)
[ ] Pets (type, shedding, where they spend time)
[ ] Schedule preferences (daily routines, available time windows)
[ ] Problem areas (spots that need extra attention)
[ ] Cleaning style preference (thorough vs quick, any preferences)

## Conversation Flow
1. **Opening**: Greet warmly, explain you'll help set up their cleaning plan through a quick chat
2. **Discovery**: Let them share naturally. When they mention something, acknowledge it and gently explore related topics
3. **Filling gaps**: If a topic hasn't come up after several exchanges, gently nudge toward it
4. **Summary**: Once you have enough info, summarize what you learned and ask if you got it right
5. **Handoff**: Thank them and let them know their plan is being created

## Example Nudges
- "That's great! And who else lives there with you?"
- "It sounds like mornings are busy - is there a quieter time that might work better for cleaning?"
- "I'm curious about the kitchen - is that where you'd like to focus first?"

## Important Guidelines
- Keep responses under 50 words unless summarizing
- Don't overwhelm with multiple questions
- If they seem unsure, offer examples or options
- Celebrate what they share ("That's helpful to know!")
- Stay focused on cleaning-related topics
- Respond in the same language the user writes in

## Completion Signal
When you've covered all checklist items and confirmed with the user, end your summary message with this marker on its own line:
[ONBOARDING_COMPLETE]"#;

/// Claude Messages API adapter
pub struct ClaudeAdapter {
    api_key: String,
    model: String,
    client: Client,
    event_sender: Arc<Mutex<Option<UnboundedSender<LlmEvent>>>>,
    conversation_history: Arc<Mutex<Vec<Message>>>,
    pending_text: Arc<Mutex<String>>,
}

/// Message in conversation history
#[derive(Clone, Debug, Serialize, Deserialize)]
struct Message {
    role: String,
    content: String,
}

/// Request body for Anthropic Messages API
#[derive(Serialize)]
struct MessagesRequest {
    model: String,
    max_tokens: u32,
    stream: bool,
    system: String,
    messages: Vec<Message>,
}

/// SSE event types from Anthropic
#[derive(Debug, Deserialize)]
#[serde(tag = "type")]
enum StreamEvent {
    #[serde(rename = "message_start")]
    MessageStart { message: MessageMeta },
    #[serde(rename = "content_block_start")]
    ContentBlockStart { index: u32, content_block: ContentBlock },
    #[serde(rename = "content_block_delta")]
    ContentBlockDelta { index: u32, delta: Delta },
    #[serde(rename = "content_block_stop")]
    ContentBlockStop { index: u32 },
    #[serde(rename = "message_delta")]
    MessageDelta { delta: MessageDeltaContent, usage: Option<UsageStats> },
    #[serde(rename = "message_stop")]
    MessageStop,
    #[serde(rename = "ping")]
    Ping,
    #[serde(rename = "error")]
    Error { error: ApiError },
}

#[derive(Debug, Deserialize)]
struct MessageMeta {
    id: String,
    model: String,
    usage: Option<UsageStats>,
}

#[derive(Debug, Deserialize)]
struct ContentBlock {
    #[serde(rename = "type")]
    block_type: String,
    text: Option<String>,
}

#[derive(Debug, Deserialize)]
struct Delta {
    #[serde(rename = "type")]
    delta_type: String,
    text: Option<String>,
}

#[derive(Debug, Deserialize)]
struct MessageDeltaContent {
    stop_reason: Option<String>,
}

#[derive(Debug, Deserialize)]
struct UsageStats {
    input_tokens: Option<u32>,
    output_tokens: Option<u32>,
}

#[derive(Debug, Deserialize)]
struct ApiError {
    #[serde(rename = "type")]
    error_type: String,
    message: String,
}

impl ClaudeAdapter {
    pub fn new(api_key: String, model: String) -> Self {
        let client = Client::builder()
            .timeout(std::time::Duration::from_secs(120))
            .build()
            .expect("Failed to build HTTP client");

        Self {
            api_key,
            model,
            client,
            event_sender: Arc::new(Mutex::new(None)),
            conversation_history: Arc::new(Mutex::new(Vec::new())),
            pending_text: Arc::new(Mutex::new(String::new())),
        }
    }

    /// Send request to Claude API and stream response
    async fn send_to_claude(&self) -> Result<()> {
        let history = self.conversation_history.lock().await.clone();

        if history.is_empty() {
            warn!("No messages to send to Claude");
            return Ok(());
        }

        let message_count = history.len();
        let request = MessagesRequest {
            model: self.model.clone(),
            max_tokens: 1024,
            stream: true,
            system: CLARA_SYSTEM_PROMPT.to_string(),
            messages: history,
        };

        info!(model = %self.model, messages = message_count, "Sending request to Claude API");

        let response = self.client
            .post("https://api.anthropic.com/v1/messages")
            .header("x-api-key", &self.api_key)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .json(&request)
            .send()
            .await
            .context("Failed to send request to Claude API")?;

        if !response.status().is_success() {
            let status = response.status();
            let body = response.text().await.unwrap_or_default();
            error!(status = %status, body = %body, "Claude API error");

            let sender = self.event_sender.lock().await;
            if let Some(tx) = sender.as_ref() {
                tx.send(LlmEvent::Error {
                    message: format!("Claude API error: {} - {}", status, body),
                }).ok();
            }
            return Ok(());
        }

        // Process SSE stream
        let mut stream = response.bytes_stream();
        let mut accumulated_text = String::new();
        let mut input_tokens: u32 = 0;
        let mut output_tokens: u32 = 0;

        // Notify output starting
        {
            let sender = self.event_sender.lock().await;
            if let Some(tx) = sender.as_ref() {
                tx.send(LlmEvent::OutputAudioStart).ok();
            }
        }

        let mut buffer = String::new();

        while let Some(chunk_result) = stream.next().await {
            let chunk = match chunk_result {
                Ok(c) => c,
                Err(e) => {
                    error!(error = %e, "Error reading stream chunk");
                    continue;
                }
            };

            buffer.push_str(&String::from_utf8_lossy(&chunk));

            // Process complete SSE events (lines starting with "data: ")
            while let Some(pos) = buffer.find("\n\n") {
                let event_str = buffer[..pos].to_string();
                buffer = buffer[pos + 2..].to_string();

                for line in event_str.lines() {
                    if let Some(data) = line.strip_prefix("data: ") {
                        if data == "[DONE]" {
                            continue;
                        }

                        match serde_json::from_str::<StreamEvent>(data) {
                            Ok(event) => {
                                match event {
                                    StreamEvent::MessageStart { message } => {
                                        debug!(id = %message.id, model = %message.model, "Message started");
                                        if let Some(usage) = message.usage {
                                            if let Some(inp) = usage.input_tokens {
                                                input_tokens = inp;
                                            }
                                        }
                                    }
                                    StreamEvent::ContentBlockDelta { delta, .. } => {
                                        if let Some(text) = delta.text {
                                            accumulated_text.push_str(&text);

                                            let sender = self.event_sender.lock().await;
                                            if let Some(tx) = sender.as_ref() {
                                                tx.send(LlmEvent::OutputTextDelta {
                                                    text: text.clone(),
                                                }).ok();
                                            }
                                        }
                                    }
                                    StreamEvent::MessageDelta { usage, .. } => {
                                        if let Some(u) = usage {
                                            if let Some(out) = u.output_tokens {
                                                output_tokens = out;
                                            }
                                        }
                                    }
                                    StreamEvent::MessageStop => {
                                        debug!("Message complete");
                                    }
                                    StreamEvent::Error { error } => {
                                        error!(error_type = %error.error_type, message = %error.message, "Claude API stream error");
                                        let sender = self.event_sender.lock().await;
                                        if let Some(tx) = sender.as_ref() {
                                            tx.send(LlmEvent::Error {
                                                message: error.message,
                                            }).ok();
                                        }
                                    }
                                    StreamEvent::Ping => {
                                        debug!("Ping received");
                                    }
                                    _ => {}
                                }
                            }
                            Err(e) => {
                                debug!(data = %data, error = %e, "Failed to parse SSE event");
                            }
                        }
                    }
                }
            }
        }

        // Add assistant response to history
        if !accumulated_text.is_empty() {
            self.conversation_history.lock().await.push(Message {
                role: "assistant".to_string(),
                content: accumulated_text,
            });
        }

        // Send completion events
        {
            let sender = self.event_sender.lock().await;
            if let Some(tx) = sender.as_ref() {
                tx.send(LlmEvent::OutputAudioCommit).ok();
                tx.send(LlmEvent::Finished {
                    usage_in: input_tokens,
                    usage_out: output_tokens,
                }).ok();
            }
        }

        info!(input_tokens = input_tokens, output_tokens = output_tokens, "Claude response complete");
        Ok(())
    }
}

impl LlmRealtime for ClaudeAdapter {
    fn start_turn(
        &self,
        turn_id: &str,
        _policy_version: &str,
        _prompt_version: &str,
    ) -> Result<()> {
        info!(turn_id = turn_id, "Claude adapter: Turn started");

        // Clear pending text for new turn
        let pending = self.pending_text.clone();
        tokio::spawn(async move {
            *pending.lock().await = String::new();
        });

        Ok(())
    }

    fn send_audio_chunk(&self, data: &[u8], format: &str) -> Result<()> {
        // Text-only adapter - audio chunks are ignored
        debug!(
            size = data.len(),
            format = format,
            "Claude adapter: Audio chunk ignored (text-only mode)"
        );
        Ok(())
    }

    fn send_text(&self, text: &str) -> Result<()> {
        info!(text_len = text.len(), "Claude adapter: Received text input");

        let text = text.to_string();
        let conversation_history = self.conversation_history.clone();
        let event_sender = self.event_sender.clone();
        let api_key = self.api_key.clone();
        let model = self.model.clone();
        let client = self.client.clone();

        // Spawn async task to send to Claude
        tokio::spawn(async move {
            // Add user message to history
            conversation_history.lock().await.push(Message {
                role: "user".to_string(),
                content: text,
            });

            // Get current history for API call
            let history = conversation_history.lock().await.clone();

            // Send to Claude API
            let request = MessagesRequest {
                model: model.clone(),
                max_tokens: 1024,
                stream: true,
                system: CLARA_SYSTEM_PROMPT.to_string(),
                messages: history.clone(),
            };

            tracing::info!(model = %model, message_count = history.len(), "Sending request to Claude API (send_text)");

            let response = match client
                .post("https://api.anthropic.com/v1/messages")
                .header("x-api-key", &api_key)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .json(&request)
                .send()
                .await {
                    Ok(r) => r,
                    Err(e) => {
                        tracing::error!(error = %e, "Failed to send request to Claude API");
                        let sender = event_sender.lock().await;
                        if let Some(tx) = sender.as_ref() {
                            tx.send(LlmEvent::Error { message: e.to_string() }).ok();
                        }
                        return;
                    }
                };

            if !response.status().is_success() {
                let status = response.status();
                let body = response.text().await.unwrap_or_default();
                tracing::error!(status = %status, body = %body, "Claude API error");
                let sender = event_sender.lock().await;
                if let Some(tx) = sender.as_ref() {
                    tx.send(LlmEvent::Error {
                        message: format!("Claude API error: {} - {}", status, body),
                    }).ok();
                }
                return;
            }

            // Process SSE stream
            let mut stream = response.bytes_stream();
            let mut accumulated_text = String::new();
            let mut input_tokens: u32 = 0;
            let mut output_tokens: u32 = 0;

            // Notify output starting
            {
                let sender = event_sender.lock().await;
                if let Some(tx) = sender.as_ref() {
                    tracing::debug!("Sending OutputAudioStart event");
                    tx.send(LlmEvent::OutputAudioStart).ok();
                } else {
                    tracing::error!("Event sender is None - cannot send OutputAudioStart!");
                }
            }

            let mut buffer = String::new();

            while let Some(chunk_result) = stream.next().await {
                let chunk = match chunk_result {
                    Ok(c) => c,
                    Err(e) => {
                        tracing::error!(error = %e, "Error reading stream chunk");
                        continue;
                    }
                };

                buffer.push_str(&String::from_utf8_lossy(&chunk));

                // Process complete SSE events
                while let Some(pos) = buffer.find("\n\n") {
                    let event_str = buffer[..pos].to_string();
                    buffer = buffer[pos + 2..].to_string();

                    for line in event_str.lines() {
                        if let Some(data) = line.strip_prefix("data: ") {
                            if data == "[DONE]" {
                                continue;
                            }

                            if let Ok(event) = serde_json::from_str::<StreamEvent>(data) {
                                match event {
                                    StreamEvent::ContentBlockDelta { delta, .. } => {
                                        if let Some(text) = delta.text {
                                            tracing::info!(text_len = text.len(), text_preview = %text.chars().take(50).collect::<String>(), "Claude: Emitting text delta");
                                            accumulated_text.push_str(&text);
                                            let sender = event_sender.lock().await;
                                            if let Some(tx) = sender.as_ref() {
                                                if tx.send(LlmEvent::OutputTextDelta { text }).is_err() {
                                                    tracing::error!("Failed to send text delta - receiver dropped");
                                                }
                                            } else {
                                                tracing::error!("Event sender is None - cannot send text delta!");
                                            }
                                        }
                                    }
                                    StreamEvent::MessageDelta { usage, .. } => {
                                        if let Some(u) = usage {
                                            output_tokens = u.output_tokens.unwrap_or(0);
                                        }
                                    }
                                    StreamEvent::MessageStart { message } => {
                                        if let Some(u) = message.usage {
                                            input_tokens = u.input_tokens.unwrap_or(0);
                                        }
                                    }
                                    StreamEvent::MessageStop => {
                                        // Message complete
                                    }
                                    _ => {}
                                }
                            }
                        }
                    }
                }
            }

            // Add assistant response to history
            if !accumulated_text.is_empty() {
                conversation_history.lock().await.push(Message {
                    role: "assistant".to_string(),
                    content: accumulated_text.clone(),
                });
            }

            // Send turn finished event
            {
                let sender = event_sender.lock().await;
                if let Some(tx) = sender.as_ref() {
                    tx.send(LlmEvent::Finished {
                        usage_in: input_tokens,
                        usage_out: output_tokens,
                    }).ok();
                }
            }
        });

        Ok(())
    }

    fn commit_input(&self) -> Result<()> {
        info!("Claude adapter: Input committed");

        let pending_text = self.pending_text.clone();
        let conversation_history = self.conversation_history.clone();
        let adapter_api_key = self.api_key.clone();
        let adapter_model = self.model.clone();
        let event_sender = self.event_sender.clone();

        // Spawn async task to send to Claude
        tokio::spawn(async move {
            let text = {
                let mut pending = pending_text.lock().await;
                let text = pending.clone();
                pending.clear();
                text
            };

            if text.is_empty() {
                warn!("No text to commit");
                return;
            }

            // Add user message to history
            conversation_history.lock().await.push(Message {
                role: "user".to_string(),
                content: text,
            });

            // Create temporary adapter instance for API call
            let adapter = ClaudeAdapter {
                api_key: adapter_api_key,
                model: adapter_model,
                client: Client::new(),
                event_sender,
                conversation_history,
                pending_text: Arc::new(Mutex::new(String::new())),
            };

            if let Err(e) = adapter.send_to_claude().await {
                error!(error = %e, "Failed to get Claude response");
            }
        });

        Ok(())
    }

    fn interrupt(&self) -> Result<()> {
        info!("Claude adapter: Interrupted");

        // For text mode, just signal completion
        let sender = self.event_sender.clone();
        tokio::spawn(async move {
            let sender = sender.lock().await;
            if let Some(tx) = sender.as_ref() {
                tx.send(LlmEvent::OutputAudioCommit).ok();
            }
        });

        Ok(())
    }

    fn subscribe(&self) -> UnboundedReceiver<LlmEvent> {
        let (tx, rx) = unbounded_channel();

        // Set sender synchronously using try_lock to avoid race condition
        // This ensures the sender is set before any messages are sent
        let sender = self.event_sender.clone();

        // Use try_lock - in practice, the lock should always be available at subscription time
        match sender.try_lock() {
            Ok(mut guard) => {
                *guard = Some(tx);
                info!("Claude adapter: Event sender set synchronously");
            }
            Err(_) => {
                // Fallback: spawn but this is a race condition we should fix
                warn!("Could not set event sender synchronously, using async fallback");
                let sender_clone = sender.clone();
                tokio::spawn(async move {
                    *sender_clone.lock().await = Some(tx);
                });
            }
        }

        rx
    }
}

/// Extension trait for text input (since base trait is audio-focused)
impl ClaudeAdapter {
    /// Add text input directly (bypassing audio)
    pub fn add_text_input(&self, text: String) {
        let pending = self.pending_text.clone();
        tokio::spawn(async move {
            let mut pending = pending.lock().await;
            if !pending.is_empty() {
                pending.push(' ');
            }
            pending.push_str(&text);
        });
    }

    /// Clear conversation history (for new session)
    pub fn clear_history(&self) {
        let history = self.conversation_history.clone();
        tokio::spawn(async move {
            history.lock().await.clear();
        });
    }

    /// Get conversation history (for persistence)
    pub async fn get_history(&self) -> Vec<(String, String)> {
        self.conversation_history
            .lock()
            .await
            .iter()
            .map(|m| (m.role.clone(), m.content.clone()))
            .collect()
    }
}

impl Default for ClaudeAdapter {
    fn default() -> Self {
        Self::new(
            std::env::var("ANTHROPIC_API_KEY").unwrap_or_else(|_| "stub-key".to_string()),
            "claude-sonnet-4-20250514".to_string(),
        )
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_adapter_creation() {
        let adapter = ClaudeAdapter::new(
            "test-key".to_string(),
            "claude-sonnet-4-20250514".to_string(),
        );
        assert_eq!(adapter.model, "claude-sonnet-4-20250514");
    }

    #[tokio::test]
    async fn test_text_input() {
        let adapter = ClaudeAdapter::new(
            "test-key".to_string(),
            "claude-sonnet-4-20250514".to_string(),
        );

        adapter.add_text_input("Hello".to_string());

        // Give async task time to complete
        tokio::time::sleep(tokio::time::Duration::from_millis(10)).await;

        let pending = adapter.pending_text.lock().await;
        assert_eq!(*pending, "Hello");
    }

    #[test]
    fn test_system_prompt_contains_key_elements() {
        assert!(CLARA_SYSTEM_PROMPT.contains("Clara"));
        assert!(CLARA_SYSTEM_PROMPT.contains("guided"));
        assert!(CLARA_SYSTEM_PROMPT.contains("Rooms"));
        assert!(CLARA_SYSTEM_PROMPT.contains("Pets"));
        assert!(CLARA_SYSTEM_PROMPT.contains("[ONBOARDING_COMPLETE]"));
    }
}
