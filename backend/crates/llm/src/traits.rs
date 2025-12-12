//! LLM adapter trait definitions

use anyhow::Result;
use bytes::Bytes;
use serde_json::Value;
use tokio::sync::mpsc::UnboundedReceiver;

/// Events emitted by LLM adapter
#[derive(Debug, Clone)]
pub enum LlmEvent {
    /// Output audio stream starting
    OutputAudioStart,

    /// Output audio chunk
    OutputAudioDelta {
        seq: u64,
        data: Bytes,
        format: String,
    },

    /// Output audio stream complete
    OutputAudioCommit,

    /// Output text chunk
    OutputTextDelta { text: String },

    /// Tool call request
    ToolCall { name: String, args: Value },

    /// Error occurred
    Error { message: String },

    /// Turn finished with usage stats
    Finished { usage_in: u32, usage_out: u32 },
}

/// LLM realtime adapter trait
pub trait LlmRealtime: Send + Sync {
    /// Start a new turn
    fn start_turn(
        &self,
        turn_id: &str,
        policy_version: &str,
        prompt_version: &str,
    ) -> Result<()>;

    /// Send audio chunk to LLM
    fn send_audio_chunk(&self, data: &[u8], format: &str) -> Result<()>;

    /// Commit input audio (end of user speech)
    fn commit_input(&self) -> Result<()>;

    /// Interrupt ongoing output
    fn interrupt(&self) -> Result<()>;

    /// Subscribe to LLM events
    fn subscribe(&self) -> UnboundedReceiver<LlmEvent>;
}

