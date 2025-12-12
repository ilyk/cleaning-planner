# CleanFlow API Reference & Data Model v0.9 - Spec Audit Report

_Generated: 2025-01-29_

## Executive Summary

This audit compares the current Rust implementation against the CleanFlow API Reference & Data Model v0.9 specification. The current implementation is primarily focused on Clara voice streaming functionality and lacks most of the core CleanFlow API endpoints and data model.

**Overall Compliance: 15%** (3/20 endpoints implemented, 0/11 tables match spec)

## API Endpoints Audit

### Plan APIs
| Endpoint | Method | Path | Status | Notes |
|----------|--------|------|--------|-------|
| Generate Plan | POST | `/plan/generate` | ❌ **MISSING** | Core functionality not implemented |
| Revise Plan | POST | `/plan/revise` | ❌ **MISSING** | Core functionality not implemented |
| Get Plan | GET | `/plan/{planId}` | ❌ **MISSING** | Core functionality not implemented |
| Printable Plan | POST | `/plan/printable` | ❌ **MISSING** | PDF generation not implemented |
| Family Assign | POST | `/family/assign` | ❌ **MISSING** | Bulk assignment not implemented |
| Telemetry Complete | POST | `/telemetry/complete` | ❌ **MISSING** | Task completion tracking not implemented |

### Clara Voice APIs
| Endpoint | Method | Path | Status | Notes |
|----------|--------|------|--------|-------|
| Create Session | POST | `/clara/session` | ✅ **IMPLEMENTED** | Matches spec, returns sessionId and streamUrl |
| Start Turn | POST | `/clara/session/turn` | ✅ **IMPLEMENTED** | Matches spec, returns turnId and streamUrl |
| Stream | GET | `/clara/stream` | ✅ **IMPLEMENTED** | WebSocket upgrade, matches spec |
| Cancel Turn | POST | `/clara/cancel` | ❌ **MISSING** | Cancel functionality not implemented |

### Lookup & Metadata APIs
| Endpoint | Method | Path | Status | Notes |
|----------|--------|------|--------|-------|
| Get Home | GET | `/homes/{homeId}` | ❌ **MISSING** | Home profile not implemented |
| Get Task Templates | GET | `/task-templates` | ❌ **MISSING** | Template listing not implemented |
| List Plans | GET | `/plans` | ❌ **MISSING** | Plan listing with pagination not implemented |

## Data Model Audit

### Database Tables
| Table | Status | Notes |
|-------|--------|-------|
| `homes` | ❌ **MISSING** | No table exists |
| `members` | ❌ **MISSING** | No table exists |
| `rooms` | ❌ **MISSING** | No table exists |
| `task_templates` | ❌ **MISSING** | No table exists |
| `plans` | ⚠️ **PARTIAL** | Exists but schema doesn't match spec |
| `plan_tasks` | ❌ **MISSING** | No table exists |
| `assignments` | ❌ **MISSING** | No table exists |
| `telemetry_events` | ❌ **MISSING** | No table exists |
| `printable_exports` | ❌ **MISSING** | No table exists |
| `clara_sessions` | ❌ **MISSING** | No table exists |
| `clara_turns` | ❌ **MISSING** | No table exists |

### Current Schema Issues
- **plans table**: Uses `plan_id UUID` instead of `id text PK` with `p_*` prefix
- **plans table**: Missing required fields: `date`, `mode`, `sections`, `version`, `prompt_version`, `policy_version`, `cached`
- **plans table**: Missing unique constraint on `(home_id, date, mode)`
- **Missing indexes**: All spec-required indexes are missing
- **Missing enums**: No database enums for `PlanMode`, `TaskState`, `MemberRole`, `TelemetryKind`

## Authentication & Headers Audit

| Feature | Status | Notes |
|---------|--------|-------|
| JWT Bearer Auth | ✅ **IMPLEMENTED** | Working with `Authorization: Bearer <JWT>` |
| X-Client-Version | ❌ **MISSING** | Header not extracted or used |
| Idempotency-Key | ❌ **MISSING** | No idempotency support |
| X-Prompt-Version | ❌ **MISSING** | Header not extracted or used |
| X-Policy-Version | ❌ **MISSING** | Header not extracted or used |

## Error Handling Audit

| Feature | Status | Notes |
|---------|--------|-------|
| Error Envelope | ⚠️ **PARTIAL** | Basic error structure exists but doesn't match spec |
| Error Codes | ⚠️ **PARTIAL** | Some codes exist but missing spec-required ones |
| Request ID | ⚠️ **PARTIAL** | Generated but not from trace context |
| Error Details | ❌ **MISSING** | No `details` field in error responses |

