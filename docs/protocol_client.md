# CleanFlow Client Protocol Reference

**Version:** 0.9  
**Last Updated:** 2024

## Overview

This document describes the client-side protocol implementation for CleanFlow, including WebSocket events, API contracts, and data models used for communication with the backend.

## WebSocket Protocol

### Connection

**Endpoint:** `wss://api.cleanflow.com/v1/clara/stream?sessionId={sessionId}`

**Authentication:** Bearer token in Authorization header

**Connection Management:**
- Auto-reconnect on connection loss
- Heartbeat every 30 seconds
- Graceful shutdown on app background

### Message Format

All WebSocket messages use JSON format with the following structure:

```json
{
  "type": "message_type",
  "data": "base64_encoded_data",
  "timestamp": 1640995200000,
  "turnId": "optional_turn_id"
}
```

### Client → Server Messages

#### Audio Delta
```json
{
  "type": "input.audio.delta",
  "data": "base64_encoded_opus_frame",
  "timestamp": 1640995200000
}
```
- **Frequency:** 20-40ms intervals
- **Format:** Opus encoded audio
- **Sample Rate:** 16kHz
- **Channels:** Mono

#### Audio Commit
```json
{
  "type": "input.audio.commit",
  "turnId": "turn_123",
  "timestamp": 1640995200000
}
```
- **Purpose:** Signal end of audio input
- **Trigger:** User releases push-to-talk

#### Interrupt
```json
{
  "type": "input.interrupt",
  "turnId": "turn_123",
  "timestamp": 1640995200000
}
```
- **Purpose:** Cancel current turn
- **Trigger:** User taps interrupt button

### Server → Client Messages

#### Audio Output Delta
```json
{
  "type": "output.audio.delta",
  "data": "base64_encoded_opus_frame",
  "timestamp": 1640995200000,
  "turnId": "turn_123"
}
```
- **Format:** Opus encoded audio
- **Playback:** Buffered with jitter compensation

#### Turn Started
```json
{
  "type": "turn.started",
  "turn": {
    "id": "turn_123",
    "sessionId": "session_456",
    "status": "listening",
    "createdAt": "2024-01-15T10:00:00Z"
  }
}
```

#### Turn Completed
```json
{
  "type": "turn.completed",
  "turn": {
    "id": "turn_123",
    "sessionId": "session_456",
    "status": "completed",
    "transcript": "User said hello",
    "audioUrl": "https://api.cleanflow.com/audio/turn_123.mp3",
    "toolCalls": [
      {
        "id": "tool_789",
        "name": "generate_plan",
        "parameters": {"mode": "focus"},
        "result": "Plan generated successfully",
        "status": "completed"
      }
    ]
  }
}
```

#### Caption
```json
{
  "type": "caption",
  "text": "Hello, how can I help you today?",
  "isPartial": false
}
```

#### Error
```json
{
  "type": "error",
  "message": "Audio processing failed",
  "code": "AUDIO_ERROR"
}
```

## REST API Protocol

### Base URL
`https://api.cleanflow.com`

### Authentication
All requests require Bearer token authentication:
```
Authorization: Bearer <jwt_token>
```

### Required Headers
```
X-Client-Version: 0.9.0
X-Prompt-Version: 1.0.0
X-Policy-Version: 1.0.0
Idempotency-Key: <uuid> (for mutating operations)
```

### Plans API

#### Generate Plan
```http
POST /v1/plan/generate
Content-Type: application/json

{
  "homeId": "home_123",
  "date": "2024-01-15",
  "mode": "FOCUS",
  "preferences": {
    "maxDurationMinutes": 120,
    "preferredTools": ["broom", "mop"],
    "avoidTasks": ["deep_cleaning"]
  }
}
```

