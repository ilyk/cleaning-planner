package com.ilyk.cleaningplanner.feature.clara.service

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.ilyk.cleaningplanner.core.model.VoiceStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed class TTSState {
    data object Idle : TTSState()
    data class Speaking(val text: String) : TTSState()
    data object Error : TTSState()
}

@Singleton
class TTSService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _state = MutableStateFlow<TTSState>(TTSState.Idle)
    val state: StateFlow<TTSState> = _state.asStateFlow()

    init {
        initializeTTS()
    }

    private fun initializeTTS() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                isInitialized = true
            } else {
                _state.value = TTSState.Error
            }
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
            }

            override fun onDone(utteranceId: String?) {
                _state.value = TTSState.Idle
            }

            override fun onError(utteranceId: String?) {
                _state.value = TTSState.Error
            }
        })
    }

    fun speak(text: String, voiceStyle: VoiceStyle) {
        if (!isInitialized) {
            _state.value = TTSState.Error
            return
        }

        tts?.let { engine ->
            engine.stop()
            
            engine.setPitch(getPitchForStyle(voiceStyle))
            engine.setSpeechRate(getRateForStyle(voiceStyle))
            
            _state.value = TTSState.Speaking(text)
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "clara_utterance")
        }
    }

    fun stop() {
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
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}

