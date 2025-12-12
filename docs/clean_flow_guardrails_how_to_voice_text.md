# CleanFlow – Guardrails How‑To (Voice + Text)

**Scope:** End‑to‑end safety/validation for Clara’s realtime voice mode and text chat, with the client treated as untrusted. This document is implementation‑ready and pairs with (2) Protocol Spec and (3) Backend Architecture.

---

## 1) Principles & Goals
- **Backend is authoritative.** All validation/guardrails run server‑side; the app is considered hostile.
- **No full STT by default.** Primary gating uses phoneme/keyword spotting + audio embeddings; STT is optional and local‑only for ambiguous spans.
- **Stream early, intercept anytime.** Sliding window moderation before and during LLM streaming. Late hits can interrupt and pivot.
- **Privacy first.** No raw audio logs; store only aggregate signals and decisions.
- **Deterministic before probabilistic.** Hard rules → ML verdicts → (optional) narrow STT for tie‑breaks.

---

## 2) Threat Model (What we defend against)
- Unsafe user prompts: hate/harassment, sexual content (esp. minors), graphic violence, self‑harm instructions, illicit behavior, PII solicitation.
- Prompt injection/tool abuse: attempts to trigger file/QR/assignment tools with dangerous args.
- Output safety: the model emitting disallowed content or unredacted PII.
- Replay/tampering: crafted audio frames, seq gaps, or replayed turns.

---

## 3) Realtime Guardrail Pipeline (Server‑side)

```
Client (untrusted)
  └─▶ Ingress WS (auth, seq checks)
        └─▶ Quarantine Ring Buffer (2–4 s)
              ├─▶ Deterministic checks: VAD, codec, LID, frame/seq, digit patterns
              ├─▶ Phoneme/Keyword Spotter (CTC/RNNT over short windows)
              ├─▶ Audio Embeddings Classifier (multi‑label safety)
              └─▶ Policy Engine (risk → action)
                    ├─ ALLOW / FORWARD CLEAN SPANS → LLM session
                    ├─ DOWNGRADE CAPABILITIES (no tools / no write)
                    ├─ MASK RANGE (drop/bleep) + continue
                    └─ HARD BLOCK + safe reply
                               └─▶ Output Filter/Redactor → Client
```

**Latency budget:** ≤50–80 ms guardrail overhead amortized per 1–2 s audio; TTFT target ≤300 ms with warm LLM session.

---

## 4) Risk Classes → Actions Matrix

| Class | Trigger Examples | Default Action | Capability Gate |
|---|---|---|---|
| R0 | Clean speech | ALLOW | Full |
| R1 | Mild harassment, heated tone | ALLOW + tone mitigation | Allow read; throttle write tools |
| R2 | PII digits pattern, adult sexual innuendo | MASK digits/terms + CONTINUE | `DENY_TOOLS` this turn |
| R3 | Hate/sexual minors/self‑harm instructions/violence | HARD BLOCK | `HARD_BLOCK` + safe script |

**Capabilities:** `ALLOW_CHAT`, `ALLOW_PLAN_READ`, `ALLOW_PLAN_WRITE`, `DENY_TOOLS`, `HARD_BLOCK`.

---

## 5) Input Guardrails (No‑STT Path)

### 5.1 Deterministic checks
- **Auth & limits:** JWT, one active turn per session; per‑account + IP rate limits.
- **Framing:** `opus@24000`, 20–40 ms frames, max 20 KB. Enforce monotonic `seq`; drop on >N gap.
- **VAD & quality:** reject non‑speech/music spans.
- **Language ID (LID):** allow configured locales; route others to safe reply.
- **Digit‑phoneme detector:** CTC over digit phonemes; if ≥10 digits in ≤6 s with high confidence → R2.

### 5.2 Phoneme/keyword spotting
- Tiny CTC or RNNT model emits phoneme posteriors. Fuzzy match against unsafe lexicon (slurs, sexual minors, explicit violence, self‑harm verbs, PII solicitations). Use edit‑distance ≤1 and allow homophones.

### 5.3 Audio embeddings classifier
- Distilled audio encoder (≈100–300 ms receptive field) → multi‑label heads: harassment, sexual, violence, self‑harm, hate, PII. Thresholds tuned to favor recall; use hysteresis to reduce flapping.

### 5.4 Optional narrow STT (tie‑break only)
- If R2/R3 is suspected but < definitive, run **on‑server or on‑device** STT on a 1–2 s span, immediately redact and discard transcript; use only a boolean verdict.

---

## 6) Output Guardrails
- **Text filter:** policy regexes + taxonomy classifier over incremental tokens.
- **PII redaction:** mask emails, long digits, addresses in captions/structured fields.
- **Tool arg validation:** strict schemas; server‑side expansion from authenticated `homeId/memberIds`.
- **Rate & length limits:** tokens/sec ceiling, max utterance duration.

