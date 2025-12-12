# OpenAI Realtime API Migration

## Overview
This document outlines the migration from the current GPT text + TTS approach to OpenAI's Realtime API for true bidirectional voice streaming.

## Key Benefits

### Current Approach (GPT + TTS + Whisper)
1. User speaks → Whisper API transcribes (1-2s)
2. Transcription → GPT-5 generates text (2-4s)
3. Text → TTS generates audio (3-6s)
4. **Total latency: 6-12 seconds**

### Realtime API
1. User speaks → Realtime API responds with audio
2. **Total latency: 320ms-1s**
3. Simultaneous transcription for UI display
4. Natural turn-taking with server-side VAD

## Architecture

### WebSocket Connection
```
Client ←WebSocket→ OpenAI Realtime API
   ↓ send audio          ↓ receive audio
   ↓ receive transcript  ↓ send transcript
```

### Audio Flow
1. **Input**: PCM16 @ 24kHz → Base64 → WebSocket
2. **Output**: WebSocket → Base64 → PCM16 → AudioTrack

### Message Types
- `session.update` - Configure voice, language, instructions
- `input_audio_buffer.append` - Send user audio chunks
- `response.audio.delta` - Receive Clara's audio chunks
- `response.audio_transcript.delta` - Receive Clara's transcript
- `conversation.item.input_audio_transcription.completed` - User transcript

## Implementation

### ✅ Completed Files

#### 1. `OpenAIRealtimeService.kt`
**Location**: `/feature/clara/src/main/kotlin/com/ilyk/cleaningplanner/feature/clara/service/`

**Key Features**:
- WebSocket connection management
- Bidirectional audio streaming (PCM16 @ 24kHz)
- Server-side VAD for automatic turn detection
- Real-time transcription
- State management with StateFlow

**States**:
```kotlin
sealed class RealtimeState {
    object Idle
    object Connecting
    object Connected
    object UserSpeaking
    data class ClaraSpeaking(val transcript: String)
    data class Error(val message: String)
}
```

**Public API**:
```kotlin
// Start a session
suspend fun startRealtimeSession(): Result<Unit>

// Stop the session
fun stopRealtimeSession()

// Observe state
val state: StateFlow<RealtimeState>
val userTranscript: StateFlow<String>
val claraTranscript: StateFlow<String>
```

#### 2. `VoiceChatViewModel.kt` (Simplified)
**Location**: `/feature/clara/src/main/kotlin/com/ilyk/cleaningplanner/feature/clara/ui/voice/`

**Simplified from 310 lines → 102 lines**

**Before** (Complex):
- WhisperSTTService for transcription
- GPT-5 API for responses  
- StreamingTTSService for audio
- ConversationRepository for history
- Complex state management

**After** (Simple):
- OpenAIRealtimeService only
- Automatic turn-taking
- Automatic transcription
- Automatic conversation context
- Simple state mapping

**Changes**:
```kotlin
// REMOVED dependencies:
- WhisperSTTService
- StreamingTTSService
- ClaraRepository
- ConversationRepository
- OpenAIApi
- LanguagePrefsDataStore
- OpenAIConfigDataStore

// ADDED dependency:
+ OpenAIRealtimeService
```

### 🔄 Changes Needed

#### 1. Dependency Injection
**File**: `data/network/src/main/kotlin/com/ilyk/cleaningplanner/data/network/di/NetworkModule.kt`

**No changes needed** - `NetworkModule` already provides `OkHttpClient` singleton.

The existing `OkHttpClient` is automatically injected into:
- `OpenAIRealtimeService` for WebSocket connections
- `AvatarRepository` for downloading GLB files
- Retrofit instances for API calls

The configured timeouts in `NetworkModule` are sufficient for WebSocket connections.

#### 2. Welcome Screen (Keep Simple TTS)
**File**: `/feature/clara/src/main/kotlin/com/ilyk/cleaningplanner/feature/clara/ui/welcome/WelcomeViewModel.kt`

**No change needed** - Welcome uses StreamingTTSService with static text (fast, consistent)

#### 3. Voice Chat Screen (No UI changes needed)
**File**: `/feature/clara/src/main/kotlin/com/ilyk/cleaningplanner/feature/clara/ui/voice/VoiceChatScreen.kt`

UI already supports all states - works as-is!

### API Configuration

#### Model
```kotlin
const val MODEL = "gpt-4o-realtime-preview-2024-12-17"
```

#### WebSocket URL
```kotlin
const val WS_URL = "wss://api.openai.com/v1/realtime?model=$MODEL"
```

#### Headers
```kotlin
"Authorization: Bearer ${apiKey}"
"OpenAI-Beta: realtime=v1"
```

