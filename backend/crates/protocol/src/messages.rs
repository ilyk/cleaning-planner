//! WebSocket message types for Clara streaming protocol

use crate::errors::ErrorCode;
use serde::{Deserialize, Serialize};

/// Audio format specification
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum AudioFormat {
    /// Opus codec at 24kHz
    #[serde(rename = "opus@24000")]
    Opus24k,
    /// PCM 16-bit at 16kHz
    #[serde(rename = "pcm16@16000")]
    Pcm16k,
}

impl AudioFormat {
    pub fn as_str(&self) -> &'static str {
        match self {
            AudioFormat::Opus24k => "opus@24000",
            AudioFormat::Pcm16k => "pcm16@16000",
        }
    }
}

/// Inbound messages from client to server
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum InboundMessage {
    /// Start a turn
    TurnStart {
        turn_id: String,
        session_id: String,
    },

    /// Audio data chunk
    InputAudioDelta {
        /// Monotonic sequence number
        seq: u64,
        /// Audio format
        format: AudioFormat,
        /// Base64-encoded audio data
        data: String,
    },

    /// Commit input audio (end of user speech)
    InputAudioCommit,

    /// Interrupt ongoing output (barge-in)
    InputInterrupt,

    /// Text input from user
    InputText {
        /// The text message
        text: String,
        /// Optional hints for processing
        hints: Option<TextHints>,
    },

    /// Heartbeat ping
    Ping,
}

/// Hints for text processing
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TextHints {
    /// Optional mode hint (e.g., "focus")
    pub mode: Option<String>,
}

/// Outbound messages from server to client
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum OutboundMessage {
    /// Server acknowledges turn start
    TurnStarted {
        turn_id: String,
        policy_version: String,
        prompt_version: String,
    },

    /// Output audio stream starting
    OutputAudioStart {
        format: AudioFormat,
    },

    /// Output audio chunk
    OutputAudioDelta {
        seq: u64,
        format: AudioFormat,
        /// Base64-encoded audio data
        data: String,
    },

    /// Output audio stream complete
    OutputAudioCommit,

    /// Text output delta (transcription or response text)
    OutputTextDelta {
        text: String,
    },

    /// Guardrail notice
    GuardrailNotice {
        code: String,
        message: String,
        categories: Vec<String>,
    },

    /// Server backpressure notification
    ServerBackpressure {
        reason: String,
    },

    /// Turn finished
    TurnFinish {
        turn_id: String,
        metadata: TurnFinishMetadata,
    },

    /// Error message
    Error {
        code: ErrorCode,
        message: String,
        request_id: Option<String>,
    },

    /// Heartbeat pong
    Pong,
}

/// Metadata included in turn finish
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TurnFinishMetadata {
    /// Usage statistics
    pub usage: UsageMetadata,
    /// Latency in milliseconds
    pub latency_ms: u64,
    /// Time to first token (audio) in milliseconds
    pub ttft_ms: Option<u64>,
    /// Policy version used
    pub policy_version: String,
    /// Prompt version used
    pub prompt_version: String,
}

/// Token usage metadata
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UsageMetadata {
    /// Input tokens
    pub tokens_in: u32,
    /// Output tokens
    pub tokens_out: u32,
    /// Input audio seconds
    pub audio_in_seconds: f32,
    /// Output audio seconds
    pub audio_out_seconds: f32,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_inbound_message_serialization() {
        let msg = InboundMessage::TurnStart {
            turn_id: "turn-123".to_string(),
            session_id: "sess-456".to_string(),
        };

        let json = serde_json::to_string(&msg).unwrap();
        let parsed: InboundMessage = serde_json::from_str(&json).unwrap();

        match parsed {
            InboundMessage::TurnStart { turn_id, session_id } => {
                assert_eq!(turn_id, "turn-123");
                assert_eq!(session_id, "sess-456");
            }
            _ => panic!("Wrong variant"),
        }
    }

    #[test]
    fn test_audio_delta_serialization() {
        let msg = InboundMessage::InputAudioDelta {
            seq: 42,
            format: AudioFormat::Opus24k,
            data: "YWJjZGVmZ2g=".to_string(),
        };

        let json = serde_json::to_string(&msg).unwrap();
        assert!(json.contains("\"seq\":42"));
        assert!(json.contains("\"format\":\"opus@24000\""));

        let parsed: InboundMessage = serde_json::from_str(&json).unwrap();
        match parsed {
            InboundMessage::InputAudioDelta { seq, format, data } => {
                assert_eq!(seq, 42);
                assert_eq!(format, AudioFormat::Opus24k);
                assert_eq!(data, "YWJjZGVmZ2g=");
            }
            _ => panic!("Wrong variant"),
        }
    }

    #[test]
    fn test_outbound_error_serialization() {
        let msg = OutboundMessage::Error {
            code: ErrorCode::RateLimited,
            message: "Too many requests".to_string(),
            request_id: Some("req-789".to_string()),
        };

        let json = serde_json::to_string(&msg).unwrap();
        let parsed: OutboundMessage = serde_json::from_str(&json).unwrap();

        match parsed {
            OutboundMessage::Error {
                code,
                message,
                request_id,
            } => {
                assert_eq!(code, ErrorCode::RateLimited);
                assert_eq!(message, "Too many requests");
                assert_eq!(request_id, Some("req-789".to_string()));
            }
            _ => panic!("Wrong variant"),
        }
    }

    #[test]
    fn test_turn_finish_serialization() {
        let msg = OutboundMessage::TurnFinish {
            turn_id: "turn-123".to_string(),
            metadata: TurnFinishMetadata {
                usage: UsageMetadata {
                    tokens_in: 100,
                    tokens_out: 200,
                    audio_in_seconds: 5.5,
                    audio_out_seconds: 3.2,
                },
                latency_ms: 350,
                ttft_ms: Some(320),
                policy_version: "v1.0".to_string(),
                prompt_version: "v2.3".to_string(),
            },
        };

        let json = serde_json::to_string(&msg).unwrap();
        let parsed: OutboundMessage = serde_json::from_str(&json).unwrap();

        match parsed {
            OutboundMessage::TurnFinish { turn_id, metadata } => {
                assert_eq!(turn_id, "turn-123");
                assert_eq!(metadata.usage.tokens_in, 100);
                assert_eq!(metadata.latency_ms, 350);
            }
            _ => panic!("Wrong variant"),
        }
    }
}

