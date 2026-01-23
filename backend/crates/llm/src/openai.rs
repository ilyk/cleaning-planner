//! OpenAI Realtime API adapter (production implementation)

use crate::traits::{LlmEvent, LlmRealtime};
use anyhow::{Context, Result};
use bytes::Bytes;
use futures::{SinkExt, StreamExt};
use serde_json::Value;
use std::sync::Arc;
use tokio::sync::mpsc::{unbounded_channel, UnboundedReceiver, UnboundedSender};
use tokio::sync::Mutex;
use tokio_tungstenite::{connect_async, tungstenite::Message};
use tracing::{debug, error, info, warn};
use url::Url;


/// OpenAI Realtime adapter
pub struct OpenAiRealtimeAdapter {
    api_key: String,
    model: String,
    base_url: String,
    event_sender: Arc<Mutex<Option<UnboundedSender<LlmEvent>>>>,
    write_sender: Arc<Mutex<Option<UnboundedSender<Message>>>>,
    current_turn_id: Arc<Mutex<Option<String>>>,
}

impl OpenAiRealtimeAdapter {
    pub fn new(api_key: String, model: String) -> Self {
        Self {
            api_key,
            model,
            base_url: "wss://api.openai.com/v1/realtime".to_string(),
            event_sender: Arc::new(Mutex::new(None)),
            write_sender: Arc::new(Mutex::new(None)),
            current_turn_id: Arc::new(Mutex::new(None)),
        }
    }

    /// Create WebSocket connection and event loop
    async fn connect_and_run(&self, turn_id: String) -> Result<()> {
        let mut url = Url::parse(&self.base_url)?;
        url.set_query(Some(&format!("model={}", self.model)));
        
        // Add authorization header
        let auth_header = format!("Bearer {}", self.api_key);
        
        let (ws_stream, _) = connect_async(url.as_str()).await
            .context("Failed to connect to OpenAI Realtime API")?;

        let (write, mut read) = ws_stream.split();

        // Channel for sending messages to WebSocket
        let (write_tx, mut write_rx) = unbounded_channel::<Message>();
        *self.write_sender.lock().await = Some(write_tx.clone());
        
        // Create event channel
        let (tx, _rx) = unbounded_channel::<LlmEvent>();
        *self.event_sender.lock().await = Some(tx.clone());
        *self.current_turn_id.lock().await = Some(turn_id.clone());

        // Spawn writer task
        let mut write_handle = write;
        tokio::spawn(async move {
            while let Some(msg) = write_rx.recv().await {
                if let Err(e) = write_handle.send(msg).await {
                    error!(error = %e, "Failed to send WebSocket message");
                    break;
                }
            }
        });

        // Send session configuration
        let session_msg = serde_json::json!({
            "type": "session.update",
            "session": {
                "modalities": ["audio", "text"],
                "voice": "alloy",
                "instructions": "You are Clara, a helpful voice assistant for family task planning.",
                "model": self.model,
            }
        });

        write_tx.send(Message::Text(session_msg.to_string()))?;
        info!(turn_id = turn_id, "Connected to OpenAI Realtime API");

        // Spawn event processing task
        let event_sender = self.event_sender.clone();
        tokio::spawn(async move {
            let mut seq = 0u64;
            while let Some(msg) = read.next().await {
                match msg {
                    Ok(Message::Text(text)) => {
                        if let Err(e) = Self::process_message(&text, &event_sender, &mut seq).await {
                            error!(error = %e, "Error processing OpenAI message");
                        }
                    }
                    Ok(Message::Close(_)) => {
                        info!("OpenAI WebSocket closed");
                        break;
                    }
                    Err(e) => {
                        error!(error = %e, "WebSocket error");
                        break;
                    }
                    _ => {}
                }
            }
        });

        Ok(())
    }

