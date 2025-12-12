# CleanFlow API Reference & Data Model (v0.9)

_Last updated: 2025-10-29_

**Base URL**
- Production: `https://api.cleanflow.app/v1`
- Staging: `https://staging.cleanflow.app/v1`

**Auth**
- Header: `Authorization: Bearer <JWT>`
- Optional: `X-Client-Version: <semver>`
- Optional: `Idempotency-Key: <uuid4>` for create/mutate endpoints

**Content**
- Requests/Responses: `application/json; charset=utf-8`
- Streaming: `text/event-stream` (SSE) or WebSocket as defined in Protocol Spec (separate doc)

**Versioning**
- Header: `X-Prompt-Version: <string>` (surfaced for telemetry)
- Header: `X-Policy-Version: <string>`

**Errors**
```json
{
  "error": {
    "code": "PLAN_NOT_FOUND",
    "message": "Plan p_123 not found",
    "details": {"planId": "p_123"},
    "requestId": "req_9Yt..."
  }
}
```
- Common codes: `UNAUTHORIZED`, `FORBIDDEN`, `RATE_LIMITED`, `VALIDATION_FAILED`, `CONFLICT`, `NOT_FOUND`, `INTERNAL`

**Pagination**
- Cursor-based: `?limit=50&cursor=abc`
- Response fields: `items`, `nextCursor`

**Idempotency**
- For create/mutate POST/PATCH: send `Idempotency-Key`; server ensures single effect within 24h window.

---

## 1) Plan APIs

### POST /plan/generate
Generate or fetch the canonical plan for a home/date/mode.

**Request**
```json
{
  "homeId": "h_01h9",
  "date": "2025-10-29",
  "mode": "focus",        
  "constraints": {
    "timeboxMinutes": 45,
    "rooms": ["r_kitchen"],
    "include": ["wipe_counters"],
    "exclude": ["deep_clean_oven"]
  },
  "client": {"locale": "en-US", "tz": "America/Los_Angeles"}
}
```
**Response**
```json
{
  "planId": "p_01mx",
  "homeId": "h_01h9",
  "date": "2025-10-29",
  "mode": "focus",
  "sections": [
    {
      "id": "s_now",
      "title": "Now",
      "tasks": ["t_8x1", "t_8x2"]
    },
    {
      "id": "s_next",
      "title": "Next",
      "tasks": ["t_9p1"]
    }
  ],
  "tasks": [
    {
      "taskId": "t_8x1",
      "templateId": "tmpl_wipe_counters",
      "roomId": "r_kitchen",
      "title": "Wipe kitchen counters",
      "estimateMin": 7,
      "state": "pending",
      "assignee": {"memberId": "m_dad", "name": "Alex"}
    }
  ],
  "version": 12,
  "promptVersion": "2025-10-28.3",
  "policyVersion": "2025-10-27.1",
  "cached": true
}
```

### POST /plan/revise
Apply user edits (reorder, postpone, reassign) and reconcile canonical plan.

**Request**
```json
{
  "planId": "p_01mx",
  "edits": [
    {"op": "reorder", "taskId": "t_8x1", "afterTaskId": "t_8x2"},
    {"op": "postpone", "taskId": "t_9p1", "toDate": "2025-10-30"},
    {"op": "assign", "taskId": "t_8x1", "memberId": "m_kid"}
  ]
}
```
**Response** → Updated plan (same shape as `/plan/generate`).

### GET /plan/{planId}
Fetch a plan by id.

### POST /plan/printable
Create a printable PDF and map QR codes to tasks.

**Request**
```json
{
  "planId": "p_01mx",
  "options": {"paperSize": "A4", "kidsFriendly": true, "qrDensity": "per-task"}
}
```
**Response**
```json
{
  "exportId": "x_7zk",
  "pdfUrl": "https://cdn.cleanflow.app/exports/x_7zk.pdf",
  "qr": [ {"taskId": "t_8x1", "qrId": "qr_001"} ]
}
```

### POST /family/assign
Bulk assignments for a plan.

**Request**
```json
{"planId": "p_01mx", "assignments": [{"taskId": "t_8x1", "memberId": "m_kid"}]}
```
**Response** → Updated plan slice for assignments.

