//! Quarantine ring buffer for audio spans (2-4s sliding window)

use std::collections::VecDeque;
use std::time::{Duration, Instant};

/// Audio frame with metadata
#[derive(Debug, Clone)]
pub struct AudioFrame {
    pub seq: u64,
    pub data: Vec<u8>,
    pub timestamp: Instant,
    pub format: String,
}

/// Quarantine buffer with sliding window
pub struct QuarantineBuffer {
    frames: VecDeque<AudioFrame>,
    max_duration_ms: u64,
    slide_ms: u64,
    sample_rate_hz: u32,
}

impl QuarantineBuffer {
    /// Create a new quarantine buffer
    /// 
    /// # Arguments
    /// * `max_duration_ms` - Maximum span duration in milliseconds (default: 1200)
    /// * `slide_ms` - Slide window size in milliseconds (default: 200)
    /// * `sample_rate_hz` - Audio sample rate (default: 24000)
    pub fn new(max_duration_ms: u64, slide_ms: u64, sample_rate_hz: u32) -> Self {
        Self {
            frames: VecDeque::new(),
            max_duration_ms,
            slide_ms,
            sample_rate_hz,
        }
    }

    /// Push a new frame into the buffer
    pub fn push(&mut self, frame: AudioFrame) {
        self.frames.push_back(frame);
        self.prune_old_frames();
    }

    /// Check if a complete span is ready for processing
    pub fn has_span_ready(&self) -> bool {
        if self.frames.is_empty() {
            return false;
        }

        let span_duration = self.span_duration_ms();
        span_duration >= self.max_duration_ms.min(100) // At least 100ms
    }

    /// Peek at the current span without consuming it
    pub fn peek_span(&self) -> Vec<u8> {
        let duration_limit = Duration::from_millis(self.max_duration_ms);
        let mut span = Vec::new();
        let mut accumulated_duration = Duration::ZERO;

        for frame in &self.frames {
            let frame_dur = Self::frame_duration(&frame.data);
            if accumulated_duration + frame_dur > duration_limit {
                break;
            }
            accumulated_duration += frame_dur;
            span.extend_from_slice(&frame.data);
        }

        span
    }

    /// Consume the oldest frames up to slide window
    pub fn consume_slide(&mut self) {
        if self.frames.is_empty() {
            return;
        }

        let slide_duration = Duration::from_millis(self.slide_ms);
        let mut to_remove = 0;
        let mut accumulated = Duration::ZERO;

        for frame in &self.frames {
            if accumulated >= slide_duration {
                break;
            }
            accumulated += Self::frame_duration(&frame.data);
            to_remove += 1;
        }

        for _ in 0..to_remove {
            self.frames.pop_front();
        }
    }

    /// Remove frames from a specific range (for masking)
    pub fn mask_range(&mut self, start_ms: u64, end_ms: u64) {
        let start_duration = Duration::from_millis(start_ms);
        let end_duration = Duration::from_millis(end_ms);
        let mut accumulated = Duration::ZERO;
        let mut to_clear = Vec::new();

        for (idx, frame) in self.frames.iter_mut().enumerate() {
            let frame_dur = Self::frame_duration(&frame.data);

            if accumulated >= start_duration && accumulated < end_duration {
                // Zero out frame data (mask)
                frame.data.fill(0);
                to_clear.push(idx);
            }
            
            accumulated += frame_dur;
            if accumulated >= end_duration {
                break;
            }
        }
    }

    /// Get span duration in milliseconds
    pub fn span_duration_ms(&self) -> u64 {
        let total_duration: Duration = self.frames.iter()
            .map(|f| Self::frame_duration(&f.data))
            .sum();
        total_duration.as_millis() as u64
    }

    /// Clear all frames
    pub fn clear(&mut self) {
        self.frames.clear();
    }

    /// Get number of frames
    pub fn len(&self) -> usize {
        self.frames.len()
    }

    /// Check if buffer is empty
    pub fn is_empty(&self) -> bool {
        self.frames.is_empty()
    }

    /// Remove frames older than max_duration
    fn prune_old_frames(&mut self) {
        let max_duration = Duration::from_millis(self.max_duration_ms * 2); // Keep 2x buffer
        
        while let Some(front) = self.frames.front() {
            if front.timestamp.elapsed() > max_duration {
                self.frames.pop_front();
            } else {
                break;
            }
        }
    }

    /// Estimate frame duration based on data size
    fn frame_duration(data: &[u8]) -> Duration {
        // For Opus @ 24kHz: ~480 samples per 20ms frame
        // Rough estimate: ~40 bytes per ms for compressed Opus
        let estimated_ms = data.len() as u64 / 40;
        Duration::from_millis(estimated_ms.max(20)) // Min 20ms
    }
}

impl Default for QuarantineBuffer {
    fn default() -> Self {
        Self::new(1200, 200, 24000)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_quarantine_push_and_peek() {
        let mut buffer = QuarantineBuffer::new(1000, 200, 24000);
        
        let frame = AudioFrame {
            seq: 1,
            data: vec![0x01; 100],
            timestamp: Instant::now(),
            format: "opus@24000".to_string(),
        };
        
        buffer.push(frame);
        assert!(!buffer.is_empty());
        assert_eq!(buffer.len(), 1);
    }

    #[test]
    fn test_quarantine_has_span_ready() {
        let mut buffer = QuarantineBuffer::new(1000, 200, 24000);
        
        // Push enough data to exceed minimum span
        for i in 0..10 {
            let frame = AudioFrame {
                seq: i,
                data: vec![0x01; 500], // Large frames
                timestamp: Instant::now(),
                format: "opus@24000".to_string(),
            };
            buffer.push(frame);
        }
        
        // Should have enough data for a span
        assert!(buffer.has_span_ready());
        
        let span = buffer.peek_span();
        assert!(!span.is_empty());
    }

    #[test]
    fn test_quarantine_mask_range() {
        let mut buffer = QuarantineBuffer::new(1000, 200, 24000);
        
        for i in 0..5 {
            let frame = AudioFrame {
                seq: i,
                data: vec![0xFF; 200],
                timestamp: Instant::now(),
                format: "opus@24000".to_string(),
            };
            buffer.push(frame);
        }
        
        buffer.mask_range(100, 300);
        
        // Check that frames in range are zeroed
        let span = buffer.peek_span();
        // Some frames should be masked
        assert!(span.iter().any(|&b| b == 0));
    }
}


