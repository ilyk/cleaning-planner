# CLEANFLOW_SYNC_CONTRACT

## 1. Overview

This document defines the **canonical JSON contract** used between the Android CleanFlow app and the Clara backend for:

- **Home**: household profile / configuration
- **Plan**: generated daily/weekly cleaning plans
- **HistoryEntry**: behavioural telemetry (completions, skips, durations, notes)
- **Suggestion**: optimization suggestions from local and cloud AI

The goal is to keep the **Android domain models** (`core/model/domain`) and the **backend domain models** (`backend/crates/domain`) conceptually aligned while allowing each side to evolve independently.

- JSON field names are **snake_case**.
- Kotlin/Android types use **camelCase** and map via DTOs.
- Rust/backend types use `serde` with explicit field names or `rename_all = "snake_case"`.

Unless otherwise noted, all fields are **optional** for forward-compatibility; new fields must be added in a way that older clients and servers can ignore.

Assumptions used for this first version:

- **Identity model**: Anonymous `home_id` issued by the backend (no full user accounts yet).
- **Cloud opt-in**: Disabled by default; users explicitly enable cloud optimization in Settings.
- **Adaptation aggressiveness**: Backend produces **suggestions**; the app applies changes only after user approval.
- **Model provider**: OpenAI (GPT‑5.x) behind a pluggable `llm` abstraction (can support other vendors later).
- **Data retention**: Implementation detail of the backend; contracts do not assume specific retention windows.

---

## 2. Home Profile Payload

Used by:

- `POST /v1/cleanflow/home` (register or update home profile)
- Included as context in plan/optimization requests.

```json
{
  "home_id": "h_ab12cd34",
  "profile": {
    "name": "Smith Family Home",
    "rooms": [
      {
        "id": "kitchen",
        "name": "Kitchen",
        "kind": "kitchen"
      },
      {
        "id": "living_room",
        "name": "Living room",
        "kind": "living"
      }
    ],
    "floors": 2,
    "has_pets": true,
    "devices": [
      "robot_vacuum",
      "air_purifier"
    ],
    "constraints": {
      "quiet_hours": {
        "start": "21:00",
        "end": "07:00"
      },
      "allergies": ["dust", "pollen"],
      "dont_touch_areas": ["dad_desk", "electronics_drawer"]
    },
    "preferences": {
      "mode": "minimalist",          
      "cloud_opt_in": true,
      "auto_optimize": false,
      "max_daily_minutes": 90,
      "kid_friendly": true
    }
  }
}
```

### 2.1 Field Mapping

- **Android**
  - `UserProfile` (`core/model/domain/UserProfile.kt`)
    - `name` → `profile.name`
    - `rooms: List<String>` → `profile.rooms[*].name` (ids derived client-side)
    - `floors` → `profile.floors`
    - `hasPets` → `profile.has_pets`
    - `devices: List<String>` → `profile.devices`
    - `preference` (`Minimalist` / `FullControl`) → `profile.preferences.mode`
  - Additional fields like `constraints` and enhanced `preferences` live in a separate DTO/metadata object and are **optional**.

- **Backend**
  - `Home` / `Room` (`backend/crates/domain/src/models.rs`)
    - `Home.id` ↔ `home_id`
    - `Room.id`, `Room.name`, `Room.kind` ↔ `profile.rooms[*]`
  - Any extra fields are stored in `metadata` columns (JSONB) where available.

---

## 3. Plan Payload

Used by:

- `POST /v1/cleanflow/home/initial-plan` (response body)
- `POST /v1/cleanflow/plan/optimize` (response body / polling)

This payload is conceptually aligned with `GeneratePlanResponse` and `Plan`/`PlanTask` in `backend/crates/domain/src/models.rs`, plus additional metadata useful for the Android client.

```json
{
  "plan_id": "p_12ab34cd",
  "home_id": "h_ab12cd34",
  "date": "2025-11-17",
  "mode": "focus",
  "sections": [
    {
      "id": "s_morning",
      "title": "Morning reset",
      "tasks": ["t_dishes", "t_counters"]
    }
  ],
  "tasks": [
    {
      "task_id": "t_dishes",
      "template_id": "tmpl_dishes_daily",
      "room_id": "kitchen",
      "title": "Do the dishes",
      "estimate_min": 15,
      "state": "pending",
      "priority": 10,
      "section_id": "s_morning",
      "assignee": {
        "member_id": "m_mom",
        "name": "Alex"
      },
      "metadata": {
        "difficulty": "medium",
        "energy_level": "low",
        "required_supplies": ["dish_soap", "sponge"],
        "recurrence": "daily",
        "origin": "cloud_ai"        
      }
    }
  ],
  "version": 1,
  "prompt_version": "cleanflow-v1",
  "policy_version": "safety-v1",
  "cached": false
}
```

### 3.1 Field Mapping

