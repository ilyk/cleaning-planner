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
    private var modelPath: String? = null
    private var hasVisemes: Boolean = false
    private var lastFrameTime: Long = 0
    private var frameCount: Int = 0
    private var currentFps: Float = 60f
    private var isAnimating: Boolean = false
    
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
            
            modelPath = glbPath
            
            withContext(Dispatchers.Main) {
                sceneView?.let { scene ->
                    try {
                        // Load model using SceneView's model loader
                        val asset = scene.modelLoader.loadModel(glbPath)
                        if (asset != null) {
                            // Create instance from asset
                            val instance = scene.modelLoader.createInstance(asset)
                            if (instance != null) {
                                val node = ModelNode(instance)
                                modelNode = node
                                scene.addChildNode<ModelNode>(node)
                                hasVisemes = false
                            }
                        }
                    } catch (e: Exception) {
                        // Model loading failed, but don't crash
                        e.printStackTrace()
                        return@withContext Result.failure(e)
                    }
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun playIdleAnimation() {
        isAnimating = true
        try {
            modelNode?.playAnimation(0)
        } catch (e: Exception) {
            // Animation not available
        }
    }
    
    override fun stopAnimations() {
        isAnimating = false
        try {
            modelNode?.stopAnimation(0)
        } catch (e: Exception) {
            // No animation to stop
        }
    }
    
    private fun startFpsTracking() {
        lastFrameTime = System.currentTimeMillis()
        sceneView?.let { view ->
            // Track FPS through frame updates
            var frameCounter = 0
            view.onFrame = { frameTimeNanos ->
                frameCounter++
                val currentTime = System.currentTimeMillis()
                val elapsed = currentTime - lastFrameTime
                
                if (elapsed >= 1000) {
                    currentFps = (frameCounter * 1000f) / elapsed
                    frameCounter = 0
                    lastFrameTime = currentTime
                }
            }
        }
    }
    
    override fun applyViseme(visemeId: String, weight: Float) {
        // Morph target application for lip-sync
        // Currently using amplitude fallback
        applyAmplitudeFallback(weight)
    }
    
    override fun applyVisemes(visemes: List<Pair<String, Float>>) {
        visemes.forEach { (visemeId, weight) ->
            applyViseme(visemeId, weight)
        }
    }
    
    override fun blink() {
        // Trigger random blink
        // Could be implemented with morph targets or animation
    }
    
    override fun hasVisemeSupport(): Boolean = hasVisemes
    
    override fun getCurrentFps(): Float = currentFps
    
    override fun release() {
        stopAnimations()
        modelNode = null
        sceneView = null
        modelPath = null
    }
    
    private fun applyAmplitudeFallback(amplitude: Float) {
        // Amplitude-based animation fallback
        // Actual implementation would manipulate morph targets
    }
}

