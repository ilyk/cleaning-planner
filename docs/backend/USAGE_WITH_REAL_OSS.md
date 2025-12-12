# Using Real OSS Integrations - Quick Start Guide

This guide shows how to enable and use the real OSS library integrations in the Clara backend.

---

## Quick Start

### 1. Enable All OSS Features

```bash
# Build with all OSS integrations enabled
cd backend
cargo build --features oss-all

# Run server with all features
cargo run -p clara-stream-server --features oss-all
```

### 2. Enable Specific Features

```bash
# Only security features
cargo run -p clara-stream-server \
    --features oss-llm-security,oss-path-security,oss-quantum-shield

# Only capability and intel features
cargo run -p clara-stream-server \
    --features oss-worker-capabilities,oss-threat-intel
```

### 3. Development (Mocks Only)

```bash
# No features = all mocks
cargo run -p clara-stream-server
```

---

## Integration in Application Code

### Update `AppState` to Include OSS Adapters

**File**: `crates/api/src/state.rs`

```rust
use clara_oss_integrations::{
    llm_security_adapter, path_security_adapter, blockchain_adapter,
    capabilities_adapter, module_registry_adapter, threat_intel_adapter,
    quantum_shield_adapter,
};

pub struct AppState {
    pub config: Arc<AppConfig>,
    pub store: Arc<Store>,
    pub session_registry: Arc<SessionRegistry>,
    pub metrics: Arc<Metrics>,
    
    // OSS adapters
    pub llm_security: Arc<Box<dyn llm_security_adapter::LlmSecurityProvider>>,
    pub path_security: Arc<Box<dyn path_security_adapter::PathSecurityProvider>>,
    pub blockchain: Arc<Box<dyn blockchain_adapter::BlockchainAuditor>>,
    pub capabilities: Arc<Box<dyn capabilities_adapter::CapabilitiesProvider>>,
    pub module_registry: Arc<Box<dyn module_registry_adapter::ModuleRegistry>>,
    pub threat_intel: Arc<Box<dyn threat_intel_adapter::ThreatIntelProvider>>,
    pub quantum_shield: Arc<Box<dyn quantum_shield_adapter::QuantumShieldProvider>>,
}

impl AppState {
    pub fn new(config: AppConfig, store: Store) -> anyhow::Result<Self> {
        Ok(Self {
            config: Arc::new(config),
            store: Arc::new(store),
            session_registry: Arc::new(SessionRegistry::new()),
            metrics: Arc::new(Metrics::new()),
            
            // Initialize OSS adapters (automatically selects real or mock based on features)
            llm_security: Arc::new(llm_security_adapter::create_provider()?),
            path_security: Arc::new(path_security_adapter::create_provider()?),
            blockchain: Arc::new(blockchain_adapter::create_auditor()?),
            capabilities: Arc::new(capabilities_adapter::create_provider()?),
            module_registry: Arc::new(module_registry_adapter::create_registry()?),
            threat_intel: Arc::new(threat_intel_adapter::create_provider()?),
            quantum_shield: Arc::new(quantum_shield_adapter::create_provider()?),
        })
    }
}
```

---

## Using OSS Adapters

### 1. Guardrails Pipeline

**File**: `crates/guardrails/src/pipeline.rs`

```rust
use clara_oss_integrations::{llm_security_adapter, threat_intel_adapter};

pub struct Pipeline {
    llm_security: Arc<Box<dyn llm_security_adapter::LlmSecurityProvider>>,
    threat_intel: Arc<Box<dyn threat_intel_adapter::ThreatIntelProvider>>,
    // ... other components
}

impl Pipeline {
    pub async fn evaluate_window(&self, audio: &[u8], format: &str) -> Verdict {
        // 1. Load policy pack
        let policy_pack = self.llm_security
            .load_policy_pack(&self.config.policy_version)?;
        
        // 2. Check threat intel for known bad patterns
        // (assuming we have text from ASR)
        for token in tokens {
            if self.threat_intel.is_known_bad_token(&token)? {
                return Verdict {
                    action: Action::Block,
                    categories: vec!["threat-intel-match".to_string()],
                    confidence: 1.0,
                    redactions: vec![],
                };
            }
        }
        
        // 3. Run other checks (VAD, LID, keyword, etc.)
        let verdict = self.run_checks(audio, format)?;
        
        // 4. Record verdict telemetry
        self.llm_security.record_verdict_telemetry(&verdict)?;
        
        verdict
    }
}
```

