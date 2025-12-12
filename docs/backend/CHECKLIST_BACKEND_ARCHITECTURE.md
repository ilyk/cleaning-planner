# ✅ Clara Backend Architecture — QA & Operations Checklist

**Scope:** (3) Backend Architecture  
**Status:** Post-implementation verification  
**Last Updated:** 2025-10-29

---

## 🧱 1. Core Build Verification

| Check | Command | Expected |
|-------|---------|----------|
| Workspace builds cleanly | `cargo build --workspace --all-features` | ✅ no warnings |
| Tests pass | `cargo test --workspace` | ✅ all green |
| Format/lint | `cargo fmt --all --check` / `cargo clippy --all-targets -- -D warnings` | ✅ clean |
| Migrations | `make migrate` | ✅ DB tables created |
| Feature flags compile | see Feature Flags Matrix | ✅ each combination builds |

### Feature Flags Matrix

| Feature Set | Purpose | Must Compile |
|-------------|---------|--------------|
| `default` | mock LLM + basic guardrails | ✅ |
| `openai_realtime` | real GPT-5 streaming | ✅ |
| `use-llm-security` | policy pack integration | ✅ |
| `use-pdf,use-path-security` | full toolchain | ✅ |
| `all features together` | full production | ✅ |

---

## 🧭 2. Local Environment Bring-up

### Prerequisites
- Rust ≥ 1.80
- Docker ≥ 24.0
- Environment vars:
  - `OPENAI_API_KEY=sk-...` (optional for dev)
  - `JWT_SECRET=dev_secret`
  - `DATABASE_URL=postgres://postgres:postgres@localhost:5432/clara`
  - `REDIS_URL=redis://localhost:6379`

### Quick Start

```bash
# One command setup
make setup

# Or step-by-step:
make docker-up    # Start Postgres + Redis
make migrate      # Run migrations
make dev          # Start server with all features
```

### Health Check

```bash
curl http://localhost:8080/health
# → {"status":"ok","version":"2025.10.29"}

curl http://localhost:8080/health/details
# → {"status":"ok","features":[...],"policy_version":"...","prompt_version":"..."}
```

---

## 🔒 3. Security Verification

| Area | Validation | Command |
|------|------------|---------|
| JWT Auth | All endpoints reject missing/invalid tokens | `curl -X POST http://localhost:8080/v1/clara/session` → 401 |
| Frame HMAC (if enabled) | Tampered frames → `AUTH_FAILED` | See WebSocket tests |
| Max payload | >20 KB frame → `PAYLOAD_TOO_LARGE` | `python3 examples/generate_opus_tone.py 1` with large data |
| Session isolation | Multiple WS per session blocked | Multiple `wscat` connections → only one succeeds |
| Rate limit | >3 turns/min → `RATE_LIMITED` | Rapid-fire turn creation → rate limit response |
| No PII leakage | Search logs for `data_b64` | `grep -r "data_b64" logs/` → should be absent |
| TLS enforced | Backend terminates HTTPS/wss only | (Production deployment) |

---

## 🧠 4. Guardrails QA

### Golden Test Fixtures

Run: `cargo test -p clara-guardrails`

| Scenario | Expected Verdict | Test |
|----------|------------------|------|
| Clean speech | `ALLOW` | `allows_clean_request()` |
| Digits sequence | `MASK` | `masks_digit_sequence()` |
| Unsafe keyword | `BLOCK` | `blocks_hate_speech_sample()` |
| Borderline tone | `DOWNGRADE` | `downgrades_risky_tone()` |
| Unknown language (LID) | `BLOCK` | `blocks_disallowed_language()` |
| Silent/no speech | `ALLOW` | `allows_silent_audio()` |

### Runtime Flag Toggle

```bash
# Without llm-security
cargo run -p clara-stream-server

# With llm-security
cargo run -p clara-stream-server --features use-llm-security

# Verify: thresholds should differ between runs
```

### Policy Pack Version

```bash
# Start turn and check logs
grep "policy_version" logs/clara.log
# → Should show policy pack version in turn.start/turn.finish
```

---

## 🔄 5. LLM Realtime Adapter QA

### Simulated Flow

```bash
# 1) Generate JWT
make jwt

# 2) Create session
SESSION=$(curl -s -H "Authorization: Bearer $TOKEN" \
  -X POST http://localhost:8080/v1/clara/session | jq -r '.sessionId')

# 3) Start turn
TURN=$(curl -s -H "Authorization: Bearer $TOKEN" \
  -X POST http://localhost:8080/v1/clara/session/turn \
  -d '{"mode":"focus"}' | jq -r '.turnId')

# 4) Connect WS and stream
wscat -c "ws://localhost:8080/v1/clara/stream?sessionId=$SESSION&turnId=$TURN" \
  -H "Authorization: Bearer $TOKEN"
```

### Expected Message Sequence

**Send:**
```json
{"type":"turn.start","sessionId":"...","turnId":"..."}
{"type":"input.audio.delta","seq":1,"format":"opus@24000","data":"<b64>"}
{"type":"input.audio.commit","seq":1}
```

**Receive:**
- `OutputAudioStart`
- Multiple `OutputAudioDelta` events
- `OutputAudioCommit`
- `TurnFinish` with usage stats

### Barge-in Test

```json
{"type":"input.interrupt"}
```

