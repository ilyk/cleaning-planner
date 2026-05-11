package com.ilyk.cleaningplanner.domain.model

import com.ilyk.cleaningplanner.core.model.TaskStatus

/**
 * Computed status property for Task based on completion timestamps
 */
val Task.status: TaskStatus
    get() = when {
        completedAt != null -> TaskStatus.Done
        skippedAt != null -> TaskStatus.Skipped
        else -> TaskStatus.Pending
    }
