package com.ilyk.cleaningplanner.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Request DTOs for task operations
 */

@Serializable
data class CompleteTaskRequest(
    val taskId: String
)

@Serializable
data class SkipTaskRequest(
    val taskId: String
)

@Serializable
data class AssignTaskRequest(
    val taskId: String,
    val memberId: String
)
