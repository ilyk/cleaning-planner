//! WebSocket handler

use base64::Engine;
use crate::{heartbeat::HeartbeatMonitor, turn_executor::TurnExecutor, validation::*};
use axum::extract::ws::{Message, WebSocket};
use clara_config::AppConfig;
use clara_llm;
use clara_protocol::{InboundMessage, OutboundMessage};
use clara_session::SessionManager;
use clara_store::Store;
use clara_telemetry::Metrics;
use futures::{stream::SplitSink, SinkExt, StreamExt};
use std::sync::Arc;
use tokio::sync::mpsc;
use tracing;

/// Log error for WebSocket (before split, can't send messages)
fn log_ws_error(error: &str) {
    tracing::error!(error = error, "WebSocket error (pre-split)");
}

/// Handle WebSocket connection
pub async fn handle_websocket(
    socket: WebSocket,
    session_id: String,
    turn_id: String,
    session_manager: Arc<SessionManager>,
    config: Arc<AppConfig>,
    store: Store,
    metrics: Arc<Metrics>,
    home_id: String,
) {
    tracing::info!(
        session_id = session_id,
        turn_id = turn_id,
        "WebSocket connection established"
    );

    metrics.ws_connections.inc();

    // Mark session as connected
    if let Err(e) = session_manager.mark_connected(&session_id).await {
        tracing::error!(error = %e, "Failed to mark session as connected");
        metrics.ws_connections.dec();
        return;
    }

    // Create LLM adapter
    let llm_config = clara_llm::LlmConfig {
        provider: config.llm.provider.clone(),
        openai_api_key: config.llm.openai_api_key.clone(),
        openai_model: config.llm.openai_model.clone(),
        anthropic_api_key: config.llm.anthropic_api_key.clone(),
        anthropic_model: config.llm.anthropic_model.clone(),
    };
    let use_mock = config.llm.provider == "mock"
        || (!config.features.openai_realtime && config.llm.provider == "openai");
    let llm = clara_llm::create_adapter(&llm_config, use_mock);

    let llm = match llm {
        Ok(adapter) => adapter,
        Err(e) => {
            tracing::error!(error = %e, "Failed to create LLM adapter");
            metrics.ws_connections.dec();
            let _ = session_manager.mark_disconnected(&session_id).await;
            return;
        }
    };

    // Create turn executor
    let mut executor = TurnExecutor::new(
        turn_id.clone(),
        config.versions.guardrail_policy_version.clone(),
        config.versions.prompt_version.clone(),
        llm,
        store,
        home_id,
        metrics.clone(),
    );

    // Start turn
    if let Err(e) = executor.start() {
        tracing::error!(error = %e, "Failed to start turn");
        log_ws_error("Failed to start turn");
        metrics.ws_connections.dec();
        let _ = session_manager.mark_disconnected(&session_id).await;
        return;
    }

    // Prepare turn started message (will be sent first in message loop)
    let turn_started_msg = OutboundMessage::TurnStarted {
        turn_id: turn_id.clone(),
        policy_version: config.versions.guardrail_policy_version.clone(),
        prompt_version: config.versions.prompt_version.clone(),
    };

    // Run message loop (it will send turn_started as first message)
    let result = message_loop_with_initial(socket, &mut executor, &config, session_id.clone(), turn_started_msg).await;

    // Cleanup
    metrics.ws_connections.dec();
    if let Err(e) = session_manager.mark_disconnected(&session_id).await {
        tracing::error!(error = %e, "Failed to mark session as disconnected");
    }

    if let Err(e) = result {
        tracing::error!(
            session_id = session_id,
            turn_id = turn_id,
            error = %e,
            "WebSocket handler error"
        );
        metrics
            .errors_total
            .with_label_values(&["WS_HANDLER_ERROR"])
            .inc();
    }

    tracing::info!(
        session_id = session_id,
        turn_id = turn_id,
        "WebSocket connection closed"
    );
}

