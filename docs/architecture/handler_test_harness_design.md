# Backend Handler Test Harness — Design Doc (W4.7)

> Owner: TBD. Target landing: a dedicated PR after this doc is reviewed.
> Status: design proposal. Replaces the deferral note in PLAN.md §0 ("Backend handler test harness + integration tests").

## Problem

`backend/crates/api/src/state.rs::AppState` has **14** `Arc<dyn Service>` fields plus six non-service deps (`config`, `jwt_validator`, `session_manager`, `store`, `db_pool`, `metrics`). Of the 14 services, only **3** expose `Mock*` impls today:

* `MockPlanService` (`crates/domain/src/services/plan_service.rs:36`)
* `MockPrintableService` (`crates/domain/src/services/printable_service.rs:14`)
* `MockIdempotencyService` (`crates/domain/src/services/idempotency_service.rs:19`)

The other 11 are `Db*` or `Llm*` impls bound to a live `sqlx::PgPool` (`crates/store/src/lib.rs:19` — `pub type DbPool = sqlx::PgPool;`). `AppConfig` is a nested config tree (`crates/config/src/lib.rs`: ServerConfig + DatabaseConfig + RedisConfig + AuthConfig + LlmConfig + RateLimitConfig + TimeoutConfig + VersionConfig + FeatureFlags + GuardrailsConfig).

Net effect: no handler integration test can be written today without spinning a real Postgres and assembling the full config tree. PR `494d235` shipped `rooms.rs`, `households.rs`, and `skip_task` in `plans.rs` with no handler tests for exactly this reason.

## Recommendations (one per question)

### 1. Mock strategy — hand-rolled

**Recommend:** hand-roll the 11 missing `Mock*` structs in the same file as the trait they implement, alongside the existing pattern.

Rationale: the traits are small (3–7 methods each, all `async fn`). Hand-rolled mocks compile fast, are obvious in IDE jump-to-definition, and let us pin specific return values per-test without macro magic. `mockall`'s `automock` adds a proc-macro to every trait and bloats compile time — for 14 traits across 4 crates the cost outweighs the ergonomic win. Hybrid is worst-of-both.

Concrete shape (sketch, per service):

```rust
// crates/domain/src/services/telemetry_service.rs
pub struct MockTelemetryService {
    pub last_request: Mutex<Option<TelemetryCompleteRequest>>,
    pub response: Mutex<Result<TelemetryCompleteResponse>>,
}
impl MockTelemetryService {
    pub fn new_ok() -> Self { /* canned success */ }
    pub fn new_err(e: anyhow::Error) -> Self { /* canned failure */ }
}
#[async_trait]
impl TelemetryService for MockTelemetryService {
    async fn record_telemetry(&self, r: TelemetryCompleteRequest) -> Result<TelemetryCompleteResponse> {
        *self.last_request.lock().unwrap() = Some(r);
        match &*self.response.lock().unwrap() {
            Ok(resp) => Ok(resp.clone()),
            Err(e) => Err(anyhow::anyhow!("{}", e)),
        }
    }
}
```

Multiply by 11 services. Total ~600–900 lines, maintenance shared by anyone touching the trait.

### 2. `AppState::for_testing(...)` constructor

**Recommend:** a builder-style constructor that takes only the services the test needs and noop-mocks the rest. Place behind `#[cfg(any(test, feature = "test-support"))]` so it never ships in release binaries.

Signature sketch (`crates/api/src/state.rs`):