**Response:**
```json
{
  "id": "plan_123",
  "homeId": "home_123",
  "date": "2024-01-15",
  "mode": "FOCUS",
  "version": 1,
  "createdAt": "2024-01-15T10:00:00Z",
  "updatedAt": "2024-01-15T10:00:00Z",
  "tasks": [
    {
      "id": "task_456",
      "title": "Clean kitchen counters",
      "description": "Wipe down all counter surfaces",
      "priority": "NOW",
      "estimatedDurationMinutes": 15,
      "tools": ["spray", "cloth"],
      "tips": ["Start from the top"],
      "qrCode": "qr_789",
      "assignedTo": null,
      "completedAt": null,
      "skippedAt": null,
      "createdAt": "2024-01-15T10:00:00Z",
      "updatedAt": "2024-01-15T10:00:00Z"
    }
  ],
  "metadata": {
    "totalEstimatedMinutes": 120,
    "taskCount": 8,
    "completedCount": 0,
    "skippedCount": 0,
    "efficiencyScore": null
  }
}
```

#### Revise Plan
```http
POST /v1/plan/revise
Content-Type: application/json

{
  "planId": "plan_123",
  "changes": [
    {
      "type": "MODIFY_TASK",
      "taskId": "task_456",
      "newTitle": "Clean kitchen counters thoroughly"
    }
  ],
  "reason": "User requested more detail"
}
```

#### Get Plan
```http
GET /v1/plan/{planId}
```

#### Generate Printable
```http
POST /v1/plan/printable
Content-Type: application/json

{
  "planId": "plan_123",
  "format": "PDF",
  "includeQR": true,
  "includeInstructions": true
}
```

**Response:**
```json
{
  "url": "https://api.cleanflow.com/printables/plan_123.pdf",
  "expiresAt": "2024-01-16T10:00:00Z",
  "format": "PDF",
  "sizeBytes": 245760
}
```

### Family API

#### Assign Tasks
```http
POST /v1/family/assign
Content-Type: application/json

{
  "homeId": "home_123",
  "assignments": [
    {
      "taskId": "task_456",
      "assignedTo": "member_789",
      "reason": "Member prefers kitchen tasks"
    }
  ]
}
```

**Response:**
```json
{
  "success": true,
  "message": "Assignments updated successfully",
  "assignments": [
    {
      "taskId": "task_456",
      "assignedTo": "member_789",
      "reason": "Member prefers kitchen tasks"
    }
  ]
}
```

### Telemetry API

#### Complete Event
```http
POST /v1/telemetry/complete
Content-Type: application/json

{
  "id": "event_123",
  "homeId": "home_123",
  "userId": "user_456",
  "taskId": "task_789",
  "eventType": "TASK_COMPLETED",
  "timestamp": "2024-01-15T10:30:00Z",
  "metadata": {
    "duration_minutes": 12,
    "tools_used": "spray,cloth"
  }
}
```

**Response:**
```json
{
  "success": true,
  "message": "Event recorded successfully",
  "eventId": "event_123"
}
```

#### Batch Complete
```http
POST /v1/telemetry/batch
Content-Type: application/json

{
  "events": [
    {
      "id": "event_123",
      "homeId": "home_123",
      "userId": "user_456",
      "eventType": "TASK_COMPLETED",
      "timestamp": "2024-01-15T10:30:00Z"
    }
  ]
}
```

### Clara API

#### Create Session
```http
POST /v1/clara/session
Content-Type: application/json

{
  "homeId": "home_123",
  "userId": "user_456",
  "capabilities": ["voice", "planning", "family"]
}
```

**Response:**
```json
{
  "id": "session_123",
  "homeId": "home_123",
  "userId": "user_456",
  "createdAt": "2024-01-15T10:00:00Z",
  "expiresAt": "2024-01-15T11:00:00Z",
  "status": "ACTIVE"
}
```

#### Start Turn
```http
POST /v1/clara/session/turn
Content-Type: application/json

{
  "sessionId": "session_123",
  "audioFormat": {
    "codec": "opus",
    "sampleRate": 16000,
    "channels": 1,
    "bitrate": 64000
  },
  "sampleRate": 16000,
  "channels": 1
}
```