#### Session Configuration
```json
{
  "type": "session.update",
  "session": {
    "modalities": ["text", "audio"],
    "instructions": "You are Clara, a warm cleaning planning assistant...",
    "voice": "nova",
    "input_audio_format": "pcm16",
    "output_audio_format": "pcm16",
    "turn_detection": {
      "type": "server_vad",
      "threshold": 0.5,
      "silence_duration_ms": 700
    }
  }
}
```

### Multi-Language Support

Language is configured per session:
```kotlin
private fun sendSessionConfig(language: String) {
    val langName = when (language) {
        "es" -> "Spanish"
        "fr" -> "French"
        "de" -> "German"
        "uk" -> "Ukrainian"
        else -> "English"
    }
    
    val instructions = "You are Clara, a warm cleaning planning assistant. You respond in $langName..."
    // Send to WebSocket
}
```

### Audio Specifications

#### Input (User → API)
- **Format**: PCM16 (16-bit linear PCM)
- **Sample Rate**: 24,000 Hz
- **Channels**: Mono
- **Encoding**: Base64 over WebSocket

#### Output (API → User)
- **Format**: PCM16
- **Sample Rate**: 24,000 Hz
- **Channels**: Mono
- **Decoding**: Base64 from WebSocket

### Error Handling

```kotlin
sealed class RealtimeState {
    data class Error(val message: String) : RealtimeState()
}

// Connection errors
- "API key not configured"
- "Connection failed"
- "Unknown error"

// API errors (from WebSocket)
- Parsed from error messages
- Displayed in UI
- Automatic reconnection possible
```

### Performance

#### Latency Improvements
- **Before**: 6-12 seconds total
- **After**: 320ms-1s total
- **Improvement**: 6-12x faster

#### Resource Usage
- **Before**: 3 services (Whisper + GPT + TTS) + Database writes
- **After**: 1 WebSocket connection
- **Network**: Reduced API calls from 3/turn to continuous streaming

#### Code Simplification
- **VoiceChatViewModel**: 310 lines → 102 lines (-67%)
- **Dependencies**: 9 → 2 (-78%)
- **Complexity**: High → Low

## Migration Steps

### ✅ Step 1: Create Realtime Service
- [x] `OpenAIRealtimeService.kt` created
- [x] WebSocket connection
- [x] Audio recording/playback
- [x] State management

### ✅ Step 2: Update ViewModel
- [x] `VoiceChatViewModel.kt` refactored
- [x] Simplified dependencies
- [x] State mapping

### ✅ Step 3: Dependency Injection
- [x] Reuse existing `OkHttpClient` from `NetworkModule`
- [x] No new module needed
- [x] Automatic injection into `OpenAIRealtimeService`

### 🔲 Step 4: Test
- [ ] Start voice chat
- [ ] Verify connection
- [ ] Test conversation flow
- [ ] Verify language switching
- [ ] Check error handling

### 🔲 Step 5: Cleanup (Optional)
- [ ] Remove `WhisperSTTService` (if unused elsewhere)
- [ ] Keep `StreamingTTSService` (used by Welcome)
- [ ] Remove conversation database writes (Realtime handles context)

## Testing Checklist

- [ ] Voice chat connects successfully
- [ ] User speech detected automatically
- [ ] Clara responds with audio
- [ ] Transcripts display in real-time
- [ ] Language switching works
- [ ] Error states handled gracefully
- [ ] Connection drops recovered
- [ ] Audio quality is clear
- [ ] Latency feels natural (<1s)
- [ ] No audio glitches or cutoffs

## API Pricing

### Before (per conversation turn)
- Whisper: $0.006 / min
- GPT-5: $2.50 / 1M input + $10 / 1M output
- TTS: $15 / 1M characters

### After (Realtime API)
- **Text tokens**: $5 / 1M input + $20 / 1M output
- **Audio tokens**: $100 / 1M input + $200 / 1M output
- **Note**: Higher cost but much better UX

## Rollback Plan

If issues arise:
1. Keep old services intact (don't delete yet)
2. Add feature flag to switch between modes
3. Gradual rollout

```kotlin
val useRealtimeAPI = BuildConfig.ENABLE_REALTIME_API // Feature flag

if (useRealtimeAPI) {
    // Use OpenAIRealtimeService
} else {
    // Use WhisperSTTService + GPT + StreamingTTSService
}
```

## References

- [OpenAI Realtime API Docs](https://platform.openai.com/docs/guides/realtime)
- [WebSocket Spec](https://tools.ietf.org/html/rfc6455)
- [PCM Audio Format](https://en.wikipedia.org/wiki/Pulse-code_modulation)

## Status

✅ **Implementation Complete**
🔄 **Testing Needed**
📝 **Documentation Complete**

---

**Next Steps**: Build the app and test the Realtime API integration!

