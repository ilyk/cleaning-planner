package com.ilyk.cleaningplanner.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class TaskStatus {
    TODO,
    DOING,
    DONE,
    SKIPPED
}