- **Android**
  - Local `Task` (`core/model/domain/Task.kt`)
    - `id` ↔ `tasks[*].task_id`
    - `title` ↔ `tasks[*].title`
    - `room` ↔ `tasks[*].room_id`
    - `estimatedMin` ↔ `tasks[*].estimate_min`
    - `assigneeId` ↔ `tasks[*].assignee.member_id`
    - `dueDate` ↔ `date`
    - `timeOfDay` ↔ derived from `section_id` or `metadata`
    - `status` ↔ `tasks[*].state`

  - Additional attributes like `difficulty`, `energy_level`, `required_supplies`, `recurrence`, `origin` are stored in:
    - `metadata` JSON on the backend, and
    - additional Room columns or a separate `task_metadata` table on Android (see schema docs).

- **Backend**
  - `Plan` / `PlanTask` / `PlanSection` in `backend/crates/domain/src/models.rs` map 1:1 to the top-level fields above.
  - The backend may store richer internal metadata; the contract only requires fields that the client needs.

---

## 4. HistoryEntry Payload

Used by:

- `POST /v1/cleanflow/history/batch` (ingestion from devices)
- `GET /v1/cleanflow/history/summary` (aggregated; may use a different response type)

```json
{
  "home_id": "h_ab12cd34",
  "device_id": "android_1234abcd",
  "entries": [
    {
      "task_id": "t_dishes",
      "date": "2025-11-17",
      "status": "done",
      "duration_min": 12,
      "note": "Kids helped",
      "origin": "app",          
      "source": "home_screen",  
      "created_at": "2025-11-17T18:35:00Z"
    },
    {
      "task_id": "t_counters",
      "date": "2025-11-17",
      "status": "skipped",
      "duration_min": null,
      "note": "Too tired",
      "origin": "voice",
      "source": "qr_scan",
      "created_at": "2025-11-17T18:40:00Z"
    }
  ]
}
```

### 4.1 Field Mapping

- **Android**
  - `HistoryEntry` (`core/model/domain/HistoryEntry.kt`)
    - `taskId` ↔ `task_id`
    - `date` ↔ `date`
    - `status` ↔ `status`
    - `durationMin` ↔ `duration_min`
    - `note` ↔ `note`
  - Additional fields to add on Android side:
    - `origin`: enum (`app`, `qr`, `ocr`, `voice`, `other`)
    - `deviceId`: stable per-install identifier (non-PII)
    - `source`: optional string describing UI entry point (`home_screen`, `kids_mode`, `paper_bridge`, etc.)

- **Backend**
  - Conceptually aligned with `TelemetryEvent` and `TelemetryCompleteRequest` in `backend/crates/domain/src/models.rs`:
    - `task_id` ↔ `task_id`
    - `duration_min` ↔ `duration_sec` (store both or convert)
    - `note` ↔ `comment`
    - `source` ↔ `source`
  - Backend persists the full record (including `origin`, `device_id`) for analysis.

---

## 5. Suggestion Payload

Used by:

- `GET /v1/cleanflow/plan/suggestions` (list suggestions for a home/plan)
- `POST /v1/cleanflow/plan/optimize` (may return suggestions inline or via polling)

```json
{
  "home_id": "h_ab12cd34",
  "plan_id": "p_12ab34cd",
  "suggestions": [
    {
      "id": "sg_1",
      "text": "Move deep bathroom cleaning to Sunday when you have more time.",
      "confidence": 87,
      "action": "change_frequency",
      "source": "cloud_ai",              
      "target": {
        "task_id": "t_bathroom_deep",
        "field": "recurrence",
        "proposed_value": "weekly_sunday"
      },
      "state": {
        "accepted": false,
        "dismissed": false,
        "applied_locally": false
      }
    }
  ]
}
```

### 5.1 Field Mapping

- **Android**
  - `Suggestion` (`core/model/domain/Suggestion.kt`)
    - `id` ↔ `suggestions[*].id`
    - `text` ↔ `suggestions[*].text`
    - `confidence` ↔ `suggestions[*].confidence`
    - `action` ↔ `suggestions[*].action`
  - Existing Room schema also stores flags like `isAccepted` / `isDismissed` (per `CLEANFLOW_IMPLEMENTATION_COMPLETE.md`):
    - Map to `state.accepted` / `state.dismissed`.
  - New fields:
    - `source`: `local_ai` or `cloud_ai`
    - `target`: structured payload describing what the suggestion would change.

- **Backend**
  - Backend suggestion models (e.g., in `crates/tools` or `crates/domain`) should mirror this structure so suggestions can be:
    - generated by GPT‑5.x,
    - stored alongside plan/history context,
    - consumed by the app in a consistent way.

---

## 6. Versioning & Compatibility

- All top-level payloads SHOULD include a **version field** once the contract stabilizes, e.g. `"schema_version": "v1"`.
- New fields must be added as **optional** and documented here.
- Breaking changes require bumping the version and supporting both variants during migration.

This document is the source of truth for the CleanFlow sync JSON contracts; Kotlin and Rust types should be kept in sync with it, but can have additional internal-only fields as needed.