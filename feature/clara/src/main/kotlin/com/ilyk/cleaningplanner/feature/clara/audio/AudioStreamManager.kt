package com.ilyk.cleaningplanner.feature.clara.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import com.ilyk.cleaningplanner.feature.clara.protocol.ProtocolConstants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages audio capture and playback for Clara voice streaming
 * 
 * Features:
 * - AudioRecord capture at 24kHz mono PCM16
 * - Opus encoding (20ms frames)
 * - AudioTrack playback with jitter buffer
 * - Barge-in support (interrupt playback)
 */
class AudioStreamManager(
    private val coroutineScope: CoroutineScope
) {
    companion object {
        private const val TAG = "AudioStreamManager"
        
        // Audio configuration
        private const val SAMPLE_RATE = ProtocolConstants.SAMPLE_RATE
        private const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        
        // Frame size for 20ms at 24kHz mono PCM16
        private const val SAMPLES_PER_FRAME = (SAMPLE_RATE * ProtocolConstants.FRAME_DURATION_MS) / 1000
        private const val BYTES_PER_FRAME = SAMPLES_PER_FRAME * 2 // 16-bit = 2 bytes
    }

    // Audio recording
    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private val isCapturing = AtomicBoolean(false)

    // Audio playback
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private val isPlaying = AtomicBoolean(false)
    private val jitterBuffer = JitterBuffer()

    // Events
    private val _capturedAudio = MutableSharedFlow<ByteArray>(extraBufferCapacity = 50)
    val capturedAudio: SharedFlow<ByteArray> = _capturedAudio
    
    // Coroutine scope for audio operations
    private val audioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Start capturing audio from microphone
     */
    fun startCapture(): Boolean {
        if (isCapturing.get()) {
            Log.w(TAG, "Already capturing")
            return false
        }

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG_IN,
                AUDIO_FORMAT
            )

            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "Invalid audio configuration")
                return false
            }

            // Use larger buffer for stability
            val bufferSize = maxOf(minBufferSize, BYTES_PER_FRAME * 4)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG_IN,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            audioRecord?.startRecording()
            isCapturing.set(true)

            captureJob = coroutineScope.launch(Dispatchers.IO) {
                captureLoop()
            }

            Log.i(TAG, "Audio capture started")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio capture", e)
            audioRecord?.release()
            audioRecord = null
            return false
        }
    }

    /**
     * Stop capturing audio
     */
    fun stopCapture() {
        if (!isCapturing.getAndSet(false)) {
            return
        }

        captureJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        Log.i(TAG, "Audio capture stopped")
    }

    /**
     * Start playing audio output
     */
    fun startPlayback(): Boolean {
        if (isPlaying.get()) {
            Log.w(TAG, "Already playing")
            return false
        }

        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG_OUT,
                AUDIO_FORMAT
            )

            if (minBufferSize == AudioTrack.ERROR || minBufferSize == AudioTrack.ERROR_BAD_VALUE) {
                Log.e(TAG, "Invalid audio configuration for playback")
                return false
            }

            // Use larger buffer for jitter
            val bufferSize = maxOf(minBufferSize, BYTES_PER_FRAME * 10)

            audioTrack = AudioTrack.Builder()
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG_OUT)
                        .setEncoding(AUDIO_FORMAT)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .build()

            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "AudioTrack initialization failed")
                audioTrack?.release()
                audioTrack = null
                return false
            }

            audioTrack?.play()
            isPlaying.set(true)

            playbackJob = coroutineScope.launch(Dispatchers.IO) {
                playbackLoop()
            }

            Log.i(TAG, "Audio playback started")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio playback", e)
            audioTrack?.release()
            audioTrack = null
            return false
        }
    }

    /**
     * Stop playing audio (barge-in)
     */
    fun stopPlayback() {
        if (!isPlaying.getAndSet(false)) {
            return
        }

        playbackJob?.cancel()
        jitterBuffer.clear()
        audioTrack?.pause()
        audioTrack?.flush()
        audioTrack?.release()
        audioTrack = null

        Log.i(TAG, "Audio playback stopped (barge-in)")
    }

    /**
     * Queue audio data for playback (from server)
     */
    fun queueAudioOutput(base64Data: String, seq: Int) {
        try {
            val pcmData = Base64.decode(base64Data, Base64.DEFAULT)
            jitterBuffer.add(pcmData, seq)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode audio data", e)
        }
    }

    /**
     * Commit playback (all audio received)
     */
    fun commitPlayback() {
        jitterBuffer.commit()
    }

    /**
     * Cleanup all resources
     */
    fun release() {
        stopCapture()
        stopPlayback()
    }

    private suspend fun captureLoop() {
        val buffer = ByteArray(BYTES_PER_FRAME)

        while (isCapturing.get() && audioScope.isActive) {
            try {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: -1

                if (bytesRead > 0) {
                    // Create a copy to avoid buffer reuse issues
                    val audioFrame = buffer.copyOf(bytesRead)
                    
                    // Emit for encoding/transmission
                    _capturedAudio.emit(audioFrame)
                } else if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                    Log.e(TAG, "Invalid operation during capture")
                    break
                } else if (bytesRead == AudioRecord.ERROR_BAD_VALUE) {
                    Log.e(TAG, "Bad value during capture")
                    break
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in capture loop", e)
                break
            }
        }

        Log.d(TAG, "Capture loop exited")
    }

    private suspend fun playbackLoop() {
        while (isPlaying.get() && audioScope.isActive) {
            try {
                val audioData = jitterBuffer.poll()
                
                if (audioData != null) {
                    val written = audioTrack?.write(audioData, 0, audioData.size) ?: -1
                    
                    if (written < 0) {
                        Log.e(TAG, "Error writing to AudioTrack: $written")
                    }
                } else {
                    // Buffer underrun, wait a bit
                    delay(10)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in playback loop", e)
                break
            }
        }

        Log.d(TAG, "Playback loop exited")
    }
}

