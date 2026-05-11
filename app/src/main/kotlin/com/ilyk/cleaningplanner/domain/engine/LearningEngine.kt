package com.ilyk.cleaningplanner.domain.engine

import com.ilyk.cleaningplanner.core.model.TaskStatus
import com.ilyk.cleaningplanner.domain.model.HistoryEntry
import com.ilyk.cleaningplanner.domain.model.Task

/**
 * In-memory per-task history aggregator. Pure Kotlin, no persistence.
 *
 * Persistence is the repository layer's job. This engine answers two questions:
 *  - "How often does this task actually get done?" (success rate)
 *  - "Given prior actuals, how long should we estimate this task next time?" (adjusted estimate)
 */
class LearningEngine {

    private val records: MutableMap<String, MutableList<HistoryEntry>> = mutableMapOf()

    fun onTaskRecorded(entry: HistoryEntry) {
        records.getOrPut(entry.taskId) { mutableListOf() }.add(entry)
    }

    /**
     * Fraction of recorded outcomes for [taskId] that were [TaskStatus.Done], counting only
     * entries whose status is Done or Skipped (Pending is excluded — it's in-progress, not an
     * outcome). Returns 0f when there is no history.
     */
    fun getSuccessRate(taskId: String): Float {
        val entries = records[taskId] ?: return 0f
        val outcomes = entries.count { it.status == TaskStatus.Done || it.status == TaskStatus.Skipped }
        if (outcomes == 0) return 0f
        val done = entries.count { it.status == TaskStatus.Done }
        return done.toFloat() / outcomes.toFloat()
    }

    /**
     * Blends [Task.estimatedDurationMinutes] with the average recorded duration of Done entries,
     * favoring the midpoint to avoid over-correcting on a single sample. Always stays strictly
     * between the recorded average and the original estimate when both differ, leaving room for
     * a small buffer above the recorded actual.
     */
    fun adjustedEstimate(task: Task): Int {
        val original = task.estimatedDurationMinutes
        val actuals = records[task.id]
            ?.filter { it.status == TaskStatus.Done && it.durationMin != null }
            ?.mapNotNull { it.durationMin }
            ?: return original
        if (actuals.isEmpty()) return original

        val avgActual = actuals.average()
        val midpoint = ((original + avgActual) / 2.0).toInt()

        return when {
            avgActual >= original -> original // no shrinkage when actuals run long
            else -> midpoint
                .coerceAtLeast(avgActual.toInt() + 1) // keep a buffer above the actual
                .coerceAtMost(original - 1)           // and stay strictly below the original
        }
    }
}
