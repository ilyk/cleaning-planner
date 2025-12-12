# Clara Streaming Protocol Specification (v0.1)

> **Scope**: Real‑time, low‑latency streaming between the mobile app (client) and backend for Clara voice/text interactions, with the backend relaying to/from the LLM. This spec covers session lifecycle, transports, message framing, sequencing, audio formats, barge‑in, retries, rate limits, security, and observability.

---

## 1. Architecture Overview

**Topology**: `App ⇄ Backend ⇄ LLM`

- The **App** connects to the **Backend** over WebSocket (WS) for bi‑directional, low‑latency streaming (SSE permitted for text‑only receive streams).
- The **Backend** maintains a server‑side session to the LLM and performs all guardrails, tool calls, and moderation. The App never talks to the LLM directly.
- All **validation is backend‑authoritative**; any client hints are non‑binding.

---

## 2. Transport

### 2.1 Modes
- **WebSocket (WS)**: Required for **voice** (bi‑directional audio + control). Recommended for all interactive sessions.
- **SSE (Server‑Sent Events)**: Optional for **text‑only** receive streams where the client does not need to send deltas.

### 2.2 Connection Requirements
- **TLS 1.2+** only; ALPN `h2` or `http/1.1`.
- Max one **active WS per session**. Additional attempts receive `409 CONFLICT`.
- Idle timeout: 45 s without frames from either side → close with policy code `GOING_AWAY`.

### 2.3 Heartbeats
- Client sends `{ "type":"ping" }` every 10 s; server replies `{ "type":"pong" }` within 5 s.
- Missed 3 consecutive heartbeats: server closes with `POLICY_TIMEOUT`.

---

## 3. Authentication & Authorization

- All upgrade requests must include `Authorization: Bearer <JWT>`.
- JWT must include: `sub` (user id), `exp`, `sid` (session id), and optional `homeId` claims.
- Backend derives **capabilities** from user/org policy and attaches to the session context.

---

## 4. Session Lifecycle

```
CREATE (HTTP) → STREAM (WS/SSE) → TURNS (one or more) → CLOSE
```

### 4.1 Create
`POST /v1/clara/session`

**Request**
```json
{
  "mode": "voice|text",
  "device": {"platform":"android","appVersion":"x.y.z"}
}
```
**Response**
```json
{
  "sessionId": "sess_...",
  "streamUrl": "wss://.../v1/clara/stream?sessionId=sess_...",
  "policyVersion": "2025-10-28.1",
  "promptVersion": "2025-10-28.3"
}
```

### 4.2 Start Turn
`POST /v1/clara/session/turn` → `{ turnId, streamUrl }`

### 4.3 Open Stream
Client opens WS to `streamUrl` and sends `turn.start`.

### 4.4 Close
Either side may send `turn.cancel` or close the socket. Server guarantees a final `turn.finish` unless hard network loss.

---

## 5. Message Framing & Ordering

- Framing is **newline‑delimited JSON** (NDJSON) per WS message.
- Each message includes `type`, `ts` (ms since epoch), and optional `seq` (monotonic per direction).
- Audio payloads are **base64** when embedded in JSON.

### 5.1 Base Envelope
```json
{
  "type": "...",
  "ts": 1730131200123,
  "turnId": "turn_...",
  "sessionId": "sess_..."
}
```

### 5.2 Sequencing
- Client messages with audio deltas MUST include `seq` starting at 1 and increment by 1.
- Server enforces monotonic `seq`; out‑of‑order or duplicate frames are dropped and logged.

---

## 6. Event Types

### 6.1 Control
- `turn.start`: Start a user turn.
```json
{ "type":"turn.start", "sessionId":"...", "turnId":"...", "input": {"mode":"voice|text"}, "locale":"en-US" }
```
- `turn.cancel`: Client requests cancellation of current turn.
```json
{ "type":"turn.cancel", "turnId":"..." }
```
- `turn.finish`: Server’s guaranteed terminal message per turn.
```json
{ "type":"turn.finish", "turnId":"...", "usage": {"tokensIn":512, "tokensOut":238}, "latencyMs": 1140 }
```

- `ping` / `pong` as described above.

### 6.2 Voice Input (Client → Server)
- `input.audio.delta` – streaming audio chunks.
```json
{ "type":"input.audio.delta", "seq": 1, "format":"opus@24000/mono/20ms", "data":"<base64>" }
```
- `input.audio.commit` – client stops sending audio for this turn.
```json
{ "type":"input.audio.commit", "seq": 240 }
```
- `input.interrupt` – user barge‑in while assistant is speaking.
```json
{ "type":"input.interrupt" }
```

### 6.3 Text Input (Client → Server)
- `input.text` – for text mode or captions‑assist.
```json
{ "type":"input.text", "text":"make a 20-min bathroom plan", "hints": {"mode":"focus"} }
```