```rust
#[cfg(any(test, feature = "test-support"))]
impl AppState {
    pub fn for_testing() -> AppStateTestBuilder { AppStateTestBuilder::default() }
}

#[cfg(any(test, feature = "test-support"))]
#[derive(Default)]
pub struct AppStateTestBuilder {
    plan_service: Option<Arc<dyn PlanService>>,
    telemetry_service: Option<Arc<dyn TelemetryService>>,
    room_service: Option<Arc<dyn RoomService>>,
    home_service: Option<Arc<dyn HomeService>>,
    // ... one Option per service
}

#[cfg(any(test, feature = "test-support"))]
impl AppStateTestBuilder {
    pub fn with_plan_service(mut self, s: Arc<dyn PlanService>) -> Self { self.plan_service = Some(s); self }
    // ... one with_* per service
    pub fn build(self) -> AppState {
        AppState {
            config: Arc::new(AppConfig::test_default()),
            jwt_validator: Arc::new(JwtValidator::for_testing()),
            session_manager: Arc::new(SessionManager::in_memory()),
            store: Store::in_memory(),
            db_pool: panic_pool(), // see §4
            metrics: Arc::new(Metrics::noop()),
            plan_service: self.plan_service.unwrap_or_else(|| Arc::new(MockPlanService)),
            telemetry_service: self.telemetry_service.unwrap_or_else(|| Arc::new(MockTelemetryService::new_ok())),
            // ... noop-mock fallback per field
        }
    }
}
```

Tests then read like: `let state = AppState::for_testing().with_telemetry_service(my_mock).build();`.

### 3. `AppConfig::test_default()`

**Recommend:** add a `test_default()` constructor next to the existing `AppConfig::load(...)` in `crates/config/src/lib.rs`, gated on `#[cfg(any(test, feature = "test-support"))]`. Fills every required field with safe placeholders; no disk reads, no env vars.

```rust
#[cfg(any(test, feature = "test-support"))]
impl AppConfig {
    pub fn test_default() -> Self {
        Self {
            server: ServerConfig { host: "127.0.0.1".into(), port: 0, ... },
            database: DatabaseConfig { url: "postgres://test".into(), max_connections: 1 },
            redis: RedisConfig { url: "redis://test".into() },
            auth: AuthConfig { jwt_secret: "test-secret-32-bytes-minimum-len".into(), ... },
            llm: LlmConfig { anthropic_api_key: "test".into(), openai_api_key: "test".into(), openai_model: "test".into() },
            rate_limit: RateLimitConfig::default(),
            timeouts: TimeoutConfig::default(),
            versions: VersionConfig { prompt: "test".into(), policy: "test".into() },
            features: FeatureFlags::default(),
            guardrails: GuardrailsConfig::default(),
        }
    }
}
```

Where `default()` doesn't exist on a sub-struct, add a `#[derive(Default)]` or a minimal `impl Default`. One-time tax.

### 4. `DbPool` strategy — `panic_pool` for unit tests, `testcontainers-rs` for integration

Three options were on the table:

* (a) Wrap `Option<DbPool>` and force-check at call sites. **Rejected** — pollutes 11 service impls with `unwrap_or_panic`.
* (b) Spin a real ephemeral Postgres per-test via `testcontainers-rs`. **Recommended for integration tests** that exercise the full `Db*` stack.
* (c) Abstract `DbPool` behind a trait and provide `MockDbPool`. **Rejected** — `DbPool` is already a sqlx alias, and the `Db*` services use `sqlx` query macros that can't be retargeted at a mock without giving up the offline-cache.

**For the W4.7 handler tests specifically:** the priority endpoints (`/v1/plan/revise`, `/v1/telemetry/complete`, `/v1/rooms` POST, `/v1/households` POST, `/v1/tasks/{id}/skip`) all go through service traits in `AppState`. As long as the test plugs in a `Mock*` service for the relevant trait, the handler never touches `db_pool` directly. So:

```rust
// crates/api/src/state.rs
#[cfg(any(test, feature = "test-support"))]
fn panic_pool() -> DbPool {
    // Tests that touch handlers using only mocked services never reach db_pool;
    // panic loudly if a test accidentally hits a real query.
    use std::sync::OnceLock;
    static POOL: OnceLock<DbPool> = OnceLock::new();
    POOL.get_or_init(|| {
        sqlx::PgPool::connect_lazy("postgres://invalid-do-not-connect/0")
            .expect("lazy pool construction must not connect")
    }).clone()
}
```

