package com.ilyk.cleaningplanner.platform.network

import com.ilyk.cleaningplanner.domain.model.Plan
import com.ilyk.cleaningplanner.data.database.dao.PendingOpsDao
import com.ilyk.cleaningplanner.data.remote.api.PlansApi
import com.ilyk.cleaningplanner.data.repository.PendingOp
import com.ilyk.cleaningplanner.data.remote.dto.AssignTaskRequest
import com.ilyk.cleaningplanner.data.remote.dto.CompleteTaskRequest
import com.ilyk.cleaningplanner.data.remote.dto.RevisePlanRequest
import com.ilyk.cleaningplanner.data.remote.dto.SkipTaskRequest
import kotlinx.datetime.Clock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Engine to reconcile pending offline operations with the backend
 */
@Singleton
class ReconcileEngine @Inject constructor(
    private val plansApi: PlansApi,
    private val pendingOpsDao: PendingOpsDao,
    private val clock: Clock // Injectable clock for testing
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun run(): ReconcileStats {
        var applied = 0
        var failed = 0
        val ops = pendingOpsDao.getAllPendingOps() // Get all pending operations in order

        for (op in ops) {
            try {
                when (PendingOp.OpType.valueOf(op.type)) {
                    PendingOp.OpType.MARK_DONE -> {
                        val request = json.decodeFromString<CompleteTaskRequest>(op.payload)
                        plansApi.completeTask(request)
                    }
                    PendingOp.OpType.MARK_SKIP -> {
                        val request = json.decodeFromString<SkipTaskRequest>(op.payload)
                        plansApi.skipTask(request)
                    }
                    PendingOp.OpType.REORDER -> {
                        val request = json.decodeFromString<RevisePlanRequest>(op.payload)
                        plansApi.revise(request)
                    }
                    PendingOp.OpType.ASSIGN -> {
                        val request = json.decodeFromString<AssignTaskRequest>(op.payload)
                        plansApi.assignTask(request)
                    }
                }
                pendingOpsDao.deletePendingOp(op.id) // Delete on successful application
                applied++
            } catch (e: Throwable) {
                failed++ // Keep for next pass if it fails
                println("Failed to reconcile operation ${op.id}: ${e.message}")
            }
        }
        return ReconcileStats(applied, failed, clock.now().toEpochMilliseconds())
    }
}

data class ReconcileStats(
    val appliedOperations: Int,
    val failedOperations: Int,
    val timestamp: Long
)

/**
 * Generates a quick fallback plan for offline use
 */
@Singleton
class QuickPlanGenerator @Inject constructor() {
    fun generateSingleRoomFallback(homeId: String, date: String, mode: com.ilyk.cleaningplanner.domain.model.CleaningMode): Plan {
        // This is a simplified mock. In a real app, this would generate a basic plan
        // based on cached home data, user preferences, and a simple heuristic.
        return Plan(
            id = "fallback-plan-${homeId}-${date}-${mode}",
            homeId = homeId,
            date = date,
            mode = mode,
            version = 0,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            tasks = listOf(
                com.ilyk.cleaningplanner.domain.model.Task(
                    id = "fallback-task-1",
                    title = "Quick wipe down",
                    description = "Wipe down main surfaces in the most used room.",
                    priority = com.ilyk.cleaningplanner.domain.model.TaskPriority.NOW,
                    estimatedDurationMinutes = 10,
                    createdAt = Clock.System.now(),
                    updatedAt = Clock.System.now()
                )
            ),
            metadata = com.ilyk.cleaningplanner.domain.model.PlanMetadata(
                totalEstimatedMinutes = 10,
                taskCount = 1
            )
        )
    }
}