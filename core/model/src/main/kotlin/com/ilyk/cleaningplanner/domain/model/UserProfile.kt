package com.ilyk.cleaningplanner.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val name: String,
    val rooms: List<String>,
    val floors: Int,
    val hasPets: Boolean,
    val devices: List<String>,
    val preference: Preference,
    val quietHours: QuietHours? = null,
    val allergies: List<String> = emptyList(),
    val dontTouchAreas: List<String> = emptyList(),
    val cloudOptIn: Boolean = false,
    val autoOptimize: Boolean = false,
    val maxDailyMinutes: Int? = null,
    val kidFriendly: Boolean = true
) {
    val isComplete: Boolean
        get() = name.isNotBlank() && rooms.isNotEmpty() && floors > 0
}

@Serializable
data class QuietHours(
    val start: String,
    val end: String
)

@Serializable
enum class Preference {
    Minimalist,
    FullControl
}