/**
 * Jitter buffer for smooth audio playback
 * Implements adaptive buffering with 80-120ms target
 */
private class JitterBuffer {
    private val buffer = mutableListOf<AudioFrame>()
    private var committed = false
    private var nextExpectedSeq = 1

    data class AudioFrame(
        val data: ByteArray,
        val seq: Int,
        val timestamp: Long = System.currentTimeMillis()
    )

    @Synchronized
    fun add(data: ByteArray, seq: Int) {
        buffer.add(AudioFrame(data, seq))
        buffer.sortBy { it.seq }
    }

    @Synchronized
    fun poll(): ByteArray? {
        // Wait for buffer to fill to minimum threshold (4 frames = ~80ms)
        if (buffer.size < 4 && !committed) {
            return null
        }

        // Get next frame in sequence
        val frame = buffer.firstOrNull { it.seq >= nextExpectedSeq }
        
        if (frame != null) {
            buffer.remove(frame)
            nextExpectedSeq = frame.seq + 1
            return frame.data
        }

        // If committed and no more frames, drain remaining
        if (committed && buffer.isNotEmpty()) {
            val lastFrame = buffer.removeAt(0)
            return lastFrame.data
        }

        return null
    }

    @Synchronized
    fun commit() {
        committed = true
    }

    @Synchronized
    fun clear() {
        buffer.clear()
        committed = false
        nextExpectedSeq = 1
    }
}

/**
 * Opus encoder stub (in production, use native Opus library)
 * For now, passes through PCM data
 */
object OpusEncoder {
    fun encode(pcmData: ByteArray): ByteArray {
        // TODO: Implement actual Opus encoding
        // For MVP, return PCM as-is (will need to update protocol or add Opus lib)
        return pcmData
    }
}

/**
 * PCM decoder (for PCM16 output from server)
 */
object PcmDecoder {
    fun decode(pcmData: ByteArray): ByteArray {
        // PCM16 is already in the format we need
        return pcmData
    }
}



