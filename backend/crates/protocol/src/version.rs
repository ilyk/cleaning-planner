//! Protocol version management

/// Current protocol version
pub const PROTOCOL_VERSION: &str = "clara-stream-v0.1.0";

/// Check if a client version is compatible
pub fn is_compatible(client_version: &str) -> bool {
    client_version == PROTOCOL_VERSION
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_version_compatibility() {
        assert!(is_compatible(PROTOCOL_VERSION));
        assert!(!is_compatible("clara-stream-v0.0.1"));
        assert!(!is_compatible("invalid"));
    }
}

