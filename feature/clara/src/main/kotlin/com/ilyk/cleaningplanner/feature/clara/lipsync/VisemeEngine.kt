package com.ilyk.cleaningplanner.feature.clara.lipsync

import com.ilyk.cleaningplanner.core.model.VisemeEvent
import com.ilyk.cleaningplanner.core.model.VisemeType
import com.ilyk.cleaningplanner.feature.clara.avatar.AvatarProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Drives avatar mouth shapes based on viseme events or amplitude fallback.
 */
@Singleton
class VisemeEngine @Inject constructor() {
    
    private var currentJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    
    /**
     * Play a sequence of viseme events on the avatar.
     */
    fun playVisemes(
        visemes: List<VisemeEvent>,
        avatarProvider: AvatarProvider,
        startTimeMs: Long = System.currentTimeMillis()
    ) {
        currentJob?.cancel()
        
        if (!avatarProvider.hasVisemeSupport()) {
            // Use amplitude fallback
            playAmplitudeFallback(visemes, avatarProvider, startTimeMs)
            return
        }
        
        currentJob = scope.launch {
            var previousVisemeWeight = 0f
            
            for (event in visemes) {
                val targetTime = startTimeMs + event.startTimeMs
                val now = System.currentTimeMillis()
                val delayMs = max(0, targetTime - now)
                
                delay(delayMs)
                
                if (!isActive) break
                
                // Smooth transition from previous viseme
                animateVisemeTransition(
                    fromWeight = previousVisemeWeight,
                    toViseme = event.visemeId,
                    toWeight = 1f,
                    durationMs = 50L,
                    avatarProvider = avatarProvider
                )
                
                // Hold the viseme for its duration
                delay(event.durationMs - 50)
                
                if (!isActive) break
                
                previousVisemeWeight = 1f
            }
            
            // Return to rest
            animateVisemeTransition(
                fromWeight = previousVisemeWeight,
                toViseme = VisemeType.REST.name,
                toWeight = 0f,
                durationMs = 100L,
                avatarProvider = avatarProvider
            )
        }
    }
    
    /**
     * Fallback to amplitude-based mouth movement when visemes aren't available.
     */
    private fun playAmplitudeFallback(
        visemes: List<VisemeEvent>,
        avatarProvider: AvatarProvider,
        startTimeMs: Long
    ) {
        currentJob = scope.launch {
            for (event in visemes) {
                val targetTime = startTimeMs + event.startTimeMs
                val now = System.currentTimeMillis()
                val delayMs = max(0, targetTime - now)
                
                delay(delayMs)
                
                if (!isActive) break
                
                // Map viseme types to amplitude levels
                val amplitude = when (event.visemeId) {
                    VisemeType.AI_EE.name -> 0.3f
                    VisemeType.EH.name -> 0.4f
                    VisemeType.OH_UW.name -> 0.8f
                    VisemeType.FV.name -> 0.2f
                    VisemeType.L.name -> 0.3f
                    VisemeType.MBP.name -> 0.1f
                    else -> 0f
                }
                
                avatarProvider.applyViseme("jaw_open", amplitude)
                delay(event.durationMs)
            }
            
            // Return to closed
            avatarProvider.applyViseme("jaw_open", 0f)
        }
    }
    
    private suspend fun animateVisemeTransition(
        fromWeight: Float,
        toViseme: String,
        toWeight: Float,
        durationMs: Long,
        avatarProvider: AvatarProvider
    ) {
        val steps = 5
        val stepDuration = durationMs / steps
        
        for (i in 0..steps) {
            if (!scope.isActive) break
            
            val progress = i.toFloat() / steps
            val weight = fromWeight + (toWeight - fromWeight) * progress
            
            avatarProvider.applyViseme(toViseme, weight)
            
            if (i < steps) {
                delay(stepDuration)
            }
        }
    }
    
    /**
     * Generate viseme events from phonemes (simple approximation).
     */
    fun phonemesToVisemes(text: String, durationMs: Long): List<VisemeEvent> {
        val visemes = mutableListOf<VisemeEvent>()
        val words = text.split(" ")
        val msPerWord = durationMs / max(1, words.size)
        
        words.forEachIndexed { index, word ->
            val startTime = index * msPerWord
            
            // Simple heuristic mapping
            val viseme = when {
                word.any { it in "aeiou" } -> {
                    when (word.first().lowercaseChar()) {
                        'a', 'e' -> VisemeType.AI_EE.name
                        'o', 'u' -> VisemeType.OH_UW.name
                        else -> VisemeType.EH.name
                    }
                }
                word.any { it.lowercaseChar() in "mbp" } -> VisemeType.MBP.name
                word.any { it.lowercaseChar() in "fv" } -> VisemeType.FV.name
                word.any { it.lowercaseChar() == 'l' } -> VisemeType.L.name
                else -> VisemeType.REST.name
            }
            
            visemes.add(
                VisemeEvent(
                    visemeId = viseme,
                    startTimeMs = startTime,
                    durationMs = msPerWord
                )
            )
        }
        
        return visemes
    }
    
    fun stop() {
        currentJob?.cancel()
        currentJob = null
    }
}

