//! Heartbeat management

use std::time::{Duration, Instant};

/// Heartbeat monitor
pub struct HeartbeatMonitor {
    interval: Duration,
    max_missed: u32,
    last_ping: Option<Instant>,
    last_pong: Option<Instant>,
    missed_count: u32,
}

impl HeartbeatMonitor {
    pub fn new(interval: Duration, max_missed: u32) -> Self {
        Self {
            interval,
            max_missed,
            last_ping: None,
            last_pong: None,
            missed_count: 0,
        }
    }

    /// Check if it's time to send a ping
    pub fn should_ping(&self) -> bool {
        match self.last_ping {
            None => true,
            Some(last) => last.elapsed() >= self.interval,
        }
    }

    /// Record that a ping was sent
    pub fn record_ping(&mut self) {
        self.last_ping = Some(Instant::now());
        self.missed_count += 1;
    }

    /// Record that a pong was received
    pub fn record_pong(&mut self) {
        self.last_pong = Some(Instant::now());
        self.missed_count = 0;
    }

    /// Check if connection is dead (too many missed pongs)
    pub fn is_dead(&self) -> bool {
        self.missed_count >= self.max_missed
    }

    /// Get time since last pong
    pub fn time_since_last_pong(&self) -> Option<Duration> {
        self.last_pong.map(|t| t.elapsed())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_heartbeat_monitor() {
        let mut monitor = HeartbeatMonitor::new(Duration::from_secs(10), 3);

        // Should ping initially
        assert!(monitor.should_ping());

        // Record ping
        monitor.record_ping();
        assert_eq!(monitor.missed_count, 1);

        // Record pong
        monitor.record_pong();
        assert_eq!(monitor.missed_count, 0);
        assert!(!monitor.is_dead());
    }

    #[test]
    fn test_heartbeat_dead() {
        let mut monitor = HeartbeatMonitor::new(Duration::from_secs(10), 3);

        // Miss 3 pings
        monitor.record_ping();
        monitor.record_ping();
        monitor.record_ping();

        assert!(monitor.is_dead());
    }
}

