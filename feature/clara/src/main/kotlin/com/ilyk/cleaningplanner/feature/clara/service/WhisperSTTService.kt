package com.ilyk.cleaningplanner.feature.clara.service

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.ilyk.cleaningplanner.data.network.api.OpenAIApi
import com.ilyk.cleaningplanner.feature.clara.data.OpenAIConfigDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for Speech-to-Text using OpenAI Whisper API
 */
@Singleton
class WhisperSTTService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val openAIApi: OpenAIApi,
    private val openAIConfigDataStore: OpenAIConfigDataStore
) {
    private val recordingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingFile: File? = null
    
    // Voice Activity Detection
    private var onSpeechDetected: (() -> Unit)? = null
    private var onSilenceDetected: ((String) -> Unit)? = null
    private val silenceThresholdMs = 1500L // 1.5 seconds of silence
    private var lastSpeechTime = 0L
    private var isSpeaking = false
    private var currentLanguage = "en"
    
    companion object {
        private const val TAG = "WhisperSTTService"
        private const val SAMPLE_RATE = 16000 // 16kHz for Whisper
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
    
    /**
     * Start continuous listening with Voice Activity Detection
     */
    suspend fun startContinuousListening(
        language: String = "en",
        onSpeechStart: () -> Unit,
        onSpeechEnd: (String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        this@WhisperSTTService.currentLanguage = language
        this@WhisperSTTService.onSpeechDetected = onSpeechStart
        this@WhisperSTTService.onSilenceDetected = onSpeechEnd
        startRecording()
    }
    
    /**
     * Start recording audio from microphone
     */
    private suspend fun startRecording(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isRecording) {
                return@withContext Result.failure(Exception("Already recording"))
            }
            
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            
            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                return@withContext Result.failure(Exception("Invalid buffer size"))
            }
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                return@withContext Result.failure(Exception("AudioRecord not initialized"))
            }
            
            // Create temp file for recording
            recordingFile = File(context.cacheDir, "recording_${System.currentTimeMillis()}.pcm")
            
            audioRecord?.startRecording()
            isRecording = true
            
            Log.d(TAG, "Started recording to ${recordingFile?.absolutePath}")
            
            // Record in background
            recordingScope.launch {
                recordAudioData()
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            Result.failure(e)
        }
    }
    
    private suspend fun recordAudioData() = withContext(Dispatchers.IO) {
        val buffer = ByteArray(1024)
        val outputStream = FileOutputStream(recordingFile)
        
        try {
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    outputStream.write(buffer, 0, read)
                    
                    // Voice Activity Detection - check amplitude
                    val amplitude = calculateAmplitude(buffer, read)
                    val isSpeechDetected = amplitude > 2000 // Threshold for speech
                    
                    if (isSpeechDetected) {
                        lastSpeechTime = System.currentTimeMillis()
                        if (!isSpeaking) {
                            isSpeaking = true
                            withContext(Dispatchers.Main) {
                                onSpeechDetected?.invoke()
                            }
                        }
                    } else if (isSpeaking) {
                        // Check for silence
                        val silenceDuration = System.currentTimeMillis() - lastSpeechTime
                        if (silenceDuration > silenceThresholdMs) {
                            isSpeaking = false
                            
                            // Stop recording and transcribe
                            val result = stopRecordingInternal(currentLanguage)
                            if (result.isSuccess) {
                                withContext(Dispatchers.Main) {
                                    onSilenceDetected?.invoke(result.getOrNull() ?: "")
                                }
                            }
                            
                            // Start new recording immediately for next utterance
                            kotlinx.coroutines.delay(500)
                            if (isRecording) {
                                recordingFile = File(context.cacheDir, "recording_${System.currentTimeMillis()}.pcm")
                                // Continue recording to new file
                            }
                        }
                    }
                }
            }
        } finally {
            outputStream.close()
        }
    }
    
    private fun calculateAmplitude(buffer: ByteArray, length: Int): Int {
        var sum = 0L
        for (i in 0 until length step 2) {
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
            sum += kotlin.math.abs(sample.toInt())
        }
        return (sum / (length / 2)).toInt()
    }
    
    /**
     * Stop continuous listening
     */
    fun stopContinuousListening() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        recordingFile?.delete()
        recordingFile = null
        onSpeechDetected = null
        onSilenceDetected = null
    }
    
    /**
     * Stop recording and transcribe the audio (internal use)
     */
    private suspend fun stopRecordingInternal(language: String = "en"): Result<String> = withContext(Dispatchers.IO) {
        try {
            val pcmFile = recordingFile ?: return@withContext Result.failure(Exception("No recording file"))
            
            Log.d(TAG, "Transcribing recording, file size: ${pcmFile.length()} bytes")
            
            // Convert PCM to WAV format (Whisper requires WAV/MP3/etc)
            val wavFile = convertPcmToWav(pcmFile)
            
            if (wavFile.length() < 1000) {
                wavFile.delete()
                return@withContext Result.failure(Exception("Recording too short"))
            }
            
            // Transcribe using Whisper API
            val transcript = transcribeAudio(wavFile, language)
            wavFile.delete()
            
            Log.d(TAG, "Transcription: $transcript")
            Result.success(transcript)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in transcription", e)
            Result.failure(e)
        }
    }
    
    /**
     * Stop recording and transcribe the audio (public API for manual stop)
     */
    suspend fun stopRecordingAndTranscribe(language: String = "en"): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!isRecording) {
                return@withContext Result.failure(Exception("Not currently recording"))
            }
            
            // Stop recording
            isRecording = false
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            
            val pcmFile = recordingFile ?: return@withContext Result.failure(Exception("No recording file"))
            
            Log.d(TAG, "Stopped recording, file size: ${pcmFile.length()} bytes")
            
            // Convert PCM to WAV format (Whisper requires WAV/MP3/etc)
            val wavFile = convertPcmToWav(pcmFile)
            pcmFile.delete()
            
            if (wavFile.length() < 1000) {
                wavFile.delete()
                return@withContext Result.failure(Exception("Recording too short"))
            }
            
            // Transcribe using Whisper API
            val transcript = transcribeAudio(wavFile, language)
            wavFile.delete()
            
            Log.d(TAG, "Transcription: $transcript")
            Result.success(transcript)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in stopRecordingAndTranscribe", e)
            recordingFile?.delete()
            Result.failure(e)
        }
    }
    
    private suspend fun convertPcmToWav(pcmFile: File): File = withContext(Dispatchers.IO) {
        val wavFile = File(context.cacheDir, "recording_${System.currentTimeMillis()}.wav")
        
        val pcmData = pcmFile.readBytes()
        val totalDataLen = pcmData.size + 36
        val totalAudioLen = pcmData.size
        val channels = 1
        val byteRate = SAMPLE_RATE * channels * 2 // 16-bit = 2 bytes
        
        wavFile.outputStream().use { output ->
            // Write WAV header
            writeString(output, "RIFF")
            writeInt(output, totalDataLen)
            writeString(output, "WAVE")
            writeString(output, "fmt ")
            writeInt(output, 16) // Sub-chunk size
            writeShort(output, 1.toShort()) // Audio format (PCM)
            writeShort(output, channels.toShort())
            writeInt(output, SAMPLE_RATE)
            writeInt(output, byteRate)
            writeShort(output, (channels * 2).toShort()) // Block align
            writeShort(output, 16.toShort()) // Bits per sample
            writeString(output, "data")
            writeInt(output, totalAudioLen)
            
            // Write PCM data
            output.write(pcmData)
        }
        
        Log.d(TAG, "Converted PCM to WAV: ${wavFile.length()} bytes")
        wavFile
    }
    
    private fun writeString(output: java.io.OutputStream, s: String) {
        output.write(s.toByteArray())
    }
    
    private fun writeInt(output: java.io.OutputStream, value: Int) {
        output.write(value and 0xff)
        output.write((value shr 8) and 0xff)
        output.write((value shr 16) and 0xff)
        output.write((value shr 24) and 0xff)
    }
    
    private fun writeShort(output: java.io.OutputStream, value: Short) {
        output.write(value.toInt() and 0xff)
        output.write((value.toInt() shr 8) and 0xff)
    }
    
    private suspend fun transcribeAudio(audioFile: File, language: String): String = withContext(Dispatchers.IO) {
        val config = openAIConfigDataStore.openAIConfig.first()
        
        val requestBody = audioFile.asRequestBody("audio/wav".toMediaTypeOrNull())
        val audioPart = MultipartBody.Part.createFormData("file", audioFile.name, requestBody)
        val modelPart = MultipartBody.Part.createFormData("model", "whisper-1")
        val languagePart = MultipartBody.Part.createFormData("language", language)
        
        val response = openAIApi.transcribeAudio(
            authorization = "Bearer ${config.apiKey}",
            file = audioPart,
            model = modelPart,
            language = languagePart
        )
        
        response.text
    }
    
    fun cancelRecording() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        recordingFile?.delete()
        recordingFile = null
    }
}

