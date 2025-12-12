//! Voice Activity Detection
//!
//! Note: This module provides a legacy VAD interface. The actual VAD
//! used in the pipeline is implemented in deterministic.rs with
//! energy + variance analysis.

/// Simple VAD based on energy threshold
pub struct VoiceActivityDetector {
    threshold: f32,
}

impl VoiceActivityDetector {
    pub fn new() -> Self {
        Self { threshold: 0.01 }
    }

    /// Check if audio contains speech-like activity
    pub fn is_speech(&self, audio: &[u8]) -> bool {
        if audio.is_empty() {
            return false;
        }

        // Simple energy check: non-zero bytes above threshold
        let energy = audio.iter().filter(|&&b| b > 0).count() as f32 / audio.len() as f32;

        energy > self.threshold
    }
}

impl Default for VoiceActivityDetector {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_vad_empty() {
        let vad = VoiceActivityDetector::new();
        assert!(!vad.is_speech(&[]));
    }

    #[test]
    fn test_vad_silence() {
        let vad = VoiceActivityDetector::new();
        let silence = vec![0u8; 100];
        assert!(!vad.is_speech(&silence));
    }

    #[test]
    fn test_vad_speech() {
        let vad = VoiceActivityDetector::new();
        let speech = vec![0x01, 0x02, 0x03, 0x04, 0x05];
        assert!(vad.is_speech(&speech));
    }
}