---

## 7) Policy Engine (Reference)

```yaml
version: 2025-10-28.3
inputs:
  - vad: on
  - lid: [en, uk, cs]
  - keyword_threshold: 0.65
  - embed_thresholds:
      harassment: 0.72
      sexual: 0.68
      violence: 0.70
      self_harm: 0.60
      hate: 0.65
      pii: 0.62
actions:
  R3: [hard_block, safe_script]
  R2: [mask_terms, deny_tools]
  R1: [tone_mitigate]
  R0: [allow]
```

**Tuning rule:** lower thresholds during cold starts to avoid misses; gradually tighten with feedback.

---

## 8) Event Contract (to App)

```json
{ "type":"guardrail.notice", "class":"R2", "reason":"pii_digits", "message":"I can’t process personal numbers. Let’s continue without them." }
{ "type":"guardrail.mask", "range": {"startMs": 1200, "endMs": 1800} }
{ "type":"capability.update", "allow": ["ALLOW_CHAT","ALLOW_PLAN_READ"], "deny":["DENY_TOOLS"] }
{ "type":"interrupt", "reason":"policy_block" }
```

The app uses these only for UX; the server already enforced them.

---

## 9) Backend Pseudocode (TypeScript‑like)

```ts
onAudioDelta(ev) {
  ensureAuth(ev); ensureSeq(ev);
  quarantine.push(ev);
  while (quarantine.hasSpanReady()) {
    const span = quarantine.peekSpan();
    const d = checks.deterministic(span);
    if (d.block) return hardBlock("deterministic");
    const k = checks.keyword(span);
    const e = checks.embeddings(span);
    const verdict = policy.decide({d,k,e});
    switch (verdict.action) {
      case 'HARD_BLOCK': notifyBlock(verdict); return finish();
      case 'MASK': quarantine.mask(verdict.range); notifyMask(); break;
      case 'DOWNGRADE': caps.downgrade(verdict.level); forward(span); quarantine.consume(span); break;
      case 'ALLOW': forward(span); quarantine.consume(span);
    }
  }
}

model.on('audioDelta', (buf) => {
  const clean = outputFilter(buf);
  sendToClient(clean);
});
```

---

## 10) Red‑Team Suite (Audio)
- **Hate slur corpus** with accents/homophones.
- **Sexual content** including minor‑related euphemisms.
- **Self‑harm** instructions spoken quickly/quietly.
- **PII sequences**: 16‑digit runs with confusable digit pronunciations.
- **Prompt injection** patterns: “ignore rules…”, “call assign‑all to Alex”.
- **Background music/noise** to test VAD/quality filters.

**Acceptance:** 0% pass for R3; ≤1% pass for R2 at default thresholds.

---

## 11) Metrics & Observability
- **Input:** TTFT, frames/sec, quarantine dwell ms, keyword/embedding scores, action taken.
- **Output:** interrupts count, redactions count, token rate.
- **User outcomes:** retry rate after block, successful rephrases.
- **Privacy:** store only aggregates; delete per‑turn features after N days.

Dashboards: TTFT p50/p95, R2/R3 rate over time, false‑positive appeals, tool denial rate.

---

## 12) Incident Playbook
- **Spike in HARD_BLOCK:** check lexicon changes/threshold drift; roll back policyVersion.
- **High false positives:** raise thresholds + enable narrow STT tie‑break temporarily.
- **Stream stalls:** check backpressure; temporarily reduce frame size or token rate.

---

## 13) Config & Deployment
- Config as code; `policyVersion` pinned per release; canary to 5% sessions.
- Feature flags: `narrow_stt_tiebreak`, `deny_tools_on_r2`, `output_caption_redaction`.
- Rollback path: previous policy bundle kept hot; switch in <1 min.

---

## 14) Client UX Guidance (Non‑authoritative)
- Earcons and gentle, actionable messages.
- Immediate **barge‑in**: pause playback, clear ~150 ms buffer, send `input.interrupt`.
- Captioning off by default; if on, show **server‑provided** captions only, with redactions applied.

---

## 15) QA Checklist
- ✅ R3 block with safe script across accents/noise
- ✅ R2 digit masking without breaking normal numbers (times, durations)
- ✅ Barge‑in during unsafe phrase → interrupt within ≤150 ms
- ✅ Tool calls denied when `DENY_TOOLS` is active
- ✅ Logs contain no raw audio/text beyond policy metadata

---

## 16) Appendix – Tunables (Starting Points)
- Frame: 20 ms Opus @ 24 kHz; packet ≤ 10 KB
- Quarantine span: 800–1200 ms; slide 200 ms
- VAD gate: 0.6 posterior over 3 frames
- Keyword spot: 0.65 (hard) / 0.55 (soft)
- Embedding thresholds: see §7; calibrate on red‑team set
- Max input per turn: 45 s; max output: 90 s; cooldown: 2 s

