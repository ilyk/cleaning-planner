package com.ilyk.cleaningplanner.feature.clara.avatar

import com.ilyk.cleaningplanner.core.model.Avatar3DAsset
import com.ilyk.cleaningplanner.core.model.VisemeEvent

/**
 * Provider abstraction for loading and animating 3D avatars.
 * Allows swapping between different rendering backends.
 */
interface AvatarProvider {
    /**
     * Load a 3D model from a local file path.
     */
    suspend fun loadModel(glbPath: String): Result<Unit>
    
    /**
     * Start playing the idle animation loop.
     */
    fun playIdleAnimation()
    
    /**
     * Stop all animations.
     */
    fun stopAnimations()
    
    /**
     * Apply a viseme (mouth shape) for lip-sync.
     * If the model doesn't have viseme morphs, this should fall back
     * to amplitude-based jaw/mouth movement.
     */
    fun applyViseme(visemeId: String, weight: Float)
    
    /**
     * Apply multiple visemes with weights (for blending).
     */
    fun applyVisemes(visemes: List<Pair<String, Float>>)
    
    /**
     * Trigger a blink animation.
     */
    fun blink()
    
    /**
     * Check if the loaded model has viseme morph targets.
     */
    fun hasVisemeSupport(): Boolean
    
    /**
     * Get current FPS.
     */
    fun getCurrentFps(): Float
    
    /**
     * Release resources.
     */
    fun release()
}