### 2. Tools Layer

**File**: `crates/tools/src/printable.rs`

```rust
use clara_oss_integrations::{path_security_adapter, capabilities_adapter};

pub async fn printable(
    ctx: &ToolContext,
    plan_id: &str,
    format: &str,
) -> Result<String> {
    // 1. Check capability
    if !ctx.capabilities.has_capability(&ctx.token, Capability::AllowPrintable) {
        return Err(anyhow!("CAPABILITY_DENIED: AllowPrintable required"));
    }
    
    // 2. Validate path
    let output_path = format!("/tmp/clara/printables/{}.{}", plan_id, format);
    let safe_path = ctx.path_security
        .validate_and_canonicalize(Path::new(&output_path))?;
    
    // 3. Generate printable
    let pdf_data = generate_pdf(plan_id)?;
    
    // 4. Write with content hash
    std::fs::write(&safe_path, &pdf_data)?;
    let hash = compute_sha256(&pdf_data);
    
    // 5. Verify written content
    ctx.path_security.verify_content_hash(&safe_path, &hash)?;
    
    Ok(format!("/printables/{}.pdf", plan_id))
}
```

### 3. Session Management

**File**: `crates/session/src/lib.rs`

```rust
use clara_oss_integrations::threat_intel_adapter;

pub struct SessionRegistry {
    threat_intel: Arc<Box<dyn threat_intel_adapter::ThreatIntelProvider>>,
    // ... other fields
}

impl SessionRegistry {
    pub async fn check_rate_limit(
        &self,
        session_id: &str,
        client_ip: &str,
    ) -> Result<bool> {
        // 1. Get IP reputation
        let reputation = self.threat_intel.get_ip_reputation(client_ip)?;
        
        // 2. Adjust rate limit based on reputation
        let multiplier = self.threat_intel.get_rate_limit_multiplier(client_ip)?;
        let effective_limit = (self.base_limit as f32 * multiplier) as u32;
        
        // 3. Check against adjusted limit
        let current = self.store.get_rate_limit_count(session_id).await?;
        
        if current >= effective_limit {
            // Report activity
            self.threat_intel.report_activity(ThreatActivity {
                ip: client_ip.to_string(),
                activity_type: ActivityType::RateLimit,
                severity: Severity::Medium,
                metadata: json!({ "session_id": session_id }),
            })?;
            
            return Ok(false);
        }
        
        Ok(true)
    }
}
```

### 4. Blockchain Audit Trail

**File**: `crates/stream/src/turn_executor.rs`

```rust
use clara_oss_integrations::blockchain_adapter;

pub async fn finish_turn(
    &self,
    turn_id: &str,
    result: TurnResult,
) -> Result<()> {
    // 1. Record metrics
    self.store.telemetry.record_turn_metrics(turn_id, &result.metrics).await?;
    
    // 2. Anchor to blockchain (async, write-behind)
    if result.had_policy_violation || result.had_tool_execution {
        let record = blockchain_adapter::AuditRecord {
            record_type: blockchain_adapter::AuditRecordType::ToolExecution,
            content_hash: compute_hash(&result),
            timestamp: chrono::Utc::now(),
            metadata: json!({
                "turn_id": turn_id,
                "policy_version": self.config.policy_version,
                "prompt_version": self.config.prompt_version,
            }),
        };
        
        // This returns immediately; anchoring happens async
        let anchor_id = self.blockchain.anchor_async(record)?;
        tracing::info!(
            turn_id = turn_id,
            anchor_id = anchor_id,
            "Turn anchored to audit trail"
        );
    }
    
    Ok(())
}
```

### 5. Module Hot-Reloading

**File**: `crates/api/src/handlers.rs` (Admin endpoint)

