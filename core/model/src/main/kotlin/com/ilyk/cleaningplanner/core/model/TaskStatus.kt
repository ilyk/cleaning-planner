package com.ilyk.cleaningplanner.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class TaskStatus {
    Pending,
    Done,
    Skipped
}