### 6.4 Output (Server → Client)
- `output.audio.start` – the assistant has begun speaking.
```json
{ "type":"output.audio.start", "turnId":"..." }
```
- `output.audio.delta` – streamed PCM samples.
```json
{ "type":"output.audio.delta", "seq": 101, "format":"pcm16@24000/mono", "data":"<base64>" }
```
- `output.audio.commit` – safe to start/finish playback of buffered audio.
```json
{ "type":"output.audio.commit", "seq": 134 }
```
- `output.text.delta` – optional textual captions or UI tips.
```json
{ "type":"output.text.delta", "text":"Let’s focus on the bathroom for 20 minutes." }
```
- `suggestions` – chip suggestions for quick follow‑ups.
```json
{ "type":"suggestions", "chips":["Bathroom 20-min","Single room plan","Print checklist"] }
```

### 6.5 Tools & Results (Server ↔ Model, surfaced to Client)
- `tool.call` – backend informs client a tool is being used (for UI spinners).
```json
{ "type":"tool.call", "callId":"c_123", "tool":"fetchPlan", "args": {"homeId":"h1","mode":"focus"} }
```
- `tool.result` – summarized, sanitized outcome.
```json
{ "type":"tool.result", "callId":"c_123", "result": {"planId":"p_9","tasks": 14} }
```

### 6.6 Guardrails & Notices (Server → Client)
- `guardrail.notice`
```json
{ "type":"guardrail.notice", "code":"POLICY_BLOCK", "message":"I can’t help with that. Want cleaning tips instead?" }
```

### 6.7 Errors (Bidirectional)
```json
{ "type":"error", "code":"BACKEND_TIMEOUT", "message":"Plan generator timed out; showing cached plan.", "retryable": true }
```

---

## 7. Audio Formats

- **Input (preferred)**: `opus@24000/mono/20ms`
- **Output (preferred)**: `pcm16@24000/mono` deltas
- Max payload size: 20 KB per frame.
- Jitter buffer recommendation (client): 80–120 ms adaptive.

---

## 8. Barge‑In & Interrupts

- The client may send `input.interrupt` at any time; upon receipt, the server **must**:
  1) Immediately stop sending `output.audio.delta`.
  2) Issue an interrupt to the model session.
  3) Acknowledge by emitting `output.audio.commit` (final) followed by a new `output.audio.start` (if the turn continues).

---

## 9. Reliability & Backpressure

- **Retry**: If WS drops before any `output.*`, client retries once with same `turnId`; else, show partial message notice.
- **Backpressure**: Server may emit `{ "type":"server.backpressure", "level":"high" }`; client should reduce frame rate or pause capture momentarily.
- **Ordering**: Client enforces monotonic `seq`; gaps > 250 ms or non‑monotonic → send `turn.cancel` and reopen.

---

## 10. Rate Limiting & Quotas

- Per‑user: max 3 turns/min, burst 6.
- Per‑session concurrency: 1.
- Hard caps: input audio 60 s/turn; output audio 90 s/turn.
- Exceeding limits returns `error` with `code":"RATE_LIMIT"` and `retry_after_ms`.

---

## 11. Security

- **TLS** everywhere.
- **JWT** bound to session; rotate on refresh.
- Optional **frame HMAC**:
```json
{ "type":"input.audio.delta", "seq": 42, "mac":"hex(hmac_sha256(k_sess, seq||data))", ... }
```
- **Replays**: server rejects duplicate `(turnId, seq)`.
- **PII**: no raw audio logged; only verdicts/metrics.

---

## 12. Versioning

- Server emits `policyVersion` and `promptVersion` in `turn.start` echo and `turn.finish`.
- Breaking changes bump **major**; clients send `Accept-Protocol: clara/0.1` header on connect.

---

## 13. Observability

- **Metrics**: TTFT (ms), tokens in/out, audio in/out bytes, guardrail hits, error rate, retry rate, barge‑in count.
- **Tracing**: `sessionId`, `turnId`, and `modelRequestId` correlation.
- **Logs**: No raw audio; structured events only.

---

## 14. Error Codes (canonical)

- `UNAUTHENTICATED`, `UNAUTHORIZED`
- `POLICY_BLOCK`, `CAPABILITY_DOWNGRADED`
- `RATE_LIMIT`, `PAYLOAD_TOO_LARGE`, `SEQ_OUT_OF_ORDER`
- `BACKEND_TIMEOUT`, `MODEL_TIMEOUT`, `UPSTREAM_ERROR`
- `NETWORK_ERROR`, `POLICY_TIMEOUT`, `SERVER_OVERLOADED`

---

## 15. Examples

### 15.1 Voice Turn (happy path)
1) Client → `{ "type":"turn.start", ... }`
2) (many) Client → `input.audio.delta`
3) Client → `input.audio.commit`
4) Server → `output.audio.start`
5) (many) Server → `output.audio.delta`
6) Server → `output.audio.commit`
7) Server → `turn.finish`

### 15.2 Barge‑In
- While receiving `output.audio.delta`, client sends `input.interrupt` → server stops output, interrupts model, and accepts new `input.audio.delta` frames.

---

## 16. Conformance Tests (high level)
- Monotonic `seq` acceptance/rejection
- Heartbeat loss closes
- Backpressure signal handling
- Barge‑in within ≤50 ms from client signal
- Guardrail block path never reaches LLM
- Retry semantics idempotent on same `turnId`

---

## 17. Change Log
- v0.1: Initial draft covering voice + text, WS primary, SSE optional for text‑only.

