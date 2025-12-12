# Clara OSS Integrations

Trait-based adapters for optional security and capability management libraries.

## Overview

This crate provides integration points for external security libraries while keeping the core Clara backend vendor-agnostic and testable. Each integration is:

- **Behind a feature flag** - Only compiled when explicitly enabled
- **Trait-based** - Easy to mock and test
- **Falls back to mocks** - Works without external dependencies

## Integrated Libraries

### 1. llm-security

**Purpose**: Guardrails policy management

**Integration Point**: `guardrails` crate

**Features**:
- Policy pack loading and versioning
- Config signing and verification  
- Verdict telemetry for drift detection
- Threshold curve management

**Usage**:
```rust
let provider = llm_security_adapter::create_provider();
let policy = provider.load_policy_pack("v1.0.0")?;
provider.record_verdict_telemetry(&verdict)?;
```

### 2. path-security

**Purpose**: Tool argument validation

**Integration Point**: `tools` crate

**Features**:
- Allow-list enforcement for file paths
- Path traversal detection
- Content hash verification
- Canonicalization

**Usage**:
```rust
let provider = path_security_adapter::create_provider();
let safe_path = provider.validate_and_canonicalize(path)?;
provider.verify_content_hash(&safe_path, expected_hash)?;
```

### 3. blockchain-runtime

**Purpose**: Tamper-evident audit trails

**Integration Point**: Async background worker

**Features**:
- Hash anchoring for prompt/policy versions
- Plan diff auditing
- Write-behind (never blocks turns)
- Verifiable audit trails

**Usage**:
```rust
let auditor = blockchain_adapter::create_auditor();
auditor.anchor_async(AuditRecord { ... })?; // Non-blocking
```

### 4. worker-capabilities

**Purpose**: Turn-level capability tokens

**Integration Point**: `guardrails` → `tools`

**Features**:
- Dynamic capability assignment based on guardrail verdicts
- Fine-grained tool access control
- Token expiration
- Capability revocation

**Usage**:
```rust
let provider = capabilities_adapter::create_provider();
let token = provider.create_token(turn_id, &action)?;
if provider.has_capability(&token, Capability::AllowPlanWrite) {
    // Execute tool
}
```

### 5. module-registry

**Purpose**: Dynamic module loading

**Integration Point**: `guardrails`, `llm`

**Features**:
- Hot-load guardrail models
- Version-tagged modules
- Canary deployments
- Rolling upgrades without rebuild

**Usage**:
```rust
let registry = module_registry_adapter::create_registry();
let module = registry.load_module(ModuleType::KeywordLexicon, "v2.0.0")?;
```

### 6. threat-intel

**Purpose**: Threat intelligence feeds

**Integration Point**: `guardrails`

**Features**:
- Known bad token/phrase detection
- IP reputation scoring
- Auto-blocking by reputation
- Continuous lexicon updates

**Usage**:
```rust
let provider = threat_intel_adapter::create_provider();
if provider.is_known_bad_token(token)? {
    return Action::Block;
}
```

### 7. quantum-shield

**Purpose**: Post-quantum cryptography

**Integration Point**: `auth`, `stream`

**Features**:
- PQ keypair generation
- PQ signatures for config signing
- Frame-level HMAC with PQ keys
- Future-proof crypto

**Usage**:
```rust
let provider = quantum_shield_adapter::create_provider();
let (pub_key, priv_key) = provider.generate_keypair()?;
let signature = provider.sign(data, &priv_key)?;
```

## Feature Flags

Add to your `Cargo.toml`:

```toml
[dependencies]
clara-oss-integrations = { path = "../oss-integrations", features = ["llm-security", "path-security"] }
```

Or enable all:

```toml
features = ["llm-security", "path-security", "blockchain-runtime", "worker-capabilities", "module-registry", "threat-intel", "quantum-shield"]
```

## Architecture

```
┌─────────────────────────────────────┐
│   Clara Core (guardrails, tools)   │
└────────────────┬────────────────────┘
                 │
                 │ Trait-based interface
                 │
┌────────────────▼────────────────────┐
│     OSS Integrations (adapters)     │
│  ┌────────────────────────────────┐ │
│  │  Mock (always available)       │ │
│  └────────────────────────────────┘ │
│  ┌────────────────────────────────┐ │
│  │  Real (when feature enabled)   │ │
│  └────────────────────────────────┘ │
└─────────────────────────────────────┘
```

## Testing

Mock implementations are always available:

```rust
#[test]
fn test_with_mock() {
    let provider = MockLlmSecurityProvider::new();
    let policy = provider.load_policy_pack("v1.0.0").unwrap();
    assert!(!policy.unsafe_categories.is_empty());
}
```

Real implementations require feature flags:

```rust
#[cfg(feature = "llm-security")]
#[test]
fn test_with_real() {
    let provider = RealLlmSecurityProvider::new();
    // Test with actual llm-security crate
}
```

## Configuration

Enable features via environment:

```bash
# .env
FEATURES_OSS_LLM_SECURITY=true
FEATURES_OSS_PATH_SECURITY=true
FEATURES_OSS_BLOCKCHAIN_RUNTIME=false  # Optional
```

## Performance Impact

| Integration | Overhead | Notes |
|-------------|----------|-------|
| llm-security | <1ms | Policy lookup cached |
| path-security | <1ms | Simple validation |
| blockchain-runtime | 0ms | Async, write-behind |
| worker-capabilities | <1ms | Token check |
| module-registry | Variable | Depends on module size |
| threat-intel | <2ms | Hash table lookup |
| quantum-shield | ~5ms | PQ crypto slower than classical |

## Integration Examples

### Guardrails with llm-security

```rust
use clara_oss_integrations::LlmSecurityProvider;

pub struct EnhancedGuardrails {
    base: GuardrailsPipeline,
    security: Box<dyn LlmSecurityProvider>,
}

impl EnhancedGuardrails {
    pub fn evaluate(&self, audio: &[u8]) -> Result<Verdict> {
        // Load current policy
        let policy = self.security.load_policy_pack("latest")?;
        
        // Run base guardrails
        let verdict = self.base.evaluate(audio, "opus@24000")?;
        
        // Record telemetry
        self.security.record_verdict_telemetry(&verdict)?;
        
        Ok(verdict)
    }
}
```

### Tools with path-security

```rust
use clara_oss_integrations::PathSecurityProvider;

pub async fn generate_printable(
    path_security: &dyn PathSecurityProvider,
    output_path: &str,
) -> Result<()> {
    let path = Path::new(output_path);
    
    // Validate path
    let safe_path = path_security.validate_and_canonicalize(path)?;
    
    // Generate PDF
    let content = generate_pdf()?;
    
    // Write with verification
    write_file(&safe_path, &content)?;
    
    Ok(())
}
```

## Extending

To add a new integration:

1. Create `src/my_integration_adapter.rs`
2. Define trait with `Send + Sync`
3. Implement mock version
4. Add `#[cfg(feature = "my-integration")]` for real version
5. Export from `lib.rs`
6. Document in this README

## Dependencies

Mock implementations have zero external dependencies. Real implementations require the respective crates, which are optional workspace dependencies.

## License

Same as Clara backend (MIT)