**Current Error Format:**
```json
{
  "code": "SESSION_NOT_FOUND",
  "message": "Session not found",
  "request_id": "uuid"
}
```

**Required Error Format:**
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

## Pagination Audit

| Feature | Status | Notes |
|---------|--------|-------|
| Cursor-based Pagination | ❌ **MISSING** | No pagination implementation |
| Limit Parameter | ❌ **MISSING** | No limit support |
| nextCursor Response | ❌ **MISSING** | No cursor response |

## Idempotency Audit

| Feature | Status | Notes |
|---------|--------|-------|
| Idempotency-Key Header | ❌ **MISSING** | No idempotency support |
| 24h Dedupe Window | ❌ **MISSING** | No idempotency storage |
| Request Hash Storage | ❌ **MISSING** | No idempotency tracking |

## Security & Privacy Audit

| Feature | Status | Notes |
|---------|--------|-------|
| No Raw Audio Storage | ✅ **COMPLIANT** | No audio persistence in current implementation |
| PII Redaction | ❌ **MISSING** | No comment redaction before persistence |
| Row Level Security | ❌ **MISSING** | No home_id scoping in queries |
| Telemetry Headers | ❌ **MISSING** | No X-Prompt-Version/X-Policy-Version tracking |

## Implementation Gaps Summary

### Critical Missing Components
1. **Core Plan Management**: No plan generation, revision, or retrieval
2. **Data Model**: 10/11 tables missing, 1 table doesn't match spec
3. **Task Management**: No task templates, assignments, or telemetry
4. **Lookup APIs**: No home, member, room, or template management
5. **Pagination**: No cursor-based pagination system
6. **Idempotency**: No idempotency support for create/mutate operations

### Partial Implementations
1. **Error Handling**: Basic structure exists but doesn't match spec format
2. **Authentication**: JWT works but missing optional headers
3. **Clara Voice**: 3/4 endpoints implemented, missing cancel functionality

### Working Components
1. **Clara Session Management**: Create session, start turn, WebSocket streaming
2. **Basic Authentication**: JWT validation working
3. **Health Checks**: Basic health and metrics endpoints

## Recommended Implementation Order

1. **Database Schema** - Create all missing tables and migrations
2. **Core Models** - Implement Rust structs matching spec
3. **Plan APIs** - Implement plan generation, revision, and retrieval
4. **Lookup APIs** - Implement home, member, room, and template management
5. **Error Handling** - Align error format with spec
6. **Pagination** - Implement cursor-based pagination
7. **Idempotency** - Add idempotency support
8. **Telemetry** - Implement task completion tracking
9. **Printable** - Add PDF generation support
10. **Testing** - Add comprehensive test suite

## Files to Create/Modify

### New Files Needed
- `crates/api/src/plans.rs` - Plan API handlers
- `crates/api/src/family.rs` - Family assignment handlers
- `crates/api/src/telemetry.rs` - Telemetry handlers
- `crates/api/src/lookup.rs` - Lookup API handlers
- `crates/api/src/errors.rs` - Centralized error handling
- `crates/api/src/idempotency.rs` - Idempotency middleware
- `crates/api/src/pagination.rs` - Pagination utilities
- `crates/domain/src/models.rs` - Domain models
- `crates/domain/src/services/` - Business logic services
- `tests/api/` - Integration tests
- `tests/migrations.rs` - Migration tests
- `tools/postman/CleanFlow_v0_9.json` - Postman collection

### Files to Modify
- `crates/api/src/routes.rs` - Add new route definitions
- `crates/api/src/handlers.rs` - Add missing handlers, fix error format
- `crates/store/migrations/` - Add new migration files
- `crates/store/src/traits.rs` - Add new repository traits
- `crates/store/src/postgres.rs` - Implement new repositories

## Testing Strategy

1. **Unit Tests**: Test individual handlers and services
2. **Integration Tests**: Test full API endpoints with database
3. **Migration Tests**: Verify migrations apply and rollback correctly
4. **Property Tests**: Test pagination cursor stability and idempotency
5. **Contract Tests**: Verify API responses match spec exactly
6. **Snapshot Tests**: Ensure stable JSON response formatting

## Next Steps

1. Create database migrations for all missing tables
2. Implement core domain models and services
3. Add missing API endpoints one by one
4. Implement proper error handling and pagination
5. Add comprehensive test coverage
6. Generate OpenAPI spec and validate against documentation

---

**Audit completed by**: Senior Rust Engineer  
**Date**: 2025-01-29  
**Spec Version**: CleanFlow API Reference & Data Model v0.9
