package com.ilyk.cleaningplanner.feature.clara.generation

import com.ilyk.cleaningplanner.core.model.Avatar3DAsset
import com.ilyk.cleaningplanner.core.model.AvatarGenerationRequest
import com.ilyk.cleaningplanner.core.model.AvatarGenResult
import kotlinx.coroutines.flow.Flow

/**
 * Provider-agnostic interface for 3D avatar generation.
 * Implementations support different backends (Meshy, Luma, etc.)
 */
interface AvatarGenProvider {
    /**
     * Generate a 3D avatar from a user concept and technical constraints.
     * Returns a Flow to track progress.
     */
    suspend fun generateAvatar(request: AvatarGenerationRequest): Flow<AvatarGenResult>
    
    /**
     * Validate that the provider is properly configured.
     */
    suspend fun validateConfiguration(): Result<String>
    
    /**
     * Get provider-specific capabilities (e.g., supports IPA, SSML, etc.)
     */
    fun getCapabilities(): ProviderCapabilities
    
    /**
     * Get license information for generated assets.
     */
    fun getLicenseInfo(): String
}

data class ProviderCapabilities(
    val supportsIPA: Boolean = false,
    val supportsSSML: Boolean = false,
    val supportsVisemes: Boolean = true,
    val maxTextureSize: Int = 2048,
    val supportedFormats: List<String> = listOf("glb"),
    val supportsAnimation: Boolean = true,
    val supportsMorphTargets: Boolean = true
)