### POST /telemetry/complete
Record completion/skip durations and optional comments.

**Request**
```json
{
  "taskId": "t_8x1",
  "status": "done",            
  "durationSec": 420,
  "comment": "Sticky stain took longer",
  "source": "qr"
}
```
**Response**
```json
{"ok": true, "telemetryId": "te_9aa"}
```

---

## 2) Clara Voice APIs (summary)
_Full wire format in Protocol Spec doc._

### POST /clara/session
Create or resume an authenticated voice session.

**Response**
```json
{"sessionId": "cs_01ab", "streamUrl": "wss://api.cleanflow.app/v1/clara/stream?sessionId=cs_01ab"}
```

### POST /clara/session/turn
Start a new turn and obtain stream URL bound to the turn.

**Request**
```json
{"sessionId": "cs_01ab", "user": {"mode": "focus"}}
```
**Response**
```json
{"turnId": "ct_77f", "streamUrl": "...&turnId=ct_77f"}
```

### GET /clara/stream (SSE/WS)
Bi-directional audio/text stream. Events: `turn.start`, `input.audio.delta`, `input.interrupt`, `output.audio.delta`, `output.text.delta`, `output.audio.commit`, `turn.finish`, `error`.

### POST /clara/cancel
Cancel an in-flight turn.

**Request** `{ "turnId": "ct_77f" }` → **Response** `{ "ok": true }`

---

## 3) Lookup & Metadata APIs

### GET /homes/{homeId}
Return home profile with members and rooms.

### GET /task-templates?homeId=…
List templates applicable to a home (with estimates, tools, frequencies).

### GET /plans?homeId=…&date>=…&limit=…&cursor=…
List plans (cursor pagination).

---

# Data Model

## Overview
Relational core (PostgreSQL) with document sidecars (JSONB) for LLM artifacts. Event streams go to Kafka (or cloud equivalent). Key entities:
- **Home**, **Member**, **Room**
- **TaskTemplate** (canonical task definitions)
- **Plan** (per home/day/mode) and **PlanTask** (task instances in a plan)
- **Assignment** (task → member per plan)
- **TelemetryEvent** (done/skip/time)
- **PrintableExport** (PDF/QR mapping)
- **ClaraSession** / **ClaraTurn** (voice sessions/turns)
- **PolicyVersion**, **PromptVersion** (audit)

## Entity Tables

### homes
| column | type | notes |
|---|---|---|
| id | text PK | `h_*` |
| owner_user_id | text | FK to users (auth realm) |
| name | text | |
| tz | text | IANA zone |
| locale | text | e.g., `en-US` |
| created_at | timestamptz | |
| updated_at | timestamptz | |

**Indexes**: `(owner_user_id)`

### members
| column | type | notes |
|---|---|---|
| id | text PK | `m_*` |
| home_id | text | FK homes.id |
| name | text | display name |
| role | text | enum: `adult`, `kid`, `guest`, `pet_proxy` |
| avatar_url | text | |
| created_at | timestamptz | |

**Indexes**: `(home_id)`

### rooms
| column | type | notes |
|---|---|---|
| id | text PK | `r_*` |
| home_id | text | FK homes.id |
| name | text | |
| kind | text | enum: `kitchen`, `bathroom`, `bedroom`, `living`, `other` |
| metadata | jsonb | dimensions, surfaces, materials |

**Indexes**: `(home_id, kind)`

### task_templates
| column | type | notes |
|---|---|---|
| id | text PK | `tmpl_*` |
| title | text | human label |
| default_estimate_min | int | |
| room_kind | text | nullable; if specific |
| frequency | text | cron-like or rule JSON |
| tools | jsonb | required supplies |
| policy_tags | text[] | e.g., `sharp_objects` |
| i18n | jsonb | localized titles/copy |

### plans
| column | type | notes |
|---|---|---|
| id | text PK | `p_*` |
| home_id | text | FK homes.id |
| date | date | plan day |
| mode | text | enum: `focus`, `full_reset`, `low_energy`, `pet` |
| sections | jsonb | [{id,title,tasks[]}] (for quick reads) |
| version | int | increments on revise |
| prompt_version | text | |
| policy_version | text | |
| cached | boolean | from cache or freshly generated |
| created_at | timestamptz | |
| updated_at | timestamptz | |