async fn message_loop_with_initial(
    socket: WebSocket,
    executor: &mut TurnExecutor,
    config: &AppConfig,
    _session_id: String,
    initial_message: OutboundMessage,
) -> anyhow::Result<()> {
    // Split socket into sender and receiver
    let (mut sender, mut receiver) = socket.split();

    let mut seq_validator = SequenceValidator::new();
    let mut heartbeat = HeartbeatMonitor::new(
        config.heartbeat_interval(),
        config.server.max_missed_heartbeats,
    );

    // Subscribe to LLM events
    let mut llm_events = executor.subscribe_llm_events();

    // Create channel for sending messages from handler to sender task
    let (tx, mut rx) = mpsc::unbounded_channel::<OutboundMessage>();

    // Spawn sender task
    let sender_handle = tokio::spawn(async move {
        // Send initial message first
        if let Err(e) = send_message_to_sink(&mut sender, &initial_message).await {
            tracing::error!(error = %e, "Failed to send initial message");
            let _ = sender.close().await;
            return;
        }

        // Then handle all subsequent messages
        while let Some(msg) = rx.recv().await {
            if let Err(e) = send_message_to_sink(&mut sender, &msg).await {
                tracing::error!(error = %e, "Failed to send message");
                break;
            }

            // Check if we should close after this message
            if matches!(msg, OutboundMessage::TurnFinish { .. }) {
                tracing::debug!("Turn finished, closing sender");
                break;
            }
        }
        
        // Close the sender
        let _ = sender.close().await;
    });

    // Main receive loop
    let result = async {
        loop {
            tokio::select! {
                // Receive from client
                Some(msg_result) = receiver.next() => {
                    match msg_result {
                        Ok(Message::Text(text)) => {
                            match serde_json::from_str::<InboundMessage>(&text) {
                                Ok(inbound) => {
                                    if let Err(e) = handle_inbound_message(
                                        inbound,
                                        executor,
                                        &mut seq_validator,
                                        &mut heartbeat,
                                        config.server.max_frame_size,
                                    ).await {
                                        tracing::error!(error = %e, "Failed to handle inbound message");
                                        let error_msg = OutboundMessage::Error {
                                            code: clara_protocol::ErrorCode::InternalError,
                                            message: e.to_string(),
                                            request_id: None,
                                        };
                                        tx.send(error_msg).ok();
                                    }
                                }
                                Err(e) => {
                                    tracing::warn!(error = %e, "Failed to parse message");
                                    let error_msg = OutboundMessage::Error {
                                        code: clara_protocol::ErrorCode::InvalidRequest,
                                        message: format!("Invalid message: {}", e),
                                        request_id: None,
                                    };
                                    tx.send(error_msg).ok();
                                }
                            }
                        }
                        Ok(Message::Close(_)) => {
                            tracing::info!("Client closed connection");
                            break;
                        }
                        Err(e) => {
                            tracing::error!(error = %e, "WebSocket error");
                            break;
                        }
                        _ => {}
                    }
                }

                // Forward LLM events to sender
                Some(outbound) = llm_events.recv() => {
                    let is_finish = matches!(outbound, OutboundMessage::TurnFinish { .. });
                    tx.send(outbound).ok();
                    
                    if is_finish {
                        break;
                    }
                }

                // Heartbeat
                _ = tokio::time::sleep(config.heartbeat_interval()) => {
                    if heartbeat.should_ping() {
                        tx.send(OutboundMessage::Pong).ok();
                        heartbeat.record_ping();

                        if heartbeat.is_dead() {
                            tracing::warn!("Heartbeat timeout, closing connection");
                            break;
                        }
                    }
                }
            }
        }
        Ok::<(), anyhow::Error>(())
    }.await;

    // Drop tx to signal sender task to finish
    drop(tx);

    // Wait for sender to finish
    let _ = sender_handle.await;

    result
}

async fn handle_inbound_message(
    msg: InboundMessage,
    executor: &mut TurnExecutor,
    seq_validator: &mut SequenceValidator,
    heartbeat: &mut HeartbeatMonitor,
    max_frame_size: usize,
) -> anyhow::Result<()> {
    match msg {
        InboundMessage::TurnStart { .. } => {
            // Already started
        }
        InboundMessage::InputAudioDelta { seq, format, data } => {
            // Validate sequence
            seq_validator.validate(seq)?;

            // Validate format
            validate_audio_format(format.as_str())?;

            // Decode base64
            let audio_data = base64::engine::general_purpose::STANDARD
                .decode(&data)
                .map_err(|e| anyhow::anyhow!("Base64 decode error: {}", e))?;

            // Validate payload size
            validate_payload_size(&audio_data, max_frame_size)?;

            // Process through executor
            executor.process_audio(&audio_data, format.as_str())?;
        }
        InboundMessage::InputAudioCommit => {
            executor.commit_input()?;
            seq_validator.reset();
        }
        InboundMessage::InputInterrupt => {
            let interrupt_start = executor.interrupt()?;
            let stop_time = interrupt_start.elapsed();

            tracing::info!(stop_ms = stop_time.as_millis(), "Barge-in stop time");

            // Record barge-in stop time
            // metrics.barge_in_stop_ms.observe(stop_time.as_millis() as f64);
        }
        InboundMessage::Ping => {
            heartbeat.record_pong();
        }
    }

    Ok(())
}

async fn send_message_to_sink(
    sender: &mut SplitSink<WebSocket, Message>,
    msg: &OutboundMessage,
) -> anyhow::Result<()> {
    let json = serde_json::to_string(msg)?;
    let ws_msg = Message::Text(json);
    sender.send(ws_msg).await?;
    
    tracing::debug!(message_type = ?msg, "Sent message");
    
    Ok(())
}

async fn send_initial_message(socket: &WebSocket, msg: &OutboundMessage) -> anyhow::Result<()> {
    // For initial messages before split, we need a different approach
    // This is used only for the turn_started message
    let json = serde_json::to_string(msg)?;
    tracing::debug!(message_type = ?msg, "Initial message prepared");
    // We'll send this after split in a refactored version
    let _ = json; // Suppress warning for now
    Ok(())
}