This way **unit-style handler tests** (the 5 priority endpoints) need no Postgres. **Integration tests** that exercise the `Db*` impls themselves get a real container via a separate `tests/integration/` dir gated on a `testcontainers` feature.

### 5. Sample test — `POST /v1/plan/revise`

```rust
// crates/api/tests/handlers/revise_plan.rs
use axum::{body::Body, http::Request};
use tower::ServiceExt;
use cleanflow_api::{routes, state::AppState};
use cleanflow_domain::services::{plan_service::MockPlanService, ...};

#[tokio::test]
async fn revise_plan_returns_200_and_increments_version() {
    let plan_mock = Arc::new(MockPlanService); // canned MockPlanService::revise_plan returns version+1
    let state = AppState::for_testing().with_plan_service(plan_mock.clone()).build();
    let app = routes::create_router(state);

    let body = serde_json::to_vec(&serde_json::json!({
        "plan_id": "plan-abc",
        "edits": [{"task_id": "t1", "skip": true}]
    })).unwrap();

    let req = Request::builder()
        .method("POST")
        .uri("/v1/plan/revise")
        .header("authorization", "Bearer test-jwt-with-home-1")
        .header("content-type", "application/json")
        .body(Body::from(body)).unwrap();

    let resp = app.oneshot(req).await.unwrap();
    assert_eq!(resp.status(), 200);
    let bytes = axum::body::to_bytes(resp.into_body(), 64*1024).await.unwrap();
    let parsed: serde_json::Value = serde_json::from_slice(&bytes).unwrap();
    assert_eq!(parsed["version"], 2);
}
```

Notes:
* `Bearer test-jwt-with-home-1` is a JWT signed by `JwtValidator::for_testing()` carrying `claims.home_id = "home-1"` so the middleware extracts an `AuthExtension` that the handler accepts.
* `routes::create_router(state)` is the existing entrypoint at `crates/api/src/routes.rs`; the full middleware chain (auth + idempotency + cors + trace) runs as in prod.

The 4 other priority handler tests follow the same pattern, swapping the mocked service and request shape.

### 6. Risks and timeline

* **Auth `Extension` injection**: the auth middleware writes `AuthExtension` to the request extensions. If `JwtValidator::for_testing()` accepts any token, tests can pass `Bearer literally-anything`. If it requires signing, the test helper must mint a valid JWT.
* **Middleware chain order**: `routes::create_router` applies idempotency middleware on the plan routes. Tests must include the `Idempotency-Key` header on POST/PATCH/PUT/DELETE or the middleware will short-circuit. Already shipped on the Android side via `CommonHeadersInterceptor` — backend test helper should mirror.
* **sqlx feature flags**: `sqlx-test-utils` and `sqlx::PgPool::connect_lazy` interact with the `runtime-tokio` feature. Confirm `sqlx` is built with `runtime-tokio` in the test profile (it already is for dev/prod per `Cargo.lock`).
* **Serde body shapes**: `RevisePlanRequest` uses snake_case via serde; the test JSON must match. Capture the canonical shape from `crates/domain/src/models.rs`.

**Timeline estimate:**

| Step | Hours |
|---|---|
| Hand-roll 11 `Mock*` structs (50–80 lines each) | 4–6 |
| `AppConfig::test_default()` + sub-struct `Default` impls where missing | 1 |
| `AppState::for_testing()` builder | 1 |
| `JwtValidator::for_testing()` + JWT helper | 1 |
| `panic_pool()` + sqlx wiring | 0.5 |
| 5 priority handler tests (`revise_plan`, `complete`, rooms-create, households-create, task-skip) | 3 |
| CI integration + workspace test runtime check | 0.5 |
| **Total** | **11–13 hours** |

This is a one-shot land. Once the harness is in, each new handler test costs ~30 minutes.

## Out of scope for the harness PR

* Integration tests against a real Postgres (separate W, gated on a `testcontainers` feature).
* Replacing the `Db*` services with mocks (the harness sidesteps them by mocking at the trait layer above).
* Auth-failure paths (403/401) — first PR proves the happy path + 404; auth-fail tests follow in W4.7b.