**Response:**
```json
{
  "id": "turn_456",
  "sessionId": "session_123",
  "status": "LISTENING",
  "createdAt": "2024-01-15T10:05:00Z",
  "completedAt": null,
  "transcript": null,
  "toolCalls": [],
  "audioUrl": null
}
```

#### Cancel Turn
```http
POST /v1/clara/cancel
Content-Type: application/json

{
  "sessionId": "session_123",
  "turnId": "turn_456",
  "reason": "User cancelled"
}
```

## Error Handling

### Error Envelope Format
```json
{
  "error": "VALIDATION_ERROR",
  "message": "Invalid request parameters",
  "code": "400",
  "details": {
    "field": "homeId",
    "reason": "required"
  },
  "timestamp": "2024-01-15T10:00:00Z"
}
```

### Common Error Codes
- `400` - Bad Request
- `401` - Unauthorized
- `403` - Forbidden
- `404` - Not Found
- `409` - Conflict
- `429` - Rate Limited
- `500` - Internal Server Error
- `503` - Service Unavailable

### Client Error Handling
1. Parse error envelope
2. Map to user-friendly message
3. Show retry option for retryable errors
4. Log error details for debugging
5. Update UI state accordingly

## Data Models

### Core Models

#### Plan
```kotlin
data class Plan(
    val id: String,
    val homeId: String,
    val date: String, // ISO date
    val mode: CleaningMode,
    val version: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val tasks: List<Task>,
    val metadata: PlanMetadata
)
```

#### Task
```kotlin
data class Task(
    val id: String,
    val title: String,
    val description: String?,
    val priority: TaskPriority,
    val estimatedDurationMinutes: Int,
    val tools: List<String>,
    val tips: List<String>,
    val qrCode: String?,
    val assignedTo: String?,
    val completedAt: Instant?,
    val skippedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant
)
```

#### ClaraSession
```kotlin
data class ClaraSession(
    val id: String,
    val homeId: String,
    val userId: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val status: SessionStatus
)
```

### Enums

#### CleaningMode
```kotlin
enum class CleaningMode {
    FOCUS,      // ⚡ Focus mode
    FULL_RESET, // 🧼 Full Reset
    LOW_ENERGY, // 🌙 Low Energy
    PET_MODE    // 🐾 Pet Mode
}
```

#### TaskPriority
```kotlin
enum class TaskPriority {
    NOW,    // Must do today
    NEXT,   // Should do today
    LATER   // Can be deferred
}
```

#### TelemetryEventType
```kotlin
enum class TelemetryEventType {
    TASK_COMPLETED,
    TASK_SKIPPED,
    TASK_STARTED,
    PLAN_GENERATED,
    PLAN_REVISED,
    MODE_CHANGED,
    CLARA_SESSION_STARTED,
    CLARA_SESSION_ENDED,
    PRINTABLE_GENERATED,
    FAMILY_ASSIGNMENT_CHANGED,
    APP_OPENED,
    APP_BACKGROUNDED
}
```

## Implementation Notes

### WebSocket Connection Management
- Implement exponential backoff for reconnection
- Handle network state changes
- Gracefully handle app lifecycle events
- Maintain connection state across screen rotations

### Audio Processing
- Use Opus codec for efficient compression
- Implement proper buffering for smooth playback
- Handle audio focus changes
- Support background audio when appropriate

### Error Recovery
- Implement retry mechanisms with exponential backoff
- Cache data for offline fallback
- Show appropriate error messages to users
- Log errors for debugging and monitoring

### Performance Considerations
- Batch API calls when possible
- Implement proper caching strategies
- Use background processing for heavy operations
- Optimize UI updates for smooth performance

This protocol reference provides the complete specification for implementing CleanFlow client communication. All implementations should follow these contracts exactly to ensure compatibility with the backend services.
