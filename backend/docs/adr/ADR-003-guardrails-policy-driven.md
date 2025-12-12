# ADR-003: Guardrails — Policy-Driven Pipeline, llm-security Integration

**Status**: Accepted  
**Date**: 2025-10-29  
**Deciders**: Backend Architecture Team

## Context

Clara must filter unsafe content, PII, and policy violations in real-time during voice interactions. The system needs to be policy-driven and adaptable without code changes.

## Decision

- **Pipeline Architecture**: Multi-stage audio-first validation (VAD → LID → Keyword → Embedding → Policy)
- **Policy Engine**: Centralized policy engine that maps verdicts to actions (ALLOW, MASK, DOWNGRADE, BLOCK)
- **llm-security Integration**: Optional integration for policy pack management, threshold curves, and drift detection
- **Feature-Gated**: llm-security behind `use-llm-security` feature flag

## Rationale

### Policy-Driven
- Thresholds and categories configurable via policy packs
- No code changes needed for policy updates
- Canary deployments and A/B testing supported
- Versioned policies for auditability

### Multi-Stage Pipeline
- Early exit optimizations (VAD, LID)
- Defense in depth — multiple detection methods
- Configurable sensitivity per stage

### llm-security Integration
- Industry-standard policy management
- Signature verification for policy packs
- Telemetry for continuous improvement
- Drift detection for policy tuning

## Consequences

### Positive
- Flexible policy management
- Production-grade content filtering
- Continuous improvement via telemetry
- Vendor-agnostic (feature-gated)

### Negative
- Additional dependency when enabled
- Policy pack loading overhead (one-time on startup)
- More complex configuration

## Implementation Details

- Policy engine loads policy packs at startup
- Thresholds updated dynamically from policy packs
- Verdicts recorded for drift analysis
- Fallback to basic policy when llm-security disabled

## Alternatives Considered

1. **Hard-coded thresholds**: Rejected — too inflexible
2. **Database-driven policies**: Rejected — adds DB dependency
3. **External service**: Rejected — latency concerns

