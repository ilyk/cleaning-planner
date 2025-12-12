//! Mock LLM adapter for testing

use crate::traits::{LlmEvent, LlmRealtime};
use anyhow::Result;
use bytes::Bytes;
use std::sync::{Arc, Mutex};
use tokio::sync::mpsc::{unbounded_channel, UnboundedReceiver, UnboundedSender};

/// Mock LLM adapter that simulates responses
pub struct MockLlmAdapter {
    sender: Arc<Mutex<Option<UnboundedSender<LlmEvent>>>>,
    audio_seq: Arc<Mutex<u64>>,
}

impl MockLlmAdapter {
    pub fn new() -> Self {
        Self {
            sender: Arc::new(Mutex::new(None)),
            audio_seq: Arc::new(Mutex::new(0)),
        }
    }

    /// Simulate audio output
    fn simulate_output(&self) -> Result<()> {
        let sender = self.sender.lock().unwrap();
        if let Some(tx) = sender.as_ref() {
            // Send start
            tx.send(LlmEvent::OutputAudioStart).ok();

            // Send some audio chunks
            for i in 0..5 {
                let mut seq = self.audio_seq.lock().unwrap();
                *seq += 1;

                let data = Bytes::from(vec![0x00, 0x01, 0x02, i]);
                tx.send(LlmEvent::OutputAudioDelta {
                    seq: *seq,
                    data,
                    format: "opus@24000".to_string(),
                })
                .ok();
            }

            // Send text
            tx.send(LlmEvent::OutputTextDelta {
                text: "Mock response".to_string(),
            })
            .ok();

            // Send commit
            tx.send(LlmEvent::OutputAudioCommit).ok();

            // Send finish
            tx.send(LlmEvent::Finished {
                usage_in: 100,
                usage_out: 50,
            })
            .ok();
        }

        Ok(())
    }
}

impl Default for MockLlmAdapter {
    fn default() -> Self {
        Self::new()
    }
}

impl LlmRealtime for MockLlmAdapter {
    fn start_turn(
        &self,
        turn_id: &str,
        _policy_version: &str,
        _prompt_version: &str,
    ) -> Result<()> {
        tracing::info!(turn_id = turn_id, "Mock LLM: Turn started");
        *self.audio_seq.lock().unwrap() = 0;
        Ok(())
    }

    fn send_audio_chunk(&self, data: &[u8], format: &str) -> Result<()> {
        tracing::debug!(
            size = data.len(),
            format = format,
            "Mock LLM: Received audio chunk"
        );
        Ok(())
    }

    fn commit_input(&self) -> Result<()> {
        tracing::info!("Mock LLM: Input committed");

        // Simulate response generation
        self.simulate_output()?;

        Ok(())
    }

    fn interrupt(&self) -> Result<()> {
        tracing::info!("Mock LLM: Interrupted");

        // Send commit immediately
        let sender = self.sender.lock().unwrap();
        if let Some(tx) = sender.as_ref() {
            tx.send(LlmEvent::OutputAudioCommit).ok();
        }

        Ok(())
    }

    fn subscribe(&self) -> UnboundedReceiver<LlmEvent> {
        let (tx, rx) = unbounded_channel();
        *self.sender.lock().unwrap() = Some(tx);
        rx
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_mock_adapter() {
        let adapter = MockLlmAdapter::new();
        let mut rx = adapter.subscribe();

        adapter
            .start_turn("turn-123", "policy-v1", "prompt-v1")
            .unwrap();
        adapter
            .send_audio_chunk(&[0x01, 0x02, 0x03], "opus@24000")
            .unwrap();
        adapter.commit_input().unwrap();

        // Receive events
        let mut events = Vec::new();
        while let Ok(event) = rx.try_recv() {
            events.push(event);
        }

        // Should have start, deltas, text, commit, finished
        assert!(!events.is_empty());
    }
}