**Verify:** Last `OutputAudioDelta` timestamp ≤ 50ms after interrupt log entry.

---

## ⚙️ 6. Tools & Store

| Function | Check | Command |
|----------|-------|---------|
| Plan generate/revise | Returns valid JSON structure | `cargo test -p clara-tools` |
| Printable plan | PDF file written to `/tmp/clara/printables/...` | Check filesystem after tool call |
| Family assignment | Member validation, proper UUID handling | `cargo test -p clara-tools --test capability_gating` |
| DB persistence | Inserts visible in Postgres | `psql $DATABASE_URL -c "SELECT * FROM plans LIMIT 5;"` |

### Capability Gating Tests

Run: `cargo test -p clara-tools --test capability_gating`

- ✅ `ALLOW_CHAT` → plan read allowed, write denied
- ✅ `ALLOW_PLAN_WRITE` → revise OK; home/member claims validated
- ✅ `DENY_TOOLS` → all tool calls rejected with `POLICY_BLOCK` code

---

## 📈 7. Metrics & Observability

| Metric | Purpose | Verify |
|--------|---------|--------|
| `clara_ttft_ms` | First token latency | Histogram increasing |
| `clara_guardrail_verdicts_total{action}` | Safety stats | Increments with turns |
| `clara_ws_connections` | Active streams | Reflects client count |
| `clara_errors_total{code}` | Reliability | Stable under load |
| `/metrics` | Prometheus scrape | HTTP 200 |
| `/health/details` | Feature flags + versions | Shows correct features |

### Grafana Panels (Optional)

See `docs/grafana-dashboard.json` for:
- **Realtime Health** → TTFT p95, WS active count
- **Safety** → Guardrail actions stacked by category
- **Tools** → Printable + family calls/min

---

## 🧩 8. Integration of OSS Libraries

| Library | Verify | Command |
|---------|--------|---------|
| `llm-security` | Policy packs load OK, telemetry logs version | `cargo run --features use-llm-security` + check logs |
| `path-security` | Blocks bad paths (`/etc/passwd`) | Tool call with invalid path → error |
| `worker-capabilities` | Denies restricted tools | `cargo test --test capability_gating` |
| `module-registry` | Hot-reload guardrail lexicon works | (Manual admin endpoint test) |
| `threat-intel` | Adds blocked IPs | (Manual IP reputation test) |
| `quantum-shield` | PQ HMAC verifies | (With feature enabled) |

**Critical:** Backend must compile and run even if **all OSS features disabled**.

---

## 🧪 9. Performance Smoke Tests

| Test | Goal | Target |
|------|------|--------|
| 10 concurrent WS | Stability | No crashes |
| p95 TTFT | Latency | < 350ms |
| p95 barge-in stop | Responsiveness | < 50ms |
| CPU (single pod) | Efficiency | < 70% |
| Memory footprint | | < 300MB steady |

### Load Test (Manual)

```bash
# Run 10 concurrent clients
for i in {1..10}; do
  bash examples/smoke_test.sh &
done
wait

# Check metrics
curl http://localhost:8080/metrics | grep clara_ttft_ms
```

---

## 🪪 10. Deployment & Rollout

### Staging Plan

1. **Baseline:** All features off → measure latency
2. **Canary:** Enable `use-llm-security` (10% traffic) → watch false-positive rate
3. **Full Prod:** All features on

### Feature Toggles

```bash
# Via environment
CLARA_FEATURES=openai_realtime,use-llm-security,use-pdf,use-path-security

# Or via Cargo features
cargo build --features openai_realtime,use-llm-security,use-pdf,use-path-security
```

### Killswitch

```bash
# Disable feature via env
CLARA_DISABLE_OPENAI_REALTIME=true
CLARA_DISABLE_LLM_SECURITY=true

# Check health endpoint
curl http://localhost:8080/health/details
```

---

## 🧾 11. Documentation Completeness

| Doc | Exists | Verified |
|-----|--------|----------|
| `docs/architecture.md` | ✅ | Up-to-date |
| `README.md` in each crate | ✅ | Concise purpose |
| `docs/adr/` | ✅ | 4 core ADRs |
| `CHECKLIST_BACKEND_ARCHITECTURE.md` | ✅ | This file |
| `Makefile` | ✅ | One-command dev |
| `docker-compose.yml` | ✅ | Postgres + Redis |
| `examples/` | ✅ | Smoke test + Opus generator |

---

## ✅ Final Readiness Criteria

- [ ] All integration tests pass
- [ ] `/metrics` and `/health` OK
- [ ] Guardrails + LLM streaming validated
- [ ] PDF + family tools functional
- [ ] Rate limits, auth, and security enforced
- [ ] Observability dashboards live (if Grafana configured)
- [ ] All crates documented
- [ ] Build reproducible with `make dev`
- [ ] Smoke test passes end-to-end
- [ ] Golden tests assert expected verdicts

---

**Owner:** Backend Team (Rust)  
**Component:** (3) Backend Architecture  
**Version:** 2025-10-29  
**Status:** ✅ Ready for Staging

---

## 📝 Quick Reference

```bash
# Full setup
make setup

# Run tests
make test

# Health check
make health

# Smoke test
make smoke-test

# Generate Opus frames
python3 examples/generate_opus_tone.py 10
```

