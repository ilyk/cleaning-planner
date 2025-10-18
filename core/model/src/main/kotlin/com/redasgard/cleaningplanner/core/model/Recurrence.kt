package com.redasgard.cleaningplanner.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class Recurrence {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY
}

