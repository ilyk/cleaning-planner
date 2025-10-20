package com.ilyk.cleaningplanner.feature.clara.service

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import com.ilyk.cleaningplanner.feature.clara.data.LanguagePrefsDataStore
import com.ilyk.cleaningplanner.feature.clara.data.OpenAIConfigDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

sealed class RealtimeState {
    data object Idle : RealtimeState()
    data object Connecting : RealtimeState()
    data object Connected : RealtimeState()
    data object UserSpeaking : RealtimeState()
    data class ClaraSpeaking(val transcript: String) : RealtimeState()
    data class SessionComplete(val conversationTranscript: String) : RealtimeState()
    data class Error(val message: String) : RealtimeState()
}

/**
 * OpenAI Realtime API service for bidirectional voice conversation
 * WebSocket-based real-time audio streaming
 */
@Singleton
class OpenAIRealtimeService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val openAIConfigDataStore: OpenAIConfigDataStore,
    private val languagePrefsDataStore: LanguagePrefsDataStore
) {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isRecording = false
    private var isPlaying = false
    private var sessionConfigured = false
    private var currentLanguage = "en"
    private var isCancelling = false
    private var activeResponseId: String? = null
    private var turnVersion = 0
    private var pendingLanguageSwitch: String? = null
    
    private val _state = MutableStateFlow<RealtimeState>(RealtimeState.Idle)
    val state: StateFlow<RealtimeState> = _state.asStateFlow()
    
    private val _userTranscript = MutableStateFlow("")
    val userTranscript: StateFlow<String> = _userTranscript.asStateFlow()
    
    private val _claraTranscript = MutableStateFlow("")
    val claraTranscript: StateFlow<String> = _claraTranscript.asStateFlow()
    
    private val _isSwitchingLanguage = MutableStateFlow(false)
    val isSwitchingLanguage: StateFlow<Boolean> = _isSwitchingLanguage.asStateFlow()
    
    // Track full conversation for completion
    private val conversationLog = mutableListOf<String>()
    
    // Debug metrics
    private var droppedDeltas = 0
    private var cancelStartTime = 0L
    
    companion object {
        private const val TAG = "OpenAIRealtimeService"
        private const val SAMPLE_RATE = 24000 // 24kHz for Realtime API
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val WS_URL = "wss://api.openai.com/v1/realtime"
        private const val MODEL = "gpt-4o-realtime-preview-2024-12-17"
        private const val CANCEL_TIMEOUT_MS = 500L
        
        // Completion phrases
        private val COMPLETION_PHRASES = listOf(
            "i'm done", "that's all", "let's finish", "stop", "that's enough",
            "finish", "done", "end", "that's it", "no more", "enough",
            "i'm finished", "we're done", "let's stop"
        )
    }
    
    /**
     * Start a realtime voice session
     */
    suspend fun startRealtimeSession(): Result<Unit> {
        return try {
            _state.value = RealtimeState.Connecting
            
            val config = openAIConfigDataStore.openAIConfig.first()
            val language = languagePrefsDataStore.languageCode.first()
            
            if (config.apiKey.isBlank()) {
                _state.value = RealtimeState.Error("API key not configured")
                return Result.failure(Exception("API key not configured"))
            }
            
            val request = Request.Builder()
                .url("$WS_URL?model=$MODEL")
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("OpenAI-Beta", "realtime=v1")
                .build()
            
            webSocket = okHttpClient.newWebSocket(request, RealtimeWebSocketListener())
            
            // Initialize audio recording
            initializeAudioRecording()
            
            // Initialize audio playback
            initializeAudioPlayback()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start realtime session", e)
            _state.value = RealtimeState.Error(e.message ?: "Connection failed")
            Result.failure(e)
        }
    }
    
    private fun initializeAudioRecording() {
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )
        
        // Use VOICE_COMMUNICATION for built-in echo cancellation
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, // Echo cancellation!
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize * 2
        )
    }
    
    private fun initializeAudioPlayback() {
        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AUDIO_FORMAT
        )
        
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AUDIO_FORMAT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }
    
    private fun sendSessionConfig(language: String) {
        val langName = when (language) {
            "es" -> "Spanish"
            "fr" -> "French"
            "de" -> "German"
            "uk" -> "Ukrainian"
            else -> "English"
        }
        
        val instructions = """⚠️⚠️⚠️ ABSOLUTE REQUIREMENTS - NEVER VIOLATE THESE ⚠️⚠️⚠️

1. YOUR NAME IS CLARA. Always remember: YOU ARE CLARA.
2. You speak ONLY in $langName. NEVER switch to English, Czech, or any other language.
3. You are NOT a cleaning service. You do NOT offer to clean anything.
4. You ONLY gather information. You do NOT perform tasks.

═══════════════════════════════════════════════════════

YOU ARE CLARA - the AI assistant for "Cleaning Planner" app.

YOUR NAME: Clara (always remember this)
YOUR LANGUAGE: $langName (ONLY $langName, never switch)

🏠 APP CONTEXT:
This app helps users:
- Create personalized cleaning schedules
- Track cleaning tasks by room
- Share responsibilities with household members
- Get reminders and stay organized

📋 YOUR ROLE:
You're conducting the INITIAL SETUP INTERVIEW. This is the user's first time in the app. You need to gather information so the app can:
- Create their household profile
- Set up their rooms
- Build their personalized cleaning schedule
- Configure the dashboard

⚠️ CRITICAL RULES:
- You ONLY discuss home cleaning and household organization. NOTHING ELSE.
- You MUST gather ALL 7 pieces of information below
- Stay 100% focused on gathering data

🎯 THE 7 ESSENTIAL PIECES OF INFORMATION:
1. **Rooms**: How many? What types? (bedroom, bathroom, kitchen, living room, etc.)
2. **Size**: Approximate square footage or room sizes
3. **People**: Who lives there? (adults, children, ages if relevant)
4. **Pets**: Any pets? What types?
5. **Current frequency**: How often do they clean now?
6. **Problem areas**: Which areas get messy/need most attention?
7. **Time available**: How much time can they dedicate to cleaning?

🚫 ABSOLUTE PROHIBITIONS - NEVER DO THESE:
❌ NO small talk (weather, sports, news, politics, personal life)
❌ NO answering general questions unrelated to cleaning
❌ NO jokes, stories, or casual conversation
❌ NO switching languages (STAY IN $langName ALWAYS)
❌ NO saying you don't have a name (YOUR NAME IS CLARA)
❌ NO offering to clean anything (you're info gatherer, not cleaner)
❌ NO discussing your own abilities or limitations
❌ NO speaking Czech, English, or any language except $langName

✅ REQUIRED BEHAVIOR:
- START the conversation with a brief intro and first question
- Ask ONE question at a time
- Keep responses SHORT (1-2 sentences max)
- If user goes off-topic, IMMEDIATELY redirect: "Let's focus on your home. [Ask next question]"
- Track which info you've collected, ask for missing pieces
- When you have all 7 pieces, confirm completion

📝 OPENING - Say EXACTLY this (translated to $langName):
"Hi! I'm Clara. I'll help set up your cleaning plan. Tell me about your home - how many rooms do you have, and what types?"

DO NOT say generic things like:
❌ "I'm here to help with any information"
❌ "How can I assist you?"
❌ "What can I do for you?"

ONLY use the exact opening above, asking specifically about rooms.

🎬 REDIRECTION TEMPLATE:
Off-topic input → "Let's focus on your home. [Next question from the 7 essentials]"

✅ COMPLETION:
When user says "done/finish/stop" AND you have all 7 pieces → "Perfect! I have everything to create your cleaning plan. Let's get started!"

═══════════════════════════════════════════════════════
⚠️ FINAL REMINDERS - READ EVERY TIME BEFORE RESPONDING ⚠️

1. MY NAME IS CLARA (never forget this)
2. I speak ONLY $langName (never switch languages)
3. I gather information about the user's HOME (7 pieces)
4. I do NOT offer cleaning services
5. I do NOT switch to Czech, English, or any other language
6. I do NOT say I have no name (MY NAME IS CLARA)
7. EVERY response must be in $langName
8. EVERY question must gather one of the 7 pieces

STAY LASER-FOCUSED. Every question must gather one of the 7 pieces. NO exceptions."""
        
        val config = JSONObject().apply {
            put("type", "session.update")
            put("session", JSONObject().apply {
                put("modalities", JSONArray(listOf("text", "audio")))
                put("instructions", instructions)
                put("voice", "nova")
                put("input_audio_format", "pcm16")
                put("output_audio_format", "pcm16")
                put("turn_detection", JSONObject().apply {
                    put("type", "server_vad")
                    put("threshold", 0.5)
                    put("silence_duration_ms", 700)
                })
            })
        }
        
        webSocket?.send(config.toString())
        Log.d(TAG, "Sent session config for focused information gathering")
    }
    
    private fun startAudioRecording() {
        if (isRecording) return
        
        audioRecord?.startRecording()
        isRecording = true
        
        serviceScope.launch {
            val buffer = ByteArray(1024)
            
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    // Send audio to WebSocket
                    val base64Audio = Base64.getEncoder().encodeToString(buffer.copyOf(read))
                    val audioMessage = JSONObject().apply {
                        put("type", "input_audio_buffer.append")
                        put("audio", base64Audio)
                    }
                    webSocket?.send(audioMessage.toString())
                }
            }
        }
    }
    
    private fun stopAudioRecording() {
        if (!isRecording) return
        
        isRecording = false
        audioRecord?.stop()
        
        // Commit the audio buffer
        val commitMessage = JSONObject().apply {
            put("type", "input_audio_buffer.commit")
        }
        webSocket?.send(commitMessage.toString())
        
        // Create response
        val responseMessage = JSONObject().apply {
            put("type", "response.create")
        }
        webSocket?.send(responseMessage.toString())
    }
    
    private fun isCompletionPhrase(text: String): Boolean {
        val lowerText = text.lowercase().trim()
        return COMPLETION_PHRASES.any { phrase ->
            lowerText.contains(phrase)
        }
    }
    
    private fun isConfirmingCompletion(text: String): Boolean {
        val lowerText = text.lowercase()
        val confirmationKeywords = listOf(
            "great", "perfect", "got it", "all set", "ready",
            "dashboard", "let's get started", "begin", "start planning"
        )
        return confirmationKeywords.any { lowerText.contains(it) } && 
               (lowerText.contains("enough") || lowerText.contains("ready") || lowerText.contains("start"))
    }
    
    private fun completeSession() {
        val fullTranscript = conversationLog.joinToString("\n")
        Log.d(TAG, "Session complete. Full transcript:\n$fullTranscript")
        
        // Update state to trigger navigation
        _state.value = RealtimeState.SessionComplete(fullTranscript)
        
        // Close WebSocket gracefully
        serviceScope.launch {
            kotlinx.coroutines.delay(1000) // Give UI time to react
            stopRealtimeSession()
        }
    }
    
    /**
     * Switch language mid-conversation with proper cancellation
     * Implements atomic language switching to prevent mixed-language output
     */
    suspend fun switchLanguage(newLanguage: String) {
        // Debounce: if already cancelling, queue this switch
        if (isCancelling) {
            Log.d(TAG, "Already cancelling, queueing language switch to $newLanguage")
            pendingLanguageSwitch = newLanguage
            return
        }
        
        // No-op if already in target language
        if (newLanguage == currentLanguage) {
            Log.d(TAG, "Already in language $newLanguage, ignoring")
            return
        }
        
        Log.d(TAG, "[LanguageSwitch] START: $currentLanguage → $newLanguage, turn=$turnVersion")
        Log.d(TAG, "[LanguageSwitch] ActiveResponseId=$activeResponseId, droppedDeltas=$droppedDeltas")
        isCancelling = true
        _isSwitchingLanguage.value = true
        cancelStartTime = System.currentTimeMillis()
        
        // 1. Cancel any in-flight response
        val responseIdToCancel = activeResponseId
        if (responseIdToCancel != null) {
            Log.d(TAG, "[LanguageSwitch] Step 1: Cancelling response $responseIdToCancel")
            val cancelMessage = JSONObject().apply {
                put("type", "response.cancel")
            }
            webSocket?.send(cancelMessage.toString())
        } else {
            Log.d(TAG, "[LanguageSwitch] Step 1: No active response to cancel")
        }
        
        // 2. Stop local audio playback immediately
        Log.d(TAG, "[LanguageSwitch] Step 2: Stopping local playback")
        stopLocalPlayback()
        
        // 3. Clear UI transcripts
        Log.d(TAG, "[LanguageSwitch] Step 3: Clearing transcripts")
        _claraTranscript.value = ""
        _userTranscript.value = ""
        
        // 4. Wait for cancellation acknowledgment or timeout
        Log.d(TAG, "[LanguageSwitch] Step 4: Waiting for cancel ACK (timeout ${CANCEL_TIMEOUT_MS}ms)")
        val cancelSuccess = withContext(Dispatchers.IO) {
            var elapsed = 0L
            val pollInterval = 50L
            
            while (elapsed < CANCEL_TIMEOUT_MS) {
                if (activeResponseId == null) {
                    Log.d(TAG, "[LanguageSwitch] Cancel ACK received after ${elapsed}ms")
                    return@withContext true
                }
                kotlinx.coroutines.delay(pollInterval)
                elapsed += pollInterval
            }
            
            Log.w(TAG, "[LanguageSwitch] Cancel timeout after ${CANCEL_TIMEOUT_MS}ms - forcing clear")
            return@withContext false
        }
        
        // Hard-kill fallback if cancel didn't complete
        if (!cancelSuccess) {
            Log.w(TAG, "[LanguageSwitch] Hard-kill: Force clearing activeResponseId")
            activeResponseId = null
            // Consider full session restart if this happens repeatedly
        }
        
        // 5. Update session with new language
        currentLanguage = newLanguage
        turnVersion++
        droppedDeltas = 0 // Reset counter for new turn
        
        Log.d(TAG, "[LanguageSwitch] Step 5: Updating session to language=$newLanguage, turn=$turnVersion")
        sendSessionConfig(newLanguage)
        
        // 6. Wait for session.updated confirmation
        Log.d(TAG, "[LanguageSwitch] Step 6: Waiting for session.updated (100ms)")
        kotlinx.coroutines.delay(100)
        
        // 7. Trigger new greeting in new language
        Log.d(TAG, "[LanguageSwitch] Step 7: Starting new response in $newLanguage")
        isCancelling = false
        _isSwitchingLanguage.value = false
        triggerInitialGreeting(newLanguage)
        
        val totalTime = System.currentTimeMillis() - cancelStartTime
        Log.d(TAG, "[LanguageSwitch] COMPLETE: Total time ${totalTime}ms, droppedDeltas=$droppedDeltas")
        
        // 8. Process any pending switch (last-write-wins)
        pendingLanguageSwitch?.let { pending ->
            Log.d(TAG, "[LanguageSwitch] Processing queued switch to $pending")
            pendingLanguageSwitch = null
            if (pending != newLanguage) {
                switchLanguage(pending)
            }
        }
    }
    
    private fun stopLocalPlayback() {
        try {
            // Stop audio playback
            if (isPlaying) {
                audioTrack?.pause()
                audioTrack?.flush()
                isPlaying = false
            }
            Log.d(TAG, "Local playback stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping playback", e)
        }
    }
    
    /**
     * Get debug state for monitoring/overlay
     */
    fun getDebugState(): String {
        return """
            lang=$currentLanguage
            turn=$turnVersion
            activeResponseId=$activeResponseId
            droppedDeltas=$droppedDeltas
            isCancelling=$isCancelling
            pendingLang=$pendingLanguageSwitch
            state=${_state.value::class.simpleName}
        """.trimIndent()
    }
    
    /**
     * Stop the realtime session
     */
    fun stopRealtimeSession() {
        Log.d(TAG, "Stopping realtime session")
        
        isRecording = false
        isPlaying = false
        sessionConfigured = false
        isCancelling = false
        activeResponseId = null
        turnVersion = 0
        droppedDeltas = 0
        
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        
        webSocket?.close(1000, "Session ended")
        webSocket = null
        
        _state.value = RealtimeState.Idle
        _userTranscript.value = ""
        _claraTranscript.value = ""
        conversationLog.clear()
    }
    
    private fun triggerInitialGreeting(language: String) {
        val langName = when (language) {
            "es" -> "Spanish"
            "fr" -> "French"
            "de" -> "German"
            "uk" -> "Ukrainian"
            else -> "English"
        }
        
        // Send a response.create to make Clara speak first IN THE CORRECT LANGUAGE
        val currentTurn = turnVersion
        val responseMessage = JSONObject().apply {
            put("type", "response.create")
            put("response", JSONObject().apply {
                put("modalities", JSONArray(listOf("text", "audio")))
                put("instructions", """You MUST respond in $langName. 
                    |Say EXACTLY this in $langName (translate if needed):
                    |"Hi! I'm Clara. I'll help set up your cleaning plan. Tell me about your home - how many rooms do you have, and what types?"
                    |
                    |Remember: Respond ONLY in $langName. Do not use English.""".trimMargin())
                // Metadata for turn tracking
                put("metadata", JSONObject().apply {
                    put("turnVersion", currentTurn)
                    put("language", language)
                })
            })
        }
        webSocket?.send(responseMessage.toString())
        Log.d(TAG, "Triggered Clara's initial greeting in $langName (turn=$currentTurn)")
    }
    
    inner class RealtimeWebSocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket connected")
            _state.value = RealtimeState.Connected
            
            serviceScope.launch {
                currentLanguage = languagePrefsDataStore.languageCode.first()
                Log.d(TAG, "Using language: $currentLanguage")
                
                // Send session config
                sendSessionConfig(currentLanguage)
                
                // Don't trigger greeting here - wait for session.updated confirmation
                Log.d(TAG, "Waiting for session.updated confirmation...")
            }
        }
        
        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val json = JSONObject(text)
                val type = json.optString("type")
                
                when (type) {
                    "session.created" -> {
                        Log.d(TAG, "Session created")
                    }
                    
                    "session.updated" -> {
                        Log.d(TAG, "Session updated - configuration applied")
                        
                        // NOW it's safe to start the conversation
                        if (!sessionConfigured) {
                            sessionConfigured = true
                            
                            serviceScope.launch {
                                // Start recording
                                startAudioRecording()
                                
                                // Wait a moment, then trigger greeting
                                kotlinx.coroutines.delay(500)
                                triggerInitialGreeting(currentLanguage)
                            }
                        }
                    }
                    
                    "response.created" -> {
                        val response = json.optJSONObject("response")
                        val responseId = response?.optString("id")
                        responseId?.let {
                            activeResponseId = it
                            Log.d(TAG, "Response created: $it")
                        }
                    }
                    
                    "response.cancelled" -> {
                        val cancelledId = json.optJSONObject("response")?.optString("id")
                        val cancelTime = System.currentTimeMillis() - cancelStartTime
                        Log.d(TAG, "[LanguageSwitch] Response cancelled confirmed (id=$cancelledId, took ${cancelTime}ms)")
                        activeResponseId = null
                        // Note: isCancelling will be cleared by switchLanguage after session update
                    }
                    
                    "conversation.item.created" -> {
                        val item = json.getJSONObject("item")
                        val role = item.optString("role")
                        Log.d(TAG, "Item created: role=$role")
                    }
                    
                    "response.audio.delta" -> {
                        // Extract response metadata for filtering
                        val responseId = json.optString("response_id")
                        val itemId = json.optString("item_id")
                        
                        // Guard 1: Check if we're cancelling - ignore stale audio
                        if (isCancelling) {
                            droppedDeltas++
                            Log.d(TAG, "[DeltaFilter] Dropped audio delta during cancellation (responseId=$responseId, dropped=$droppedDeltas)")
                            return
                        }
                        
                        // Guard 2: Check response ID matches active response
                        if (activeResponseId != null && responseId.isNotEmpty() && responseId != activeResponseId) {
                            droppedDeltas++
                            Log.d(TAG, "[DeltaFilter] Dropped audio delta from stale response (expected=$activeResponseId, got=$responseId, dropped=$droppedDeltas)")
                            return
                        }
                        
                        // Receive audio from Clara
                        val delta = json.optString("delta")
                        if (delta.isNotEmpty()) {
                            playAudioChunk(delta)
                        }
                    }
                    
                    "response.audio_transcript.delta" -> {
                        // Extract response metadata for filtering
                        val responseId = json.optString("response_id")
                        val itemId = json.optString("item_id")
                        
                        // Guard 1: Check if we're cancelling - ignore stale transcripts
                        if (isCancelling) {
                            droppedDeltas++
                            Log.d(TAG, "[DeltaFilter] Dropped transcript delta during cancellation (responseId=$responseId, dropped=$droppedDeltas)")
                            return
                        }
                        
                        // Guard 2: Check response ID matches active response
                        if (activeResponseId != null && responseId.isNotEmpty() && responseId != activeResponseId) {
                            droppedDeltas++
                            Log.d(TAG, "[DeltaFilter] Dropped transcript delta from stale response (expected=$activeResponseId, got=$responseId, dropped=$droppedDeltas)")
                            return
                        }
                        
                        // Receive transcript from Clara
                        val delta = json.optString("delta")
                        _claraTranscript.value = _claraTranscript.value + delta
                        _state.value = RealtimeState.ClaraSpeaking(_claraTranscript.value)
                    }
                    
                    "response.audio_transcript.done" -> {
                        val claraResponse = _claraTranscript.value
                        Log.d(TAG, "Clara transcript complete: $claraResponse")
                        
                        // Log Clara's response
                        if (claraResponse.isNotBlank()) {
                            conversationLog.add("Clara: $claraResponse")
                        }
                        
                        // Check if Clara is confirming completion
                        if (isConfirmingCompletion(claraResponse)) {
                            Log.d(TAG, "Clara confirmed completion, ending session")
                            completeSession()
                        }
                    }
                    
                    "conversation.item.input_audio_transcription.completed" -> {
                        // User's speech transcribed
                        val transcript = json.optString("transcript")
                        _userTranscript.value = transcript
                        Log.d(TAG, "User transcript: $transcript")
                        
                        // Log conversation
                        conversationLog.add("User: $transcript")
                        
                        // Check for completion phrases
                        if (isCompletionPhrase(transcript)) {
                            Log.d(TAG, "Completion phrase detected!")
                            // Let Clara respond first, then complete
                        }
                    }
                    
                    "input_audio_buffer.speech_started" -> {
                        Log.d(TAG, "User started speaking")
                        _state.value = RealtimeState.UserSpeaking
                        _claraTranscript.value = ""
                    }
                    
                    "input_audio_buffer.speech_stopped" -> {
                        Log.d(TAG, "User stopped speaking")
                    }
                    
                    "response.done" -> {
                        Log.d(TAG, "Response complete")
                        _state.value = RealtimeState.Connected
                        // Ready for next turn
                    }
                    
                    "error" -> {
                        val error = json.optJSONObject("error")
                        val message = error?.optString("message") ?: "Unknown error"
                        Log.e(TAG, "Realtime API error: $message")
                        _state.value = RealtimeState.Error(message)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing message", e)
            }
        }
        
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket failure", t)
            _state.value = RealtimeState.Error(t.message ?: "Connection failed")
        }
        
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closed: $code - $reason")
            _state.value = RealtimeState.Idle
        }
    }
    
    private fun playAudioChunk(base64Audio: String) {
        try {
            val audioData = Base64.getDecoder().decode(base64Audio)
            
            if (!isPlaying) {
                audioTrack?.play()
                isPlaying = true
            }
            
            audioTrack?.write(audioData, 0, audioData.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio chunk", e)
        }
    }
}

