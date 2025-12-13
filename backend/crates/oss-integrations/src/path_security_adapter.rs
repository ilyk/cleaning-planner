//! Path Security integration for tool argument validation

use anyhow::Result;
use std::path::{Path, PathBuf};
use thiserror::Error;

/// Path security errors
#[derive(Debug, Error)]
pub enum PathSecurityError {
    #[error("Path not in allow-list: {0}")]
    NotAllowed(String),

    #[error("Content hash mismatch: expected {expected}, got {actual}")]
    HashMismatch { expected: String, actual: String },

    #[error("Path traversal attempt detected: {0}")]
    TraversalAttempt(String),

    #[error("Invalid path: {0}")]
    Invalid(String),
}

/// Path security provider trait
pub trait PathSecurityProvider: Send + Sync {
    /// Check if path is allowed
    fn is_path_allowed(&self, path: &Path) -> Result<bool, PathSecurityError>;

    /// Validate path and return canonical version
    fn validate_and_canonicalize(&self, path: &Path) -> Result<PathBuf, PathSecurityError>;

    /// Verify content hash before file I/O
    fn verify_content_hash(&self, path: &Path, expected_hash: &str) -> Result<bool, PathSecurityError>;

    /// Get allowed prefixes
    fn allowed_prefixes(&self) -> Vec<PathBuf>;

    /// Check for path traversal attempts
    fn check_traversal(&self, path: &Path) -> Result<(), PathSecurityError>;
}

/// Mock implementation
#[derive(Clone)]
pub struct MockPathSecurityProvider {
    allowed_prefixes: Vec<PathBuf>,
}

impl MockPathSecurityProvider {
    pub fn new() -> Self {
        Self {
            allowed_prefixes: vec![
                PathBuf::from("/tmp/cleanflow/printables"),
                PathBuf::from("/tmp/cleanflow/qr"),
                PathBuf::from("/var/cleanflow/output"),
            ],
        }
    }
}

impl Default for MockPathSecurityProvider {
    fn default() -> Self {
        Self::new()
    }
}

impl PathSecurityProvider for MockPathSecurityProvider {
    fn is_path_allowed(&self, path: &Path) -> Result<bool, PathSecurityError> {
        let path_str = path.to_string_lossy();
        
        // Check for traversal
        if path_str.contains("..") {
            return Err(PathSecurityError::TraversalAttempt(path_str.to_string()));
        }

        // Check against allow-list
        for prefix in &self.allowed_prefixes {
            if path.starts_with(prefix) {
                tracing::debug!(path = ?path, "Path allowed by prefix");
                return Ok(true);
            }
        }

        tracing::warn!(path = ?path, "Path not in allow-list");
        Ok(false)
    }

    fn validate_and_canonicalize(&self, path: &Path) -> Result<PathBuf, PathSecurityError> {
        // Check traversal
        self.check_traversal(path)?;

        // Check allow-list
        if !self.is_path_allowed(path)? {
            return Err(PathSecurityError::NotAllowed(path.to_string_lossy().to_string()));
        }

        // Return canonical path (in mock, just clean it)
        Ok(path.to_path_buf())
    }

    fn verify_content_hash(&self, path: &Path, expected_hash: &str) -> Result<bool, PathSecurityError> {
        tracing::debug!(
            path = ?path,
            expected_hash = expected_hash,
            "Mock: Verifying content hash (always true)"
        );
        
        // In mock, always succeed
        Ok(true)
    }

    fn allowed_prefixes(&self) -> Vec<PathBuf> {
        self.allowed_prefixes.clone()
    }

    fn check_traversal(&self, path: &Path) -> Result<(), PathSecurityError> {
        let path_str = path.to_string_lossy();
        
        if path_str.contains("..") || path_str.contains("~") {
            return Err(PathSecurityError::TraversalAttempt(path_str.to_string()));
        }

        Ok(())
    }
}

#[cfg(feature = "use-path-security")]
/// Real implementation using path-security crate
pub struct RealPathSecurityProvider {
    validator: path_security::PathValidator,
}

#[cfg(feature = "use-path-security")]
impl RealPathSecurityProvider {
    pub fn new(allowed_prefixes: Vec<PathBuf>) -> Result<Self> {
        Ok(Self {
            validator: path_security::PathValidator::new(allowed_prefixes)?,
        })
    }
}

#[cfg(feature = "use-path-security")]
impl PathSecurityProvider for RealPathSecurityProvider {
    fn is_path_allowed(&self, path: &Path) -> Result<bool, PathSecurityError> {
        tracing::debug!(path = ?path, "Checking if path is allowed");
        
        match self.validator.is_allowed(path) {
            Ok(allowed) => Ok(allowed),
            Err(e) => Err(PathSecurityError::Invalid(e.to_string())),
        }
    }

    fn validate_and_canonicalize(&self, path: &Path) -> Result<PathBuf, PathSecurityError> {
        tracing::debug!(path = ?path, "Validating and canonicalizing path");
        
        // Check for traversal
        if let Err(e) = self.validator.check_traversal(path) {
            return Err(PathSecurityError::TraversalAttempt(e.to_string()));
        }

        // Validate against allow-list
        if !self.validator.is_allowed(path)? {
            return Err(PathSecurityError::NotAllowed(
                path.to_string_lossy().to_string(),
            ));
        }

        // Canonicalize
        match self.validator.canonicalize(path) {
            Ok(canonical) => Ok(canonical),
            Err(e) => Err(PathSecurityError::Invalid(e.to_string())),
        }
    }

    fn verify_content_hash(&self, path: &Path, expected_hash: &str) -> Result<bool, PathSecurityError> {
        tracing::debug!(
            path = ?path,
            expected_hash = expected_hash,
            "Verifying content hash"
        );

        match self.validator.verify_hash(path, expected_hash) {
            Ok(valid) => Ok(valid),
            Err(e) => Err(PathSecurityError::HashMismatch {
                expected: expected_hash.to_string(),
                actual: e.to_string(),
            }),
        }
    }

    fn allowed_prefixes(&self) -> Vec<PathBuf> {
        self.validator.allowed_prefixes()
    }

    fn check_traversal(&self, path: &Path) -> Result<(), PathSecurityError> {
        match self.validator.check_traversal(path) {
            Ok(()) => Ok(()),
            Err(e) => Err(PathSecurityError::TraversalAttempt(e.to_string())),
        }
    }
}

/// Factory function
pub fn create_provider() -> Result<Box<dyn PathSecurityProvider>, anyhow::Error> {
    #[cfg(feature = "use-path-security")]
    {
        tracing::info!("Using real path-security provider");
        let allowed_prefixes = vec![
            PathBuf::from("/tmp/cleanflow/printables"),
            PathBuf::from("/tmp/cleanflow/qr"),
            PathBuf::from("/var/cleanflow/output"),
        ];
        Ok(Box::new(RealPathSecurityProvider::new(allowed_prefixes)?))
    }

    #[cfg(not(feature = "use-path-security"))]
    {
        tracing::debug!("Using mock path-security provider");
        Ok(Box::new(MockPathSecurityProvider::new()))
    }
}

