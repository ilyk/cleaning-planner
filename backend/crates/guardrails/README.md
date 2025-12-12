# Guardrails Module

Comprehensive safety/validation system for Clara's realtime voice mode and text chat, treating the client as untrusted.

## Architecture

The guardrails pipeline processes audio through multiple stages:

1. **Quarantine Buffer**: 2-4s ring buffer with sliding window (default: 1200ms span, 200ms slide)
2. **Deterministic Checks**: VAD, codec validation, LID, digit patterns, sequence validation
3. **Keyword Spotting**: Phoneme/keyword matching against unsafe lexicon
4. **Embedding Classification**: Multi-label safety scores (harassment, sexual, violence, self-harm, hate, PII)
5. **Policy Engine**: Risk class (R0-R3) → Action (ALLOW, DOWNGRADE, MASK, HARD_BLOCK)
6. **Output Filtering**: PII redaction, token rate limiting

## Risk Classes

- **R0**: Clean speech → ALLOW
- **R1**: Mild harassment, heated tone → DOWNGRADE (read-only)
- **R2**: PII digits, adult sexual innuendo → MASK + DENY_TOOLS
- **R3**: Hate, sexual minors, self-harm, violence → HARD_BLOCK

## Configuration

Policy is loaded from `config/policy.guardrails.yaml`. See that file for thresholds and actions.

Environment variables:
- `GUARDRAILS_KWS_ENABLED=true` (default: true)
- `GUARDRAILS_EMBEDDINGS_ENABLED=true` (default: true)
- `GUARDRAILS_QUARANTINE_SPAN_MS=1200` (default: 1200)
- `GUARDRAILS_QUARANTINE_SLIDE_MS=200` (default: 200)

## Usage

```rust
use clara_guardrails::{GuardrailPipeline, AudioSpan, pipeline::PipelineConfig};

let config = PipelineConfig {
    quarantine_span_ms: 1200,
    quarantine_slide_ms: 200,
    sample_rate_hz: 24000,
    kws_enabled: true,
    embeddings_enabled: true,
};

let pipeline = GuardrailPipeline::new(config)?;

let span = AudioSpan {
    data: audio_bytes,
    format: "opus@24000".to_string(),
    seq: 1,
};

let verdict = pipeline.process(span).await?;

match verdict.action {
    Action::Block => {
        // Send interrupt and safe script
    }
    Action::Mask => {
        // Apply masked ranges
    }
    Action::Downgrade => {
        // Limit capabilities
    }
    Action::Allow => {
        // Forward to LLM
    }
}
```

## Events

The pipeline generates events sent to clients:

- `guardrail.notice`: User-facing message about policy action
- `guardrail.mask`: Time ranges to mute/bleep
- `capability.update`: Allowed/denied capabilities
- `interrupt`: Immediate stop due to policy block

## Metrics

Prometheus metrics exposed:
- `clara_guardrail_ttft_ms`: Processing overhead
- `clara_guardrail_quarantine_dwell_ms`: Buffer dwell time
- `clara_guardrail_r2_count`: R2 detections
- `clara_guardrail_r3_count`: R3 detections
- `clara_guardrail_mask_ranges`: Mask ranges applied
- `clara_guardrail_interrupts`: Interrupts triggered
- `clara_guardrail_tool_denies`: Tool denials

## Testing

Run unit tests:
```bash
cargo test --package clara-guardrails
```

Integration tests verify R2/R3 scenarios and late-hit interrupts.

## Policy Hot-Reload

Send SIGHUP to reload policy config:
```rust
policy_engine.reload()?;
```

## Output Filtering

Filter LLM output for PII:
```rust
let filtered = pipeline.filter_output(&text).await?;
```

Check token rate limits:
```rust
if !pipeline.check_output_rate(num_tokens).await {
    // Rate limit exceeded
}
```


