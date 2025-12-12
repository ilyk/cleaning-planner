//! Quantum Shield integration for post-quantum crypto

use anyhow::Result;

/// Quantum Shield provider trait
pub trait QuantumShieldProvider: Send + Sync {
    /// Generate post-quantum key pair
    fn generate_keypair(&self) -> Result<(Vec<u8>, Vec<u8>)>; // (public, private)

    /// Sign data with post-quantum signature
    fn sign(&self, data: &[u8], private_key: &[u8]) -> Result<Vec<u8>>;

    /// Verify post-quantum signature
    fn verify(&self, data: &[u8], signature: &[u8], public_key: &[u8]) -> Result<bool>;

    /// Derive shared secret for HMAC
    fn derive_hmac_key(&self, session_secret: &[u8]) -> Result<Vec<u8>>;

    /// Validate frame HMAC
    fn validate_frame_hmac(&self, frame: &[u8], hmac: &[u8], key: &[u8]) -> Result<bool>;
}

/// Mock implementation
#[derive(Clone)]
pub struct MockQuantumShieldProvider;

impl MockQuantumShieldProvider {
    pub fn new() -> Self {
        Self
    }
}

impl Default for MockQuantumShieldProvider {
    fn default() -> Self {
        Self::new()
    }
}

impl QuantumShieldProvider for MockQuantumShieldProvider {
    fn generate_keypair(&self) -> Result<(Vec<u8>, Vec<u8>)> {
        tracing::debug!("Mock: Generating PQ keypair");

        // Mock keys
        let public_key = vec![0x01; 32];
        let private_key = vec![0x02; 64];

        Ok((public_key, private_key))
    }

    fn sign(&self, data: &[u8], _private_key: &[u8]) -> Result<Vec<u8>> {
        tracing::debug!(data_len = data.len(), "Mock: Signing with PQ");

        // Mock signature
        Ok(vec![0xAA; 128])
    }

    fn verify(&self, data: &[u8], _signature: &[u8], _public_key: &[u8]) -> Result<bool> {
        tracing::debug!(data_len = data.len(), "Mock: Verifying PQ signature");

        // Always valid in mock
        Ok(true)
    }

    fn derive_hmac_key(&self, session_secret: &[u8]) -> Result<Vec<u8>> {
        tracing::debug!(
            secret_len = session_secret.len(),
            "Mock: Deriving HMAC key from session secret"
        );

        // Simple mock derivation
        Ok(vec![0xFF; 32])
    }

    fn validate_frame_hmac(&self, frame: &[u8], _hmac: &[u8], _key: &[u8]) -> Result<bool> {
        tracing::trace!(frame_len = frame.len(), "Mock: Validating frame HMAC");

        // Always valid in mock
        Ok(true)
    }
}

#[cfg(feature = "use-quantum-shield")]
/// Real implementation using quantum-shield crate
pub struct RealQuantumShieldProvider {
    shield: quantum_shield::Shield,
}

#[cfg(feature = "use-quantum-shield")]
impl RealQuantumShieldProvider {
    pub fn new() -> Result<Self> {
        tracing::info!("Initializing real quantum-shield (post-quantum cryptography)");
        Ok(Self {
            shield: quantum_shield::Shield::new()?,
        })
    }
}

#[cfg(feature = "use-quantum-shield")]
impl QuantumShieldProvider for RealQuantumShieldProvider {
    fn generate_keypair(&self) -> Result<(Vec<u8>, Vec<u8>)> {
        tracing::debug!("Generating post-quantum keypair");

        let keypair = self.shield.generate_keypair()?;
        Ok((keypair.public_key, keypair.private_key))
    }

    fn sign(&self, data: &[u8], private_key: &[u8]) -> Result<Vec<u8>> {
        tracing::trace!(data_len = data.len(), "Signing data with PQ signature");

        let signature = self.shield.sign(data, private_key)?;
        Ok(signature)
    }

    fn verify(&self, data: &[u8], signature: &[u8], public_key: &[u8]) -> Result<bool> {
        tracing::trace!(
            data_len = data.len(),
            sig_len = signature.len(),
            "Verifying PQ signature"
        );

        Ok(self.shield.verify(data, signature, public_key)?)
    }

    fn derive_hmac_key(&self, session_secret: &[u8]) -> Result<Vec<u8>> {
        tracing::debug!(
            secret_len = session_secret.len(),
            "Deriving PQ-safe HMAC key from session secret"
        );

        let key = self.shield.derive_key(session_secret, b"ws-frame-hmac")?;
        Ok(key)
    }

    fn validate_frame_hmac(&self, frame: &[u8], hmac: &[u8], key: &[u8]) -> Result<bool> {
        tracing::trace!(frame_len = frame.len(), "Validating frame HMAC");

        Ok(self.shield.validate_hmac(frame, hmac, key)?)
    }
}

/// Factory function
pub fn create_provider() -> Result<Box<dyn QuantumShieldProvider>> {
    #[cfg(feature = "use-quantum-shield")]
    {
        tracing::info!("Using real quantum-shield provider (post-quantum cryptography enabled)");
        Ok(Box::new(RealQuantumShieldProvider::new()?))
    }

    #[cfg(not(feature = "use-quantum-shield"))]
    {
        tracing::debug!("Using mock quantum-shield provider (PQ crypto disabled)");
        Ok(Box::new(MockQuantumShieldProvider::new()))
    }
}

