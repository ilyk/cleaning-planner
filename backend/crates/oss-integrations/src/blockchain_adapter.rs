//! Blockchain Runtime integration for audit trails

use anyhow::Result;
use serde::{Deserialize, Serialize};

/// Audit record for blockchain anchoring
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AuditRecord {
    pub record_type: AuditRecordType,
    pub content_hash: String,
    pub timestamp: i64,
    pub metadata: serde_json::Value,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum AuditRecordType {
    PolicyVersion,
    PromptVersion,
    PlanDiff,
    ToolExecution,
}

/// Blockchain auditor trait
pub trait BlockchainAuditor: Send + Sync {
    /// Anchor a hash asynchronously (write-behind, never blocks)
    fn anchor_async(&self, record: AuditRecord) -> Result<String>;

    /// Verify an anchored record
    fn verify_anchor(&self, record_id: &str) -> Result<bool>;

    /// Get audit trail for a resource
    fn get_audit_trail(&self, resource_id: &str) -> Result<Vec<AuditRecord>>;
}

/// Mock implementation
#[derive(Clone)]
pub struct MockBlockchainAuditor;

impl MockBlockchainAuditor {
    pub fn new() -> Self {
        Self
    }
}

impl Default for MockBlockchainAuditor {
    fn default() -> Self {
        Self::new()
    }
}

impl BlockchainAuditor for MockBlockchainAuditor {
    fn anchor_async(&self, record: AuditRecord) -> Result<String> {
        tracing::debug!(
            record_type = ?record.record_type,
            hash = record.content_hash,
            "Mock: Anchoring audit record (async)"
        );
        
        // Return mock record ID
        Ok(format!("anchor-{}", uuid::Uuid::new_v4()))
    }

    fn verify_anchor(&self, record_id: &str) -> Result<bool> {
        tracing::debug!(record_id = record_id, "Mock: Verifying anchor (always true)");
        Ok(true)
    }

    fn get_audit_trail(&self, resource_id: &str) -> Result<Vec<AuditRecord>> {
        tracing::debug!(resource_id = resource_id, "Mock: Getting audit trail (empty)");
        Ok(vec![])
    }
}

#[cfg(feature = "use-blockchain-runtime")]
/// Real implementation using blockchain-runtime crate
pub struct RealBlockchainAuditor {
    runtime: blockchain_runtime::AuditRuntime,
}

#[cfg(feature = "use-blockchain-runtime")]
impl RealBlockchainAuditor {
    pub fn new() -> Result<Self> {
        Ok(Self {
            runtime: blockchain_runtime::AuditRuntime::new()?,
        })
    }
}

#[cfg(feature = "use-blockchain-runtime")]
impl BlockchainAuditor for RealBlockchainAuditor {
    fn anchor_async(&self, record: AuditRecord) -> Result<String> {
        tracing::debug!(
            record_type = ?record.record_type,
            hash = record.content_hash,
            "Anchoring audit record asynchronously"
        );

        // Convert to blockchain-runtime type
        let bc_record = blockchain_runtime::Record {
            record_type: format!("{:?}", record.record_type),
            content_hash: record.content_hash,
            timestamp: record.timestamp,
            metadata: record.metadata.to_string(),
        };

        // Anchor asynchronously (returns immediately with anchor ID)
        let anchor_id = self.runtime.anchor_async(bc_record)?;
        Ok(anchor_id)
    }

    fn verify_anchor(&self, record_id: &str) -> Result<bool> {
        tracing::debug!(record_id = record_id, "Verifying blockchain anchor");
        Ok(self.runtime.verify(record_id)?)
    }

    fn get_audit_trail(&self, resource_id: &str) -> Result<Vec<AuditRecord>> {
        tracing::debug!(resource_id = resource_id, "Getting audit trail");

        let records = self.runtime.get_trail(resource_id)?;

        // Convert from blockchain-runtime types
        let audit_records = records
            .into_iter()
            .map(|r| {
                let record_type = match r.record_type.as_str() {
                    "PolicyVersion" => AuditRecordType::PolicyVersion,
                    "PromptVersion" => AuditRecordType::PromptVersion,
                    "PlanDiff" => AuditRecordType::PlanDiff,
                    _ => AuditRecordType::ToolExecution,
                };

                AuditRecord {
                    record_type,
                    content_hash: r.content_hash,
                    timestamp: r.timestamp,
                    metadata: serde_json::from_str(&r.metadata).unwrap_or_default(),
                }
            })
            .collect();

        Ok(audit_records)
    }
}

/// Factory function
pub fn create_auditor() -> Result<Box<dyn BlockchainAuditor>> {
    #[cfg(feature = "use-blockchain-runtime")]
    {
        tracing::info!("Using real blockchain-runtime auditor");
        Ok(Box::new(RealBlockchainAuditor::new()?))
    }

    #[cfg(not(feature = "use-blockchain-runtime"))]
    {
        tracing::debug!("Using mock blockchain auditor");
        Ok(Box::new(MockBlockchainAuditor::new()))
    }
}