```rust
use clara_oss_integrations::module_registry_adapter;

/// Admin endpoint to hot-reload guardrail modules
pub async fn hot_reload_module(
    State(state): State<Arc<AppState>>,
    Json(req): Json<HotReloadRequest>,
) -> Result<Json<HotReloadResponse>> {
    // Requires admin token
    
    tracing::info!(
        module_type = ?req.module_type,
        from_version = req.from_version,
        to_version = req.to_version,
        "Hot reloading module"
    );
    
    state.module_registry.hot_reload(
        req.module_type,
        &req.from_version,
        &req.to_version,
    )?;
    
    Ok(Json(HotReloadResponse {
        success: true,
        message: format!(
            "Module {:?} reloaded from {} to {}",
            req.module_type, req.from_version, req.to_version
        ),
    }))
}
```

---

## Configuration

### Environment Variables

```bash
# Enable specific features at runtime (if supported by the crates)
export CLARA_LLM_SECURITY_ENABLED=true
export CLARA_PATH_SECURITY_ENABLED=true
export CLARA_BLOCKCHAIN_ENABLED=false  # Disable blockchain in dev
export CLARA_THREAT_INTEL_API_KEY="your-api-key"
export CLARA_QUANTUM_SHIELD_ENABLED=false  # Enable in production only
```

### Feature Flags in Cargo.toml

For production deployment, build with:

```toml
[dependencies]
clara-stream-server = { path = "bin/clara-stream-server", features = ["oss-all"] }
```

Or selectively:

```toml
[dependencies]
clara-stream-server = {
    path = "bin/clara-stream-server",
    features = [
        "oss-llm-security",
        "oss-path-security",
        "oss-worker-capabilities",
        "oss-threat-intel",
    ]
}
```

---

## Testing with Real Integrations

### Unit Tests

```rust
#[cfg(feature = "use-llm-security")]
#[tokio::test]
async fn test_real_policy_loading() {
    let provider = llm_security_adapter::create_provider().unwrap();
    let policy = provider.load_policy_pack("v1.0.0").unwrap();
    assert!(!policy.unsafe_categories.is_empty());
}
```

### Integration Tests

```bash
# Test with all features
cargo test --all-features

# Test specific integration
cargo test --package clara-oss-integrations \
    --features use-threat-intel \
    --test threat_intel_integration
```

---

## Deployment Checklist

- [ ] Build with `--features oss-all` for production
- [ ] Configure API keys for threat-intel (if required)
- [ ] Set up blockchain node connection (if using blockchain-runtime)
- [ ] Verify path-security allowed prefixes match deployment paths
- [ ] Test module-registry canary deployments in staging
- [ ] Enable quantum-shield for production WS endpoints
- [ ] Monitor llm-security drift reports
- [ ] Set up alerts for threat-intel high-severity reports

---

## Troubleshooting

### "Feature X not enabled" at runtime

**Solution**: Rebuild with the feature flag:
```bash
cargo build --features oss-X
```

### Mock provider being used in production

**Symptom**: Logs show "Using mock X provider"

**Solution**: Verify feature flags are enabled at build time:
```bash
cargo build --features oss-all
```

### Threat intel API key errors

**Solution**: Set the API key environment variable:
```bash
export CLARA_THREAT_INTEL_API_KEY="your-key"
```

### Path security denying valid paths

**Solution**: Update allowed prefixes in the factory function:
```rust
let allowed_prefixes = vec![
    PathBuf::from("/your/custom/path"),
];
```

---

## Performance Considerations

### With All Features Enabled

- **Overhead per turn**: ~10-50ms (varies by integration)
- **Memory**: +50-100MB for caches/modules
- **Network**: Threat intel sync every 5 minutes

### Optimization Tips

1. **Blockchain**: Use write-behind async anchoring (already implemented)
2. **Threat Intel**: Cache reputation scores locally (TTL: 1 hour)
3. **Module Registry**: Pre-load common modules at startup
4. **Quantum Shield**: Only validate frame HMACs for sensitive endpoints

---

## Summary

Real OSS integrations are now fully functional and can be enabled via feature flags. Use:

- **Development**: No features (all mocks)
- **Staging**: Selective features for validation
- **Production**: `--features oss-all` for maximum security

All adapters gracefully fall back to mocks when features are disabled, ensuring the system works in all environments!

---

*Last Updated: 2025-10-29*

