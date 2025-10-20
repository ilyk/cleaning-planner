package com.ilyk.cleaningplanner.feature.clara.service

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.ilyk.cleaningplanner.core.model.VoiceStyle
import com.ilyk.cleaningplanner.data.network.api.OpenAIApi
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed class TTSState {
    data object Idle : TTSState()
    data object Loading : TTSState()
    data class Speaking(val text: String, val currentCaption: String = "") : TTSState()
    data object Error : TTSState()
}

@Singleton
class TTSService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val openAIApi: OpenAIApi,
    private val openAIConfigDataStore: OpenAIConfigDataStore
) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val pendingSpeechQueue = mutableListOf<Pair<String, VoiceStyle>>()
    private var mediaPlayer: MediaPlayer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow<TTSState>(TTSState.Idle)
    val state: StateFlow<TTSState> = _state.asStateFlow()

    init {
        initializeTTS()
    }

    private fun initializeTTS() {
        Log.d(TAG, "Initializing TTS")
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                isInitialized = true
                Log.d(TAG, "TTS initialized successfully")
                
                // Process any pending speech requests
                if (pendingSpeechQueue.isNotEmpty()) {
                    Log.d(TAG, "Processing ${pendingSpeechQueue.size} pending speech requests")
                    val pending = pendingSpeechQueue.toList()
                    pendingSpeechQueue.clear()
                    pending.forEach { (text, style) ->
                        speakInternal(text, style)
                    }
                }
            } else {
                Log.e(TAG, "TTS initialization failed with status: $status")
                _state.value = TTSState.Error
            }
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "TTS started: $utteranceId")
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "TTS done: $utteranceId")
                _state.value = TTSState.Idle
            }

            override fun onError(utteranceId: String?) {
                Log.e(TAG, "TTS error: $utteranceId")
                _state.value = TTSState.Error
            }
        })
    }

    fun speak(text: String, voiceStyle: VoiceStyle) {
        Log.d(TAG, "speak() called with text length=${text.length}, style=$voiceStyle")
        
        // Show loading state
        _state.value = TTSState.Loading
        
        // Try OpenAI TTS first for better quality
        serviceScope.launch {
            try {
                speakWithOpenAI(text, voiceStyle)
            } catch (e: Exception) {
                Log.w(TAG, "OpenAI TTS failed, falling back to system TTS", e)
                // Fallback to system TTS
                if (!isInitialized) {
                    Log.d(TAG, "System TTS not ready yet, queueing speech request")
                    pendingSpeechQueue.add(text to voiceStyle)
                    _state.value = TTSState.Idle
                } else {
                    speakInternal(text, voiceStyle)
                }
            }
        }
    }
    
    private suspend fun speakWithOpenAI(text: String, voiceStyle: VoiceStyle) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Using OpenAI TTS")
        val config = openAIConfigDataStore.openAIConfig.first()
        
        if (config.apiKey.isBlank()) {
            throw Exception("No API key configured")
        }
        
        // Map voice style to OpenAI voices
        val voice = when (voiceStyle) {
            VoiceStyle.WARM -> "nova"      // Warm, friendly female
            VoiceStyle.BRIGHT -> "shimmer" // Bright, energetic female
            VoiceStyle.CALM -> "alloy"     // Calm, neutral
        }
        
        val jsonBody = JSONObject().apply {
            put("model", "tts-1")  // or "tts-1-hd" for higher quality
            put("input", text)
            put("voice", voice)
            put("speed", 1.0)
        }
        
        val requestBody = jsonBody.toString()
            .toRequestBody("application/json".toMediaType())
        
        val response = openAIApi.createSpeech(
            authorization = "Bearer ${config.apiKey}",
            request = requestBody
        )
        
        // Save audio to temp file
        val audioFile = File(context.cacheDir, "tts_${System.currentTimeMillis()}.mp3")
        audioFile.outputStream().use { output ->
            response.byteStream().use { input ->
                input.copyTo(output)
            }
        }
        
        Log.d(TAG, "Audio saved to ${audioFile.absolutePath}, size=${audioFile.length()} bytes")
        
        // Play with MediaPlayer and sync captions
        withContext(Dispatchers.Main) {
            mediaPlayer?.release()
            
            // Split text into caption chunks with word-based timing
            val captionChunks = splitIntoCaptionsWithTiming(text)
            
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                setOnCompletionListener {
                    Log.d(TAG, "OpenAI TTS playback completed")
                    _state.value = TTSState.Idle
                    audioFile.delete()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    _state.value = TTSState.Error
                    audioFile.delete()
                    true
                }
                prepare()
                val duration = this.duration
                _state.value = TTSState.Speaking(text, captionChunks.firstOrNull()?.text ?: "")
                start()
                Log.d(TAG, "OpenAI TTS playback started, duration=$duration ms, ${captionChunks.size} captions")
                
                // Calculate timing based on word count per caption
                val totalWords = captionChunks.sumOf { it.wordCount }
                val msPerWord = duration.toFloat() / totalWords
                
                // Update captions based on word timing
                val player = this
                serviceScope.launch {
                    try {
                        var accumulatedTime = 0L
                        captionChunks.forEach { chunk ->
                            val chunkDuration = (chunk.wordCount * msPerWord).toLong()
                            kotlinx.coroutines.delay(chunkDuration)
                            accumulatedTime += chunkDuration
                            
                            // Check if player is still valid before checking isPlaying
                            try {
                                if (player.isPlaying) {
                                    _state.value = TTSState.Speaking(text, chunk.text)
                                    Log.d(TAG, "Caption: ${chunk.text.take(30)}... (${chunk.wordCount} words, ${chunkDuration}ms)")
                                }
                            } catch (e: IllegalStateException) {
                                // Player was released, stop caption updates
                                Log.d(TAG, "MediaPlayer released, stopping caption updates")
                                return@launch
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating captions", e)
                    }
                }
            }
        }
    }
    
    private fun speakInternal(text: String, voiceStyle: VoiceStyle) {
        tts?.let { engine ->
            engine.stop()
            
            engine.setPitch(getPitchForStyle(voiceStyle))
            engine.setSpeechRate(getRateForStyle(voiceStyle))
            
            _state.value = TTSState.Speaking(text)
            Log.d(TAG, "Starting system TTS playback")
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "clara_utterance")
        } ?: Log.e(TAG, "TTS engine is null")
    }
    
    private data class CaptionChunk(val text: String, val wordCount: Int)
    
    private fun splitIntoCaptionsWithTiming(text: String): List<CaptionChunk> {
        // Split on sentence boundaries but keep punctuation
        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
        val captions = mutableListOf<CaptionChunk>()
        var currentCaption = ""
        var currentWordCount = 0
        
        sentences.forEach { sentence ->
            val trimmed = sentence.trim()
            if (trimmed.isEmpty()) return@forEach
            
            val sentenceWords = trimmed.split(Regex("\\s+")).size
            
            // Try to group sentences up to 2 lines (~15 words or 100 chars)
            if (currentCaption.isEmpty()) {
                currentCaption = trimmed
                currentWordCount = sentenceWords
            } else if (currentWordCount + sentenceWords <= 15 && (currentCaption + " " + trimmed).length <= 100) {
                currentCaption += " $trimmed"
                currentWordCount += sentenceWords
            } else {
                // Save current caption and start new one
                if (currentCaption.isNotEmpty()) {
                    captions.add(CaptionChunk(currentCaption, currentWordCount))
                }
                currentCaption = trimmed
                currentWordCount = sentenceWords
            }
        }
        
        // Add last caption
        if (currentCaption.isNotEmpty()) {
            captions.add(CaptionChunk(currentCaption, currentWordCount))
        }
        
        return captions.ifEmpty { 
            val words = text.split(Regex("\\s+")).size
            listOf(CaptionChunk(text.take(100), words))
        }
    }
    
    companion object {
        private const val TAG = "TTSService"
    }

    fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        tts?.stop()
        _state.value = TTSState.Idle
    }

    private fun getPitchForStyle(style: VoiceStyle): Float {
        return when (style) {
            VoiceStyle.WARM -> 1.0f
            VoiceStyle.BRIGHT -> 1.2f
            VoiceStyle.CALM -> 0.9f
        }
    }

    private fun getRateForStyle(style: VoiceStyle): Float {
        return when (style) {
            VoiceStyle.WARM -> 1.0f
            VoiceStyle.BRIGHT -> 1.1f
            VoiceStyle.CALM -> 0.9f
        }
    }

    fun shutdown() {
        mediaPlayer?.release()
        mediaPlayer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}

