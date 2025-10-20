package com.ilyk.cleaningplanner.feature.clara.service

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.ilyk.cleaningplanner.data.network.api.OpenAIApi
import com.ilyk.cleaningplanner.feature.clara.data.OpenAIConfigDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Streaming TTS service that progressively generates and plays audio
 * to reduce perceived latency.
 */
@Singleton
class StreamingTTSService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val openAIApi: OpenAIApi,
    private val openAIConfigDataStore: OpenAIConfigDataStore
) {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val audioQueue = ConcurrentLinkedQueue<File>()
    private var currentPlayer: MediaPlayer? = null
    private val _isPlaying = MutableStateFlow(false)
    
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()
    
    private var currentTextJob: kotlinx.coroutines.Job? = null
    private var currentAudioJob: kotlinx.coroutines.Job? = null
    
    /**
     * Speaks text with streaming effect:
     * - Splits text into sentences
     * - Generates TTS for each sentence progressively
     * - Plays audio while generating next chunks
     * - Streams text to UI word-by-word
     */
    suspend fun speakStreaming(
        text: String,
        onTextChunk: (String) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        Log.d(TAG, "Starting streaming speech for text length=${text.length}")
        _isSpeaking.value = true
        
        try {
            // Split into sentences for progressive generation
            val sentences = splitIntoSentences(text)
            Log.d(TAG, "Split into ${sentences.size} sentences")
            
            // Start text streaming animation
            currentTextJob = serviceScope.launch {
                streamTextToUI(text, onTextChunk)
            }
            
            // Generate and play audio progressively
            currentAudioJob = serviceScope.launch {
                sentences.forEachIndexed { index, sentence ->
                    if (sentence.isBlank()) return@forEachIndexed
                    
                    Log.d(TAG, "Generating audio for sentence $index: ${sentence.take(50)}...")
                    
                    try {
                        // Generate TTS for this sentence
                        val audioFile = generateTTSForSentence(sentence, index)
                        
                        // Add to queue
                        audioQueue.offer(audioFile)
                        
                        // Start playing if not already
                        if (!_isPlaying.value) {
                            playNextInQueue()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error generating sentence $index", e)
                    }
                }
            }
            
            // Wait for both to complete
            currentTextJob?.join()
            currentAudioJob?.join()
            
            // Wait for audio queue to finish
            while (audioQueue.isNotEmpty() || _isPlaying.value) {
                delay(100)
            }
            
            _isSpeaking.value = false
            onComplete()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in streaming speech", e)
            _isSpeaking.value = false
            onError(e.message ?: "Unknown error")
        }
    }
    
    private suspend fun streamTextToUI(text: String, onTextChunk: (String) -> Unit) {
        // Stream text word-by-word for typewriter effect
        val words = text.split(" ")
        val wordsPerSecond = 8 // Adjust for natural reading speed
        val delayMs = 1000L / wordsPerSecond
        
        words.forEach { word ->
            onTextChunk("$word ")
            delay(delayMs)
        }
    }
    
    private fun splitIntoSentences(text: String): List<String> {
        // Split on sentence boundaries
        return text.split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
    
    private suspend fun generateTTSForSentence(sentence: String, index: Int): File = withContext(Dispatchers.IO) {
        val config = openAIConfigDataStore.openAIConfig.first()
        
        val jsonBody = JSONObject().apply {
            put("model", "tts-1") // Use faster tts-1 for streaming
            put("input", sentence)
            put("voice", "nova")
            put("speed", 1.0)
        }
        
        val requestBody = jsonBody.toString()
            .toRequestBody("application/json".toMediaType())
        
        val response = openAIApi.createSpeech(
            authorization = "Bearer ${config.apiKey}",
            request = requestBody
        )
        
        // Save to temp file
        val audioFile = File(context.cacheDir, "tts_stream_${index}_${System.currentTimeMillis()}.mp3")
        audioFile.outputStream().use { output ->
            response.byteStream().use { input ->
                input.copyTo(output)
            }
        }
        
        Log.d(TAG, "Generated audio file: ${audioFile.name}, size=${audioFile.length()}")
        audioFile
    }
    
    private fun playNextInQueue() {
        if (_isPlaying.value) return
        
        val audioFile = audioQueue.poll() ?: return
        _isPlaying.value = true
        
        serviceScope.launch(Dispatchers.Main) {
            try {
                currentPlayer?.release()
                currentPlayer = MediaPlayer().apply {
                    setDataSource(audioFile.absolutePath)
                    setOnCompletionListener {
                        Log.d(TAG, "Completed playing: ${audioFile.name}")
                        audioFile.delete()
                        _isPlaying.value = false
                        
                        // Play next in queue
                        playNextInQueue()
                    }
                    setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                        audioFile.delete()
                        _isPlaying.value = false
                        playNextInQueue()
                        true
                    }
                    prepare()
                    start()
                    Log.d(TAG, "Started playing: ${audioFile.name}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error playing audio", e)
                audioFile.delete()
                _isPlaying.value = false
                playNextInQueue()
            }
        }
    }
    
    fun stop() {
        Log.d(TAG, "Stopping streaming TTS - canceling jobs and clearing queue")
        
        // Cancel ongoing jobs immediately
        currentTextJob?.cancel()
        currentAudioJob?.cancel()
        currentTextJob = null
        currentAudioJob = null
        
        serviceScope.launch {
            try {
                currentPlayer?.stop()
            } catch (e: Exception) {
                // Player might be in invalid state
            }
            currentPlayer?.release()
            currentPlayer = null
            _isPlaying.value = false
            _isSpeaking.value = false
            
            // Clear and delete queued files
            while (audioQueue.isNotEmpty()) {
                audioQueue.poll()?.delete()
            }
            
            Log.d(TAG, "Streaming TTS stopped and cleaned up")
        }
    }
    
    companion object {
        private const val TAG = "StreamingTTSService"
    }
}

