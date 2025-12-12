package com.ilyk.cleaningplanner.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Avatar3DAsset(
    val id: String,
    val displayName: String,
    val defaultName: String,
    val glbPath: String,
    val thumbnailPath: String?,
    val provider: String,
    val generationPrompt: String,
    val licenseInfo: String,
    val contentHash: String,
    val fileSizeBytes: Long,
    val triangleCount: Int,
    val createdAt: Long,
    val version: Int = 1
)

@Serializable
data class AvatarGenerationRequest(
    val concept: String,
    val gender: AvatarGender,
    val ageVibe: AvatarAgeVibe,
    val style: String,
    val warmth: String,
    val clothing: String? = null,
    val technicalConstraints: TechnicalConstraints = TechnicalConstraints()
)

@Serializable
data class TechnicalConstraints(
    val format: String = "glTF 2.0 (.glb)",
    val maxTriangles: Int = 80000,
    val meshType: String = "single skinned mesh",
    val rig: String = "skeletal rig + morph targets for visemes",
    val textures: String = "4-6 PBR textures (albedo/rough/metal/normal/AO), 1k-2k resolution",
    val compression: String = "KTX2 if supported, otherwise PNG",
    val animations: List<String> = listOf("idle (breathing + micro-motion)", "blink"),
    val lighting: String = "neutral base lighting, no baked shadows"
)

enum class AvatarGender {
    FEMALE, MALE, NON_BINARY
}

enum class AvatarAgeVibe {
    YOUNG_ADULT, ADULT, MATURE
}

@Serializable
data class PronunciationSettings(
    val mode: PronunciationMode,
    val value: String
)

enum class PronunciationMode {
    NONE, PHONETIC, IPA, SSML
}

@Serializable
data class Avatar3DPrefs(
    val appearanceId: String = "clara",
    val displayName: String = "Clara",
    val pronunciationMode: PronunciationMode = PronunciationMode.NONE,
    val pronunciationValue: String = "",
    val voiceId: String = "warm",
    val showAvatar: Boolean = true,
    val muteVoice: Boolean = false,
    val alwaysShowSubtitles: Boolean = true
)

@Serializable
data class VisemeEvent(
    val visemeId: String,
    val startTimeMs: Long,
    val durationMs: Long
)

enum class VisemeType {
    AI_EE,    // AI, EE
    EH,       // EH
    OH_UW,    // OH, UW
    FV,       // F, V
    L,        // L
    MBP,      // M, B, P
    REST      // Neutral/closed
}

@Serializable
data class PerformanceMetrics(
    val avgFps: Float,
    val minFps: Float,
    val maxFps: Float,
    val jankPercentage: Float,
    val frameTimeMs: Float,
    val textureMemoryMb: Float,
    val triangleCount: Int,
    val timestamp: Long
)

