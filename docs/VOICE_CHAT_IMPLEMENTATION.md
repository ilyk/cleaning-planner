# Voice Chat with OpenAI Realtime API - Implementation Plan

## Overview
Implement real-time voice conversation using OpenAI's Realtime API (WebSocket-based) for low-latency voice-to-voice chat.

## Architecture

### 1. OpenAI Realtime API Integration

**WebSocket Connection:**
```kotlin
class OpenAIRealtimeService @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val openAIConfigDataStore: OpenAIConfigDataStore
) {
    private var webSocket: WebSocket? = null
    private var audioRecorder: AudioRecorder? = null
    private var audioPlayer: AudioPlayer? = null
    
    // Connect to wss://api.openai.com/v1/realtime?model=gpt-4o-realtime-preview-2024-10-01
    suspend fun startRealtimeSession(language: String)
    
    // Send audio chunks as they're recorded
    fun sendAudioChunk(audioData: ByteArray)
    
    // Receive and play audio chunks as they arrive
    private fun onAudioReceived(audioData: ByteArray)
    
    // Handle conversation events
    private fun onConversationUpdate(event: RealtimeEvent)
}
```

**Key Features:**
- **Bidirectional streaming**: Send mic audio, receive Clara's audio
- **Low latency**: <500ms response time
- **Natural interruptions**: Can interrupt Clara mid-sentence
- **Function calling**: Clara can call functions (e.g., save room info)

### 2. Audio Recording (Microphone Input)

```kotlin
class AudioRecorder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var audioRecord: AudioRecord? = null
    private val recordingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    fun startRecording(onAudioChunk: (ByteArray) -> Unit) {
        // Record PCM16 audio at 24kHz (required by Realtime API)
        // Send chunks every 100ms
    }
    
    fun stopRecording()
}
```

### 3. Audio Playback (Speaker Output)

```kotlin
class AudioStreamPlayer @Inject constructor() {
    private var audioTrack: AudioTrack? = null
    
    fun playAudioChunk(audioData: ByteArray) {
        // Play PCM16 audio at 24kHz
    }
    
    fun stop()
}
```

### 4. Voice Chat Screen

```kotlin
@Composable
fun VoiceChatScreen(
    viewModel: VoiceChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Column {
        // Avatar with talking animation
        Avatar3DView(...)
        
        // Live transcript
        Text(uiState.claraTranscript)
        Text(uiState.userTranscript)
        
        // Push-to-talk or continuous listening
        Button(
            onClick = { viewModel.toggleListening() }
        ) {
            Icon(
                if (uiState.isListening) Icons.Default.Mic else Icons.Default.MicOff
            )
        }
    }
}
```

## Implementation Steps

### Phase 1: Basic Realtime Connection (2-3 days)
1. Add WebSocket dependencies to `data:network`
2. Create `OpenAIRealtimeService` with WebSocket connection
3. Implement session initialization and event handling
4. Add audio format conversion (PCM16, 24kHz)

### Phase 2: Audio Recording & Playback (2 days)
1. Request microphone permissions
2. Implement `AudioRecorder` with continuous recording
3. Implement `AudioStreamPlayer` for streaming playback
4. Test audio pipeline (record → send → receive → play)

### Phase 3: Voice Chat UI (1-2 days)
1. Create `VoiceChatScreen` with push-to-talk button
2. Show real-time transcripts (Clara's and user's)
3. Visual feedback (waveform, mic indicator)
4. Integrate with 3D avatar animations

### Phase 4: Advanced Features (2-3 days)
1. Add Voice Activity Detection (VAD) for automatic turn-taking
2. Implement function calling (save household data)
3. Add conversation history
4. Handle interruptions gracefully

## API Details

**WebSocket URL:**
```
wss://api.openai.com/v1/realtime?model=gpt-4o-realtime-preview-2024-10-01
```

**Headers:**
```
Authorization: Bearer YOUR_API_KEY
OpenAI-Beta: realtime=v1
```

**Session Configuration:**
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

**Send Audio:**
```json
{
  "type": "input_audio_buffer.append",
  "audio": "base64_encoded_pcm16_data"
}
```

**Receive Audio:**
```json
{
  "type": "response.audio.delta",
  "delta": "base64_encoded_pcm16_data"
}
```

## Estimated Cost

**Realtime API Pricing:**
- Audio input: $0.06 / minute
- Audio output: $0.24 / minute
- Text input: $5.00 / 1M tokens
- Text output: $20.00 / 1M tokens

**5-minute conversation:**
- Input: $0.30
- Output: $1.20
- **Total: ~$1.50**

(More expensive than text+TTS but much better UX)

## Fallback Strategy

If Realtime API fails:
1. Fall back to Speech-to-Text (Whisper) + GPT-5 + TTS
2. Show error banner but continue conversation
3. Log failures for debugging

## Testing Plan

1. **Unit tests**: WebSocket connection, audio encoding
2. **Integration tests**: End-to-end conversation flow
3. **Manual testing**: Real conversations in different languages
4. **Performance**: Measure latency (target <500ms)

## References

- [OpenAI Realtime API Docs](https://platform.openai.com/docs/guides/realtime)
- [WebSocket RFC 6455](https://tools.ietf.org/html/rfc6455)
- [Android AudioRecord](https://developer.android.com/reference/android/media/AudioRecord)
- [Android AudioTrack](https://developer.android.com/reference/android/media/AudioTrack)

## Decision: Implement Now or Later?

**Pros of implementing now:**
- Much better UX (natural conversation)
- Lower latency
- More engaging for users

**Cons:**
- Significant implementation time (5-8 days)
- Higher API costs
- More complex error handling

**Recommendation:**
Start with current TTS approach, add Realtime API in next iteration after core features are stable.

