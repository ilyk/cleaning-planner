package com.ilyk.cleaningplanner.core.model

data class AvatarPrefs(
    val appearanceId: String = "clara",
    val voiceId: String = "warm",
    val showAvatar: Boolean = true,
    val muteVoice: Boolean = false,
    val alwaysShowSubtitles: Boolean = true
)

enum class AvatarAppearance(val id: String, val displayName: String, val category: AvatarCategory) {
    CLARA("clara", "Clara", AvatarCategory.GIRL),
    AYA("aya", "Aya", AvatarCategory.GIRL),
    LEO("leo", "Leo", AvatarCategory.BOY),
    MAX("max", "Max", AvatarCategory.BOY),
    SAM("sam", "Sam", AvatarCategory.NON_BINARY),
    REN("ren", "Ren", AvatarCategory.NON_BINARY);

    companion object {
        fun fromId(id: String): AvatarAppearance = entries.find { it.id == id } ?: CLARA
    }
}

enum class AvatarCategory(val displayName: String) {
    GIRL("Girls"),
    BOY("Boys"),
    NON_BINARY("Non-binary")
}

enum class VoiceStyle(val id: String, val displayName: String) {
    WARM("warm", "Warm"),
    BRIGHT("bright", "Bright"),
    CALM("calm", "Calm");

    companion object {
        fun fromId(id: String): VoiceStyle = entries.find { it.id == id } ?: WARM
    }
}

