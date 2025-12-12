# CleanFlow Mobile Guide

**Version:** 0.9
**Scope:** Android (Kotlin) client architecture, SDK usage, UI/UX states, localization & accessibility.

---

## 1. Mobile App Architecture

### Purpose
The CleanFlow mobile app is designed as a **thin, secure client** that focuses on responsiveness and offline usability. All intelligence, plan generation, and voice processing occur on the backend. The app itself remains an untrusted environment.

### Layered Architecture
- **Presentation (UI)** – Jetpack Compose screens: Home, Task Detail, Family, Kids, and Print views. Mode-first design (⚡ Focus, 🧼 Full Reset, 🌙 Low Energy, 🐾 Pet Mode).
- **Domain Layer** – Kotlin models mirroring backend DTOs (Plan, Task, Assignment, Telemetry, Clara session).
- **Data Layer** –
  - Remote: REST APIs for Plans/Family/Telemetry, WS for Clara streaming.
  - Local: Room/ProtoDataStore for caching plans, preferences, and feature flags.
  - Sync: Optimistic UI updates; backend remains the final source of truth.
- **Platform Layer** – Audio capture/playback, VAD (UX only), permissions, connectivity, and telemetry.

### Responsibilities
- **Online Operations**: Use REST endpoints `/v1/plan/*`, `/v1/family/assign`, `/v1/telemetry/complete`, and Clara voice endpoints `/v1/clara/*`.
- **Offline Fallbacks**: Cached “Today” plan, minimal local quick plan, and background sync.
- **Safety Model**: The backend performs all true validation and guardrails. The client only provides UX cues.

### State Management
- `PlanStore` – Cached plans with timestamps.
- `ClaraStore` – Session and stream state.
- `PrefsStore` – Mode, locale, and accessibility preferences.

### Error Strategy
Unified error envelopes mapped to UI messages, showing cached data when possible and clear retry actions.

---

## 2. Client SDK Guide (Kotlin)

### Setup
- **Authentication**: Bearer JWT.
- **Headers**: Add `X-Client-Version`, `X-Prompt-Version`, `X-Policy-Version`, and `Idempotency-Key` for write ops.
- **Networking**: Retrofit + OkHttp (REST) and OkHttp WebSocket (Clara voice stream).

### Core Interfaces
```kotlin
interface PlansApi {
  @POST("/v1/plan/generate") suspend fun generate(@Body req: GeneratePlanRequest): Plan
  @POST("/v1/plan/revise") suspend fun revise(@Body req: RevisePlanRequest): Plan
  @GET("/v1/plan/{planId}") suspend fun get(@Path("planId") id: String): Plan
  @POST("/v1/plan/printable") suspend fun printable(@Body req: PrintableRequest): PrintableResult
  @POST("/v1/family/assign") suspend fun assign(@Body req: FamilyAssignRequest): AssignResult
  @POST("/v1/telemetry/complete") suspend fun complete(@Body req: TelemetryEvent): TelemetryResult
}

interface ClaraApi {
  @POST("/v1/clara/session") suspend fun createSession(@Body req: CreateSessionRequest): Session
  @POST("/v1/clara/session/turn") suspend fun startTurn(@Body req: StartTurnRequest): Turn
  @POST("/v1/clara/cancel") suspend fun cancel(@Body req: CancelTurnRequest)
}
```

### Common Headers
```kotlin
class CommonHeadersInterceptor(
  private val tokenProvider: () -> String,
  private val versions: Versions
) : Interceptor {
  override fun intercept(chain: Interceptor.Chain): Response {
    val idempotency = if (chain.request().method in listOf("POST", "PATCH")) UUID.randomUUID().toString() else null
    val builder = chain.request().newBuilder()
      .addHeader("Authorization", "Bearer ${tokenProvider()}")
      .addHeader("X-Client-Version", versions.client)
      .addHeader("X-Prompt-Version", versions.prompt)
      .addHeader("X-Policy-Version", versions.policy)
    idempotency?.let { builder.addHeader("Idempotency-Key", it) }
    return chain.proceed(builder.build())
  }
}
```

### Clara Streaming (WebSocket)
- Stream 20–40 ms Opus frames as `input.audio.delta` messages.
- Commit with `input.audio.commit`.
- Handle `output.audio.delta` events for playback.
- Support `input.interrupt` for barge-in and fast response.

### Caching
- Room/ProtoDataStore, key = `(homeId, date, mode, planVersion)`.
- Automatic revalidation on app resume or mode change.

### Testing
- Mock APIs for offline/dev modes.
- Contract tests for headers and error envelopes.
- Load tests for WS concurrency.

---

## 3. UI/UX Flows & States

### Core Principles
- Mode-first personalization.
- Real-time responsiveness for Clara streaming.
- Offline-friendly design.

### Key Screens
**Home (Today)**
- States: `loading → cached+updating → ready → error-with-cache`.
- Features: change mode, mark done/skip, expand task, open timer, share/print.
- Offline fallback: minimal quick plan.

**Task Detail**
- Checklist, estimated duration, tools, and QR history.
- Feedback → `/v1/telemetry/complete`.

**Family View**
- Show per-member task slices.
- Drag-and-drop reassignment → `/v1/family/assign`.

**Clara Voice Assistant**
- States: `idle → listening → thinking → speaking → ready`.
- Events: `start`, `delta`, `tool_call`, `tool_result`, `finish`, `error`.
- UX: Live captions, chips (suggestions), earcons for transitions.

**Printables (Paper Bridge)**
- `/v1/plan/printable` returns PDF with QR links back to tasks.

### Error & Recovery
- Consistent error handling from backend envelope.
- Stream fallback message for dropped connections.

---

## 4. Localization & Accessibility

### Localization (i18n)
- All strings use IDs and ICU message syntax.
- Runtime locale switching (per-user preference).
- Automatic adaptation of date/time formats and units.

### Accessibility (a11y)
- Large touch targets, high contrast, and screen reader compatibility.
- Semantic roles for Compose elements.
- Live region updates for streaming (e.g., “Clara is listening/speaking”).
- Reduced motion and color-blind safe palettes.
- Optional on-device captions for voice turns with PII redaction.

### Testing & QA
- Automated screenshots per locale and mode.
- TalkBack validation for all interactive screens.
- Pseudolocalization build (`[XX]` expanded strings) to catch overflow.

---

**References:**
- Implementation Summary v0.9
- Spec Audit v0.9 Final

This guide aligns mobile responsibilities and SDK design with CleanFlow backend architecture and API contracts.

