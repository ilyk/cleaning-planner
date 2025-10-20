package com.ilyk.cleaningplanner.feature.clara.avatar

import android.content.Context
import android.util.Log
import com.ilyk.cleaningplanner.core.model.VisemeType
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Quaternion
import io.github.sceneview.SceneView
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sin
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
    private var breathingStartTime: Long = 0
    
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
        Log.d(TAG, "Attaching to SceneView")
        this.sceneView = view
        
        // Configure camera position for avatar viewing
        view.cameraNode.position = Float3(0f, 1.5f, 3.5f)
        view.cameraNode.lookAt(Float3(0f, 1f, 0f))
        
        startFpsTracking()
    }
    
    companion object {
        private const val TAG = "SceneViewAvatarProvider"
    }
    
    override suspend fun loadModel(glbPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "loadModel: $glbPath")
            val file = File(glbPath)
            if (!file.exists()) {
                Log.e(TAG, "GLB file not found: $glbPath")
                return@withContext Result.failure(Exception("GLB file not found: $glbPath"))
            }
            
            Log.d(TAG, "GLB file exists, size=${file.length()} bytes")
            modelPath = glbPath
            
            withContext(Dispatchers.Main) {
                sceneView?.let { scene ->
                    try {
                        Log.d(TAG, "Loading model with SceneView")
                        
                        // SceneView expects asset paths relative to assets folder
                        // If glbPath is a full file path, extract just the asset path
                        val assetPath = if (glbPath.contains("/files/avatars/")) {
                            // It's in internal storage, use the asset path instead
                            "avatars/clara_default.glb"
                        } else if (glbPath.startsWith("/")) {
                            // Full path - try to extract relative asset path
                            glbPath.substringAfter("assets/", "avatars/clara_default.glb")
                        } else {
                            // Already an asset path
                            glbPath
                        }
                        
                        Log.d(TAG, "Using asset path: $assetPath")
                        
                        // Load model using asset path
                        val asset = scene.modelLoader.loadModel(assetPath)
                        if (asset != null) {
                            Log.d(TAG, "Asset loaded, creating instance")
                            // Create instance from asset
                            val instance = scene.modelLoader.createInstance(asset)
                            if (instance != null) {
                                Log.d(TAG, "Instance created, adding to scene")
                                val node = ModelNode(instance)
                                modelNode = node
                                scene.addChildNode(node)
                                hasVisemes = false
                                Log.d(TAG, "Model successfully loaded and added to scene")
                            } else {
                                Log.e(TAG, "Failed to create instance from asset")
                            }
                        } else {
                            Log.e(TAG, "Asset is null after loading")
                        }
                    } catch (e: Exception) {
                        // Model loading failed, but don't crash
                        Log.e(TAG, "Exception loading model", e)
                        e.printStackTrace()
                    }
                } ?: Log.e(TAG, "SceneView is null")
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Exception in loadModel", e)
            Result.failure(e)
        }
    }
    
    override fun playIdleAnimation() {
        Log.d(TAG, "playIdleAnimation() called")
        isAnimating = true
        breathingStartTime = System.currentTimeMillis()
        
        modelNode?.let { node ->
            try {
                // Try to get animation count
                val animCount = node.animationCount
                Log.d(TAG, "Model has $animCount animations")
                
                if (animCount > 0) {
                    // Play first animation on loop
                    node.playAnimation(
                        animationIndex = 0,
                        loop = true
                    )
                    Log.d(TAG, "Started looping animation 0")
                } else {
                    Log.d(TAG, "No animations in model, using procedural breathing")
                    startProceduralBreathing()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to play animation", e)
                startProceduralBreathing()
            }
        } ?: Log.w(TAG, "modelNode is null, cannot play animation")
    }
    
    private fun startProceduralBreathing() {
        sceneView?.let { view ->
            view.onFrame = { frameTime: Long ->
                if (isAnimating && modelNode != null) {
                    // Create subtle head movement like thinking/talking
                    val elapsed = (System.currentTimeMillis() - breathingStartTime) / 1000f
                    
                    // Gentle head tilt side to side (3 second cycle)
                    val tiltCycle = 3f
                    val tiltPhase = (elapsed % tiltCycle) / tiltCycle * 2f * Math.PI.toFloat()
                    val yRotation = sin(tiltPhase.toDouble()).toFloat() * 0.03f // Small tilt ±1.7 degrees
                    
                    // Slight up/down nod (4 second cycle, slower)
                    val nodCycle = 4f
                    val nodPhase = (elapsed % nodCycle) / nodCycle * 2f * Math.PI.toFloat()
                    val xRotation = sin(nodPhase.toDouble()).toFloat() * 0.02f // Small nod ±1.1 degrees
                    
                    // Apply Euler angles rotation (pitch, yaw, roll)
                    modelNode?.rotation = Float3(xRotation, yRotation, 0f)
                }
            }
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
            view.onFrame = { _: Long ->
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

