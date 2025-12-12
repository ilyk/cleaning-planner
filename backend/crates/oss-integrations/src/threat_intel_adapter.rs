//! Threat Intel integration for guardrails

use anyhow::Result;
use std::collections::HashSet;

/// Threat intelligence provider trait
pub trait ThreatIntelProvider: Send + Sync {
    /// Check if token/phrase is known bad
    fn is_known_bad_token(&self, token: &str) -> Result<bool>;

    /// Get reputation score for IP
    fn get_ip_reputation(&self, ip: &str) -> Result<ReputationScore>;

    /// Update keyword lexicons
    fn update_lexicons(&self) -> Result<LexiconUpdate>;

    /// Check if IP should be auto-blocked
    fn should_block_ip(&self, ip: &str) -> Result<bool>;

    /// Report malicious activity
    fn report_activity(&self, activity: ThreatActivity) -> Result<()>;
}

#[derive(Debug, Clone)]
pub struct ReputationScore {
    pub score: f32,         // 0.0 (bad) to 1.0 (good)
    pub known_threats: u32,
    pub last_seen: Option<i64>,
}

#[derive(Debug, Clone)]
pub struct LexiconUpdate {
    pub added_tokens: HashSet<String>,
    pub removed_tokens: HashSet<String>,
    pub version: String,
}

#[derive(Debug, Clone)]
pub struct ThreatActivity {
    pub ip: String,
    pub activity_type: ActivityType,
    pub severity: Severity,
    pub metadata: serde_json::Value,
}

#[derive(Debug, Clone)]
pub enum ActivityType {
    RateLimit,
    GuardrailBlock,
    SuspiciousPattern,
    PolicyViolation,
}

#[derive(Debug, Clone)]
pub enum Severity {
    Low,
    Medium,
    High,
    Critical,
}

/// Mock implementation
#[derive(Clone)]
pub struct MockThreatIntelProvider {
    known_bad_tokens: HashSet<String>,
}

impl MockThreatIntelProvider {
    pub fn new() -> Self {
        let mut known_bad_tokens = HashSet::new();
        known_bad_tokens.insert("badword1".to_string());
        known_bad_tokens.insert("threat".to_string());

        Self { known_bad_tokens }
    }
}

impl Default for MockThreatIntelProvider {
    fn default() -> Self {
        Self::new()
    }
}

impl ThreatIntelProvider for MockThreatIntelProvider {
    fn is_known_bad_token(&self, token: &str) -> Result<bool> {
        let is_bad = self.known_bad_tokens.contains(token);
        
        if is_bad {
            tracing::warn!(token = token, "Known bad token detected");
        }
        
        Ok(is_bad)
    }

    fn get_ip_reputation(&self, ip: &str) -> Result<ReputationScore> {
        tracing::debug!(ip = ip, "Mock: Getting IP reputation");

        Ok(ReputationScore {
            score: 0.8,     // Good by default
            known_threats: 0,
            last_seen: None,
        })
    }

    fn update_lexicons(&self) -> Result<LexiconUpdate> {
        tracing::info!("Mock: Updating threat lexicons");

        Ok(LexiconUpdate {
            added_tokens: HashSet::new(),
            removed_tokens: HashSet::new(),
            version: "mock-v1".to_string(),
        })
    }

    fn should_block_ip(&self, ip: &str) -> Result<bool> {
        let score = self.get_ip_reputation(ip)?;
        Ok(score.score < 0.3) // Block if reputation is very low
    }

    fn report_activity(&self, activity: ThreatActivity) -> Result<()> {
        tracing::warn!(
            ip = activity.ip,
            activity_type = ?activity.activity_type,
            severity = ?activity.severity,
            "Mock: Reporting threat activity"
        );
        Ok(())
    }
}

#[cfg(feature = "use-threat-intel")]
/// Real implementation using threat-intel crate
pub struct RealThreatIntelProvider {
    client: threat_intel::ThreatClient,
}

#[cfg(feature = "use-threat-intel")]
impl RealThreatIntelProvider {
    pub fn new() -> Result<Self> {
        Ok(Self {
            client: threat_intel::ThreatClient::new()?,
        })
    }
}

#[cfg(feature = "use-threat-intel")]
impl ThreatIntelProvider for RealThreatIntelProvider {
    fn is_known_bad_token(&self, token: &str) -> Result<bool> {
        tracing::trace!(token_len = token.len(), "Checking if token is known bad");

        let is_bad = self.client.is_bad_token(token)?;
        if is_bad {
            tracing::warn!(token = token, "Known bad token detected");
        }

        Ok(is_bad)
    }

    fn get_ip_reputation(&self, ip: &str) -> Result<ReputationScore> {
        tracing::debug!(ip = ip, "Getting IP reputation");

        let rep = self.client.get_reputation(ip)?;

        Ok(ReputationScore {
            score: rep.score,
            known_threats: rep.threat_count,
            last_seen: rep.last_seen_timestamp,
        })
    }

    fn update_lexicons(&self) -> Result<LexiconUpdate> {
        tracing::info!("Updating threat lexicons from feed");

        let update = self.client.sync_lexicons()?;

        Ok(LexiconUpdate {
            added_tokens: update.added.into_iter().collect(),
            removed_tokens: update.removed.into_iter().collect(),
            version: update.version,
        })
    }

    fn should_block_ip(&self, ip: &str) -> Result<bool> {
        let reputation = self.get_ip_reputation(ip)?;

        // Block if score is very low (< 0.3) or has many known threats
        let should_block = reputation.score < 0.3 || reputation.known_threats > 5;

        if should_block {
            tracing::warn!(
                ip = ip,
                score = reputation.score,
                threats = reputation.known_threats,
                "IP should be blocked"
            );
        }

        Ok(should_block)
    }

    fn report_activity(&self, activity: ThreatActivity) -> Result<()> {
        tracing::warn!(
            ip = activity.ip,
            activity_type = ?activity.activity_type,
            severity = ?activity.severity,
            "Reporting threat activity"
        );

        let ti_activity = threat_intel::Activity {
            ip: activity.ip,
            activity_type: format!("{:?}", activity.activity_type),
            severity: format!("{:?}", activity.severity),
            metadata: activity.metadata.to_string(),
        };

        self.client.report_activity(ti_activity)?;
        Ok(())
    }
}

/// Factory function
pub fn create_provider() -> Result<Box<dyn ThreatIntelProvider>> {
    #[cfg(feature = "use-threat-intel")]
    {
        tracing::info!("Using real threat-intel provider");
        Ok(Box::new(RealThreatIntelProvider::new()?))
    }

    #[cfg(not(feature = "use-threat-intel"))]
    {
        tracing::debug!("Using mock threat-intel provider");
        Ok(Box::new(MockThreatIntelProvider::new()))
    }
}