**Uniq**: `(home_id, date, mode)`

### plan_tasks
| column | type | notes |
|---|---|---|
| id | text PK | `t_*` |
| plan_id | text | FK plans.id |
| template_id | text | FK task_templates.id |
| room_id | text | FK rooms.id |
| title | text | may be LLM-rewritten |
| estimate_min | int | final estimate |
| state | text | enum: `pending`, `in_progress`, `done`, `skipped` |
| priority | int | ordering within section |
| section_id | text | `s_now`, `s_next`, `s_later` |
| assignee_member_id | text | FK members.id |
| metadata | jsonb | notes, deltas, audit |

**Indexes**: `(plan_id, section_id, priority)`

### assignments
| column | type | notes |
|---|---|---|
| id | text PK | `a_*` |
| plan_id | text | FK plans.id |
| task_id | text | FK plan_tasks.id |
| member_id | text | FK members.id |
| created_at | timestamptz | |

**Uniq**: `(task_id, member_id)`

### telemetry_events
| column | type | notes |
|---|---|---|
| id | text PK | `te_*` |
| task_id | text | FK plan_tasks.id |
| kind | text | enum: `done`, `skip` |
| duration_sec | int | nullable for skip |
| comment | text | optional |
| source | text | `app`, `qr`, `api` |
| created_at | timestamptz | |

**Indexes**: `(task_id, created_at)`

### printable_exports
| column | type | notes |
|---|---|---|
| id | text PK | `x_*` |
| plan_id | text | FK plans.id |
| pdf_url | text | CDN url |
| options | jsonb | paper size, kids mode |
| qr_map | jsonb | [{taskId, qrId}] |
| created_at | timestamptz | |

### clara_sessions
| column | type | notes |
|---|---|---|
| id | text PK | `cs_*` |
| user_id | text | auth id |
| home_id | text | default home |
| state | text | enum: `active`, `ended` |
| created_at | timestamptz | |
| ended_at | timestamptz | |

### clara_turns
| column | type | notes |
|---|---|---|
| id | text PK | `ct_*` |
| session_id | text | FK clara_sessions.id |
| policy_version | text | |
| prompt_version | text | |
| started_at | timestamptz | |
| finished_at | timestamptz | |
| usage | jsonb | token counts, cache hits |
| verdicts | jsonb | guardrail categories (no audio) |

---

## Enums (logical)
- **PlanMode**: `focus`, `full_reset`, `low_energy`, `pet`
- **TaskState**: `pending`, `in_progress`, `done`, `skipped`
- **MemberRole**: `adult`, `kid`, `guest`, `pet_proxy`
- **TelemetryKind**: `done`, `skip`

---

## Data Flows
1) **Generate Plan** → write (upsert) `plans` + `plan_tasks`; bump `version`.
2) **Revise Plan** → transactional updates to `plan_tasks` ordering/assignees; append `assignments` deltas.
3) **Complete/Skip** → append `telemetry_events`; derive analytics asynchronously.
4) **Printable Export** → create `printable_exports`; upload PDF; map `qr_map`.
5) **Clara Turn** → create `clara_turns`; stream-only artifacts live in object store; only structured `usage/verdicts` in DB.

---

## Indexing & Performance
- Hot paths: `(home_id, date, mode)` on `plans`; `(plan_id, section_id, priority)` on `plan_tasks`.
- Covering index for telemetry queries: `(task_id, created_at)`.
- CDN for PDFs/exports; signed URLs expire in 24h.
- Cache key for plan: `plan:{homeId}:{date}:{mode}:v{version}`.

---

## Security & Privacy Notes
- No raw audio stored; only aggregate `verdicts`/metrics.
- Redact PII in `comments` server-side before persistence.
- Row-level access: all reads/mutates scoped to `home_id` owned/authorized by the caller.

---

## Changelogs
- v0.9: Initial publication of API Reference + Data Model.

