# ADR-004: LLM Adapter Trait; OpenAI Realtime Implementation

**Status**: Accepted  
**Date**: 2025-10-29  
**Deciders**: Backend Architecture Team

## Context

Clara needs to support multiple LLM providers (OpenAI, Anthropic, etc.) while maintaining vendor-agnostic architecture. The system should be able to swap providers without changing core logic.

## Decision

- **Trait-Based Design**: `LlmRealtime` trait defines the interface
- **OpenAI Realtime Implementation**: Production implementation using OpenAI Realtime API (GPT-5)
- **Mock Implementation**: Default mock adapter for testing (no API key required)
- **Feature-Gated**: OpenAI adapter behind `openai_realtime` feature flag

## Rationale

### Trait-Based Design
- Vendor-agnostic — easy to swap providers
- Testable — can use mock implementations
- Clear interface — well-defined contract

### OpenAI Realtime
- Industry-leading voice AI
- Low latency streaming API
- Good developer experience
- Supports GPT-5 model

### Mock by Default
- No external dependencies for development
- Predictable behavior for testing
- Faster iteration cycles

## Consequences

### Positive
- Flexible provider switching
- Testable without external services
- Production-ready implementation available
- Clear separation of concerns

### Negative
- Additional abstraction layer
- Mock may not catch real API issues
- Feature flags increase complexity

## Implementation Details

- Trait: `crates/llm/src/traits.rs`
- OpenAI: `crates/llm/src/openai.rs` (feature-gated)
- Mock: `crates/llm/src/mock.rs` (default)

## Alternatives Considered

1. **Direct OpenAI integration**: Rejected — vendor lock-in
2. **HTTP-only**: Rejected — too high latency
3. **gRPC**: Rejected — less standard than WebSocket

