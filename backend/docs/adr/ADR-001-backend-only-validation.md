# ADR-001: Backend-Only Validation and Tool Execution

**Status**: Accepted  
**Date**: 2025-10-29  
**Deciders**: Backend Architecture Team

## Context

The Clara backend architecture requires that all validation, guardrails, and tool execution happen **server-side only**. Clients are considered untrusted, and all operations must be re-validated by the backend regardless of client-side assertions.

## Decision

- **All guardrail evaluation** happens server-side on audio streams
- **All tool execution** (plan generation, revision, printable, family assignment) executes only on the backend
- **No client-initiated tool calls** — tools are invoked only via LLM adapter tool call events
- **Capability tokens** are generated server-side based on guardrail verdicts
- **Rate limiting** and **authentication** are enforced server-side

## Consequences

### Positive
- Zero-trust security model — clients cannot bypass validation
- Centralized policy enforcement — all rules in one place
- Auditability — all tool executions logged server-side
- Consistency — same validation logic for all clients

### Negative
- Network latency for validation (mitigated by streaming design)
- Server load for processing (mitigated by rate limiting)
- More complex backend (offset by clear separation of concerns)

## Alternatives Considered

1. **Client-side pre-validation**: Rejected — security risk
2. **Hybrid validation**: Rejected — complexity not worth the benefit
3. **Client tool execution with backend audit**: Rejected — security risk

## Implementation Notes

- Guardrails pipeline runs on every audio window server-side
- Tools layer validates home_id and member_id from JWT claims
- LLM adapter translates tool calls into backend tool execution
- No raw audio or tool results sent to client without validation

