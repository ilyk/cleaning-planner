//! Message validation

use cleanflow_protocol::ProtocolError;
use anyhow::Result;

/// Sequence validator
pub struct SequenceValidator {
    expected_seq: u64,
}

impl SequenceValidator {
    pub fn new() -> Self {
        Self { expected_seq: 0 }
    }

    /// Validate and update sequence number
    pub fn validate(&mut self, seq: u64) -> Result<(), ProtocolError> {
        if seq != self.expected_seq {
            return Err(ProtocolError::SeqOutOfOrder {
                expected: self.expected_seq,
                actual: seq,
            });
        }

        self.expected_seq += 1;
        Ok(())
    }

    /// Reset sequence counter
    pub fn reset(&mut self) {
        self.expected_seq = 0;
    }
}

impl Default for SequenceValidator {
    fn default() -> Self {
        Self::new()
    }
}

/// Validate payload size
pub fn validate_payload_size(data: &[u8], max_size: usize) -> Result<(), ProtocolError> {
    if data.len() > max_size {
        return Err(ProtocolError::PayloadTooLarge {
            size: data.len(),
            max: max_size,
        });
    }
    Ok(())
}

/// Validate audio format
pub fn validate_audio_format(format: &str) -> Result<(), ProtocolError> {
    match format {
        "opus@24000" | "pcm16@16000" => Ok(()),
        _ => Err(ProtocolError::InvalidFormat(format!("Unsupported audio format: {}", format))),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_sequence_validation() {
        let mut validator = SequenceValidator::new();

        assert!(validator.validate(0).is_ok());
        assert!(validator.validate(1).is_ok());
        assert!(validator.validate(2).is_ok());

        // Out of order
        assert!(validator.validate(5).is_err());
    }

    #[test]
    fn test_payload_size_validation() {
        let data = vec![0u8; 100];
        assert!(validate_payload_size(&data, 200).is_ok());
        assert!(validate_payload_size(&data, 50).is_err());
    }

    #[test]
    fn test_audio_format_validation() {
        assert!(validate_audio_format("opus@24000").is_ok());
        assert!(validate_audio_format("pcm16@16000").is_ok());
        assert!(validate_audio_format("invalid").is_err());
    }
}

