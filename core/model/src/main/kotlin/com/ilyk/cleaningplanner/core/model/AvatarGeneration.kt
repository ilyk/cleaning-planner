package com.ilyk.cleaningplanner.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MeshyGenerationRequest(
    @SerialName("mode")
    val mode: String = "preview",
    @SerialName("prompt")
    val prompt: String,
    @SerialName("art_style")
    val artStyle: String = "realistic",
    @SerialName("negative_prompt")
    val negativePrompt: String? = null,
    @SerialName("topology")
    val topology: String = "quad",
    @SerialName("target_polycount")
    val targetPolycount: Int = 80000
)

@Serializable
data class MeshyGenerationResponse(
    @SerialName("result")
    val result: String,
    @SerialName("id")
    val id: String,
    @SerialName("status")
    val status: String,
    @SerialName("created_at")
    val createdAt: Long? = null
)

@Serializable
data class MeshyTaskStatusResponse(
    @SerialName("id")
    val id: String,
    @SerialName("status")
    val status: String,
    @SerialName("progress")
    val progress: Int,
    @SerialName("model_urls")
    val modelUrls: MeshyModelUrls? = null,
    @SerialName("thumbnail_url")
    val thumbnailUrl: String? = null
)

@Serializable
data class MeshyModelUrls(
    @SerialName("glb")
    val glb: String? = null,
    @SerialName("fbx")
    val fbx: String? = null,
    @SerialName("usdz")
    val usdz: String? = null
)

sealed class AvatarGenResult {
    data class Success(val asset: Avatar3DAsset) : AvatarGenResult()
    data class Error(val message: String, val isRetryable: Boolean = false) : AvatarGenResult()
    data class Progress(val percentage: Int, val message: String) : AvatarGenResult()
}

@Serializable
data class AvatarProviderConfig(
    val provider: String,
    val apiKey: String,
    val baseUrl: String
)

