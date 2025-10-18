package com.ilyk.cleaningplanner.feature.clara.ui.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilyk.cleaningplanner.feature.clara.avatar.SceneViewAvatarProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val avatarProvider: SceneViewAvatarProvider
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(DiagnosticsUiState())
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()
    
    init {
        startMetricsCollection()
    }
    
    private fun startMetricsCollection() {
        viewModelScope.launch {
            while (true) {
                delay(100) // Update every 100ms
                
                val fps = avatarProvider.getCurrentFps()
                val frameTimeMs = if (fps > 0) 1000f / fps else 0f
                
                _uiState.value = _uiState.value.copy(
                    fps = fps,
                    frameTimeMs = frameTimeMs,
                    jankPercentage = calculateJankPercentage(frameTimeMs)
                )
            }
        }
    }
    
    fun runProbe() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRunningProbe = true,
                probeProgress = 0f,
                probeResult = null
            )
            
            val measurements = mutableListOf<Float>()
            val durationMs = 10000L
            val sampleIntervalMs = 100L
            val totalSamples = (durationMs / sampleIntervalMs).toInt()
            
            for (i in 0 until totalSamples) {
                delay(sampleIntervalMs)
                
                val fps = avatarProvider.getCurrentFps()
                measurements.add(fps)
                
                _uiState.value = _uiState.value.copy(
                    probeProgress = (i + 1).toFloat() / totalSamples
                )
            }
            
            val avgFps = measurements.average().toFloat()
            val minFps = measurements.minOrNull() ?: 0f
            
            val result = if (avgFps >= 60f && minFps >= 55f) {
                ProbeResult.Pass(avgFps)
            } else {
                val reason = when {
                    avgFps < 60f -> "Average FPS below 60"
                    minFps < 55f -> "Minimum FPS below 55 (experienced drops)"
                    else -> "Unknown issue"
                }
                ProbeResult.Fail(avgFps, reason)
            }
            
            _uiState.value = _uiState.value.copy(
                isRunningProbe = false,
                probeProgress = 1f,
                probeResult = result
            )
        }
    }
    
    private fun calculateJankPercentage(frameTimeMs: Float): Float {
        // Frame is janky if it takes > 16.67ms (60 FPS threshold)
        return if (frameTimeMs > 16.67f) {
            ((frameTimeMs - 16.67f) / frameTimeMs * 100f).coerceIn(0f, 100f)
        } else {
            0f
        }
    }
}