    async fn process_message(
        text: &str,
        event_sender: &Arc<Mutex<Option<UnboundedSender<LlmEvent>>>>,
        seq: &mut u64,
    ) -> Result<()> {
        let msg: Value = serde_json::from_str(text)
            .context("Failed to parse OpenAI message")?;

        let msg_type = msg.get("type").and_then(|v| v.as_str()).unwrap_or("");

        let sender = event_sender.lock().await;
        if let Some(ref tx) = *sender {
            match msg_type {
                "response.created" => {
                    debug!("OpenAI response started");
                    let _ = tx.send(LlmEvent::OutputAudioStart);
                }
                "response.audio_transcript.delta" => {
                    if let Some(delta) = msg.get("delta").and_then(|v| v.as_str()) {
                        let _ = tx.send(LlmEvent::OutputTextDelta {
                            text: delta.to_string(),
                        });
                    }
                }
                "response.audio.delta" => {
                    if let Some(delta) = msg.get("delta").and_then(|v| v.as_str()) {
                        let data = base64::decode(delta)
                            .context("Failed to decode audio delta")?;
                        *seq += 1;
                        let _ = tx.send(LlmEvent::OutputAudioDelta {
                            seq: *seq,
                            data: Bytes::from(data),
                            format: "pcm16@24000".to_string(),
                        });
                    }
                }
                "response.audio.done" => {
                    debug!("OpenAI audio stream complete");
                    let _ = tx.send(LlmEvent::OutputAudioCommit);
                }
                "response.done" => {
                    let usage_in = msg
                        .get("usage")
                        .and_then(|u| u.get("input_tokens"))
                        .and_then(|v| v.as_u64())
                        .unwrap_or(0) as u32;
                    let usage_out = msg
                        .get("usage")
                        .and_then(|u| u.get("output_tokens"))
                        .and_then(|v| v.as_u64())
                        .unwrap_or(0) as u32;

                    debug!(
                        usage_in = usage_in,
                        usage_out = usage_out,
                        "OpenAI turn finished"
                    );
                    let _ = tx.send(LlmEvent::Finished {
                        usage_in,
                        usage_out,
                    });
                }
                "error" => {
                    let error_msg = msg
                        .get("error")
                        .and_then(|e| e.get("message"))
                        .and_then(|v| v.as_str())
                        .unwrap_or("Unknown error");
                    warn!(error = error_msg, "OpenAI API error");
                    let _ = tx.send(LlmEvent::Error {
                        message: error_msg.to_string(),
                    });
                }
                _ => {
                    debug!(msg_type = msg_type, "Unhandled OpenAI message type");
                }
            }
        }

        Ok(())
    }
}

impl LlmRealtime for OpenAiRealtimeAdapter {
    fn start_turn(
        &self,
        turn_id: &str,
        _policy_version: &str,
        _prompt_version: &str,
    ) -> Result<()> {
        info!(turn_id = turn_id, "Starting OpenAI Realtime turn");

        // Clone for async move
        let adapter = OpenAiRealtimeAdapter {
            api_key: self.api_key.clone(),
            model: self.model.clone(),
            base_url: self.base_url.clone(),
            event_sender: self.event_sender.clone(),
            write_sender: self.write_sender.clone(),
            current_turn_id: self.current_turn_id.clone(),
        };

        let turn_id = turn_id.to_string();
        tokio::spawn(async move {
            if let Err(e) = adapter.connect_and_run(turn_id).await {
                error!(error = %e, "Failed to start OpenAI turn");
            }
        });

        Ok(())
    }

    fn send_audio_chunk(&self, data: &[u8], format: &str) -> Result<()> {
        debug!(
            size = data.len(),
            format = format,
            "Sending audio chunk to OpenAI"
        );

        // Base64 encode audio
        let audio_b64 = base64::encode(data);
        let msg = serde_json::json!({
            "type": "input_audio_buffer.append",
            "audio": audio_b64,
        });

        // Send through WebSocket (non-blocking)
        let write_sender = self.write_sender.clone();
        let msg_text = msg.to_string();
        tokio::spawn(async move {
            let guard = write_sender.lock().await;
            if let Some(ref tx) = *guard {
                let _ = tx.send(Message::Text(msg_text.into()));
            }
        });

        Ok(())
    }

    fn send_text(&self, text: &str) -> Result<()> {
        info!(text_len = text.len(), "OpenAI adapter: Received text input (not implemented)");
        // OpenAI realtime API is audio-focused, text input would need a different approach
        Ok(())
    }

    fn commit_input(&self) -> Result<()> {
        info!("Committing input to OpenAI");

        let msg = serde_json::json!({
            "type": "input_audio_buffer.commit",
        });

        // Send through WebSocket (non-blocking)
        let write_sender = self.write_sender.clone();
        let msg_text = msg.to_string();
        tokio::spawn(async move {
            let guard = write_sender.lock().await;
            if let Some(ref tx) = *guard {
                let _ = tx.send(Message::Text(msg_text.into()));
            }
        });

        Ok(())
    }

    fn interrupt(&self) -> Result<()> {
        info!("Interrupting OpenAI output");

        let msg = serde_json::json!({
            "type": "response.cancel",
        });

        // Send through WebSocket (non-blocking)
        let write_sender = self.write_sender.clone();
        let msg_text = msg.to_string();
        tokio::spawn(async move {
            let guard = write_sender.lock().await;
            if let Some(ref tx) = *guard {
                let _ = tx.send(Message::Text(msg_text.into()));
            }
        });

        Ok(())
    }

    fn subscribe(&self) -> UnboundedReceiver<LlmEvent> {
        let (tx, rx) = unbounded_channel();
        
        // Store sender for event loop
        let sender = self.event_sender.clone();
        tokio::spawn(async move {
            *sender.lock().await = Some(tx);
        });

        rx
    }
}

impl Default for OpenAiRealtimeAdapter {
    fn default() -> Self {
        Self::new(
            std::env::var("OPENAI_API_KEY").unwrap_or_else(|_| "stub-key".to_string()),
            "gpt-5".to_string(),
        )
    }
}
