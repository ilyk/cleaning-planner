# Clara Backend Architecture (Rust)

## Overview

APP ⇄ BACKEND ⇄ LLM

yaml
Copy code

Clara’s backend is the **authoritative streaming relay** between the untrusted app and the LLM.  
It handles:
- Session + turn lifecycle
- WebSocket streaming
- Audio-based guardrails
- LLM communication
- Tool execution
- Observability and policy enforcement

---

## 1. High-Level Architecture

┌──────────────────────────────────────────────────────────────────┐
│ Public Edge │
└──────────────────────────────────────────────────────────────────┘
HTTPS (REST) WSS (stream)
│ │
┌───────▼────────┐ ┌────────▼──────────┐
│ API Gateway │ │ WS Connection Hub │
│ (axum router) │ │ (handler+broker) │
└──────┬─────────┘ └────────┬──────────┘
│ │
▼ ▼
┌───────────────┐ ┌──────────────────────┐
│ Session Svc │◄── control bus ────►│ Clara Turn Executor │
│ auth/rate/hb │ │ guardrails + LLM │
└──────┬────────┘ └────────┬─────────────┘
│ │
▼ ▼
┌───────────────┐ ┌──────────────────────┐
│ Guardrails │◄─── policy packs ───►│ LLM Adapter(s) │
│ phoneme, ML │ │ OpenAI, Anthropic… │
└──────┬────────┘ └────────┬─────────────┘
│ │
▼ ▼
┌───────────────┐ ┌──────────────────────┐
│ Tool Runtime │───► DBs (Pg/Redis) │ Telemetry + Metrics │
└───────────────┘ └──────────────────────┘

yaml
Copy code

---

## 2. Crate Structure

backend/
├─ crates/
│ ├─ api # axum routers, WS upgrade, REST endpoints
│ ├─ auth # JWT, HMAC validation
│ ├─ protocol # shared enums, serde models, error codes
│ ├─ session # session registry, concurrency caps
│ ├─ stream # WS hub, heartbeats, backpressure, seq
│ ├─ guardrails # audio-first validation pipeline
│ ├─ llm # provider-agnostic trait + OpenAI impl
│ ├─ tools # plan engine, family assign, printable
│ ├─ store # Postgres + Redis
│ ├─ telemetry # Prometheus metrics + tracing
│ └─ config # typed config + feature flags
└─ bin/clara-stream-server

markdown
Copy code

---

## 3. Request & Turn Lifecycle

1. **Session Create**  
   `POST /v1/clara/session` → `{ sessionId, streamUrl }`
2. **Turn Start**  
   `POST /v1/clara/session/turn` → `{ turnId, streamUrl }`
3. **WebSocket Upgrade**  
   Validate JWT, assign policy/prompt versions.
4. **Audio Stream In**  
   `input.audio.delta` (base64 Opus@24k, ≤20KB, monotonic seq)
5. **Guardrails**  
   VAD, LID, keyword/phoneme spotting, embedding classifiers.
6. **LLM Relay**  
   Forward safe spans to GPT-5; barge-in ≤50 ms.
7. **Metrics & Finish**  
   Record TTFT, tokens, guardrail verdicts → `/metrics`.

---

## 4. Core Modules

### `stream`
- Validates `seq`, payload, and heartbeat.
- Applies backpressure on slow consumers.
- Handles barge-in (`input.interrupt`).

### `session`
- One WS per session.
- 60 s max input / 90 s max output.
- 3 turns/min rate limit.

### `guardrails`
- Runs `VAD → LID → Keyword → Embedding → Digits → Policy`.
- Actions:
  - `BLOCK`: safe reply only.
  - `MASK`: drop subspans.
  - `DOWNGRADE`: strip tool capabilities.
  - `ALLOW`: forward clean audio.

### `llm`
```rust
trait LlmRealtime {
    fn start_turn(&self, id: &str);
    fn send_audio(&self, chunk: AudioChunk);
    fn commit(&self);
    fn interrupt(&self);
}
Implements OpenAI Realtime (or others).

tools
Executes /plan/generate, /revise, /telemetry/complete, /printable, /family/assign.

Validates homeId, memberId, capabilities.

telemetry
Exposes /metrics Prometheus endpoint.

Metrics: connections, TTFT, tokens, guardrail hits, errors, bytes I/O.

5. Data Storage
Store	Purpose
Redis	Sessions, rate counters, temporary state
Postgres	Plans, tasks, telemetry
Object store	Printable PDFs, QR manifests

Raw audio is never stored.

6. Security Model
Zero-trust client.

JWT auth (sid, home_id).

TLS 1.2+; optional HMAC for frames.

PII stripping before any LLM call.

7. SLO Targets
Metric	Target
First-token audio	≤ 350 ms p95
Barge-in stop	≤ 50 ms
WS uptime	≥ 99.9 %
Error budget	≤ 1 % of turns

8. Observability
Metrics: connections, TTFT, guardrail hits, barge-ins, errors.

Tracing: sessionId, turnId.

Logs: structured JSON (no audio).

9. Config & Versioning
Protocol version enforced.

Prompt + policy versions stamped into turn.start / finish.

Feature flags for models, lexicons, providers.

10. Failure Handling
Serve “earcon” if LLM > 800 ms.

Retry once if WS drops pre-output.

Pause reads on backpressure.

11. Integration with OSS Libraries
Library	Integration
llm-security	Policy packs + signed configs
path-security	Validate tool I/O paths
blockchain-runtime	Anchor policy/prompt hashes
worker-capabilities	Turn-level capability masks
module-registry	Hot-load guardrail models / adapters
threat-intel	Update unsafe lexicons / rate limits
quantum-shield	PQ crypto for HMAC/config signing

12. Testing
Golden-set audio clips per category.

Timing tests (TTFT / barge-in).

Chaos: reordered seq, slow LLM.

Capability enforcement checks.

13. Deployment Notes
Sticky sessions for WS.

Pod budget + drain with GOAWAY.

/metrics → Prometheus → Grafana.

14. Public Endpoints
Method	Path	Purpose
POST	/v1/clara/session	Create session
POST	/v1/clara/session/turn	Start turn
GET	/v1/clara/stream	WebSocket stream
GET	/metrics	Prometheus
GET	/health	Liveness

15. Next Steps
Implement guardrails crate.

Replace LLM stub with OpenAI Realtime adapter.

Add tools crate (plan engine, QR, printable).

Integrate OSS libs behind feature flags.

Expand CI with golden-set + timing tests.

Author: CleanFlow / Clara Backend Team
Version: 2025-10-28