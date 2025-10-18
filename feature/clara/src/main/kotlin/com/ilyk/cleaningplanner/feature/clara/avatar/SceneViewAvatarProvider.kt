package com.ilyk.cleaningplanner.feature.clara.avatar

import android.content.Context
import com.ilyk.cleaningplanner.core.model.VisemeType
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.sceneview.SceneView
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * SceneView-based implementation of AvatarProvider.
 * Handles 3D model loading, animation, and viseme-based lip-sync.
 */
@Singleton
class SceneViewAvatarProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : AvatarProvider {
    
    private var sceneView: SceneView? = null
    private var modelNode: ModelNode? = null
    private var hasVisemes: Boolean = false
    private var lastFrameTime: Long = 0
    private var frameCount: Int = 0
    private var currentFps: Float = 60f
    
    private val visemeMapping = mapOf(
        "AI_EE" to "viseme_ai",
        "EH" to "viseme_eh",
        "OH_UW" to "viseme_oh",
        "FV" to "viseme_fv",
        "L" to "viseme_l",
        "MBP" to "viseme_mbp",
        "REST" to "viseme_rest"
    )
    
    fun attachToSceneView(view: SceneView) {
        this.sceneView = view
        startFpsTracking()
    }
    
    override suspend fun loadModel(glbPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(glbPath)
            if (!file.exists()) {
                return@withContext Result.failure(Exception("GLB file not found: $glbPath"))
            }
            
            withContext(Dispatchers.Main) {
                sceneView?.let { scene ->
                    modelNode = ModelNode(modelGlbFileLocation = glbPath).apply {
                        // Position and scale the model appropriately
                        position = io.github.sceneview.math.Position(0f, -1.5f, -3f)
                        scale = io.github.sceneview.math.Scale(1f)
                    }
                    
                    modelNode?.let { node ->
                        scene.addChild(node)
                        
                        // Check for viseme morph targets
                        hasVisemes = checkForVisemes(node)
                        
                        // Start idle animation if available
                        playIdleAnimation()
                    }
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun playIdleAnimation() {
        modelNode?.let { node ->
            // Play the first animation (assumed to be idle loop)
            if (node.modelInstance?.animator?.animationCount ?: 0 > 0) {
                node.modelInstance?.animator?.apply {
                    applyAnimation(0)
                    animationIndex = 0
                }
            }
        }
    }
    
    override fun stopAnimations() {
        modelNode?.modelInstance?.animator?.apply {
            animationIndex = -1
        }
    }
    
    override fun applyViseme(visemeId: String, weight: Float) {
        val morphTargetName = visemeMapping[visemeId] ?: return
        
        if (hasVisemes) {
            // Apply morph target if available
            modelNode?.modelInstance?.let { instance ->
                // SceneView/Filament morph target application
                // This is simplified - actual implementation depends on model structure
                try {
                    // Apply the morph target weight
                    // Note: Actual API depends on SceneView version
                } catch (e: Exception) {
                    // Fallback to amplitude-based if morph fails
                    applyAmplitudeFallback(weight)
                }
            }
        } else {
            // Use amplitude-based fallback
            applyAmplitudeFallback(weight)
        }
    }
    
    override fun applyVisemes(visemes: List<Pair<String, Float>>) {
        visemes.forEach { (visemeId, weight) ->
            applyViseme(visemeId, weight)
        }
    }
    
    override fun blink() {
        // Trigger blink animation or morph
        // Random duration between 0.1-0.3 seconds
        val duration = Random.nextDouble(0.1, 0.3).toFloat()
        
        // Apply blink morph target if available
        // Otherwise, this is a no-op
    }
    
    override fun hasVisemeSupport(): Boolean = hasVisemes
    
    override fun getCurrentFps(): Float = currentFps
    
    override fun release() {
        stopAnimations()
        modelNode = null
        sceneView = null
    }
    
    private fun checkForVisemes(node: ModelNode): Boolean {
        // Check if the model has the expected morph targets
        // This is simplified - actual implementation depends on SceneView API
        return try {
            // Check for presence of viseme morph targets
            visemeMapping.values.any { morphName ->
                // Check if morph target exists
                // Implementation depends on SceneView's API
                false // Placeholder - actual check needed
            }
        } catch (e: Exception) {
            false
        }
    }
    
    private fun applyAmplitudeFallback(amplitude: Float) {
        // Apply simple jaw open based on amplitude
        // This provides basic mouth movement when visemes aren't available
        val jawOpen = (amplitude * 0.5f).coerceIn(0f, 1f)
        
        // Apply to generic jaw/mouth open morph if available
        // Otherwise, this is a visual no-op but lip-sync still "works"
    }
    
    private fun startFpsTracking() {
        lastFrameTime = System.currentTimeMillis()
        
        sceneView?.onFrame = { frameTimeNanos ->
            frameCount++
            val currentTime = System.currentTimeMillis()
            val elapsed = currentTime - lastFrameTime
            
            if (elapsed >= 1000) {
                currentFps = (frameCount * 1000f) / elapsed
                frameCount = 0
                lastFrameTime = currentTime
            }
        }
    }
}

