package com.ilyk.cleaningplanner.data.repository

import com.ilyk.cleaningplanner.domain.model.CleaningMode
import com.ilyk.cleaningplanner.domain.model.Plan
import com.ilyk.cleaningplanner.domain.model.UserProfile
import com.ilyk.cleaningplanner.data.remote.api.PlansApi
import com.ilyk.cleaningplanner.data.network.api.CleanFlowApi
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import com.ilyk.cleaningplanner.data.database.dao.PlanDao
import com.ilyk.cleaningplanner.data.database.dao.HistoryEntryDao
import com.ilyk.cleaningplanner.data.database.entities.PlanEntity
import com.ilyk.cleaningplanner.data.database.entities.TaskEntity
import com.ilyk.cleaningplanner.data.database.entities.PendingOpEntity
import com.ilyk.cleaningplanner.data.database.entities.HistoryEntryEntity
import com.ilyk.cleaningplanner.data.network.api.HistoryEntryPayload
import com.ilyk.cleaningplanner.data.network.api.HistoryBatchRequest
import com.ilyk.cleaningplanner.domain.model.HistoryOrigin
import com.ilyk.cleaningplanner.data.remote.dto.CompleteTaskRequest
import com.ilyk.cleaningplanner.data.remote.dto.SkipTaskRequest
import com.ilyk.cleaningplanner.data.remote.dto.AssignTaskRequest
import com.ilyk.cleaningplanner.data.remote.dto.GeneratePlanRequest
import com.ilyk.cleaningplanner.domain.model.Task
import com.ilyk.cleaningplanner.domain.model.TaskPriority
import com.ilyk.cleaningplanner.domain.model.PlanMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.DateTimePeriod
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.util.UUID
import com.ilyk.cleaningplanner.core.model.TaskStatus
import javax.inject.Inject
import javax.inject.Singleton

typealias HomeId = String
typealias TaskId = String
typealias Mode = CleaningMode

enum class DoneAction { COMPLETE, SKIP }

@Singleton
class PlanRepository @Inject constructor(
    private val plansApi: PlansApi,
    private val planDao: PlanDao,
    private val historyEntryDao: HistoryEntryDao,
    private val cleanFlowApi: CleanFlowApi
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getToday(homeId: HomeId, date: String, mode: Mode): Plan {
        val entity = planDao.getByKey(homeId, date, mode.name)
        return entity?.toModel() ?: run {
            // If no cached plan, try to fetch from network
            try {
                val remotePlan = plansApi.generate(GeneratePlanRequest(homeId, date, mode))
                planDao.insert(PlanEntity.fromModel(remotePlan))
                remotePlan
            } catch (e: Exception) {
                // Handle network error, return a simple fallback plan
                createFallbackPlan(homeId, date, mode)
            }
        }
    }

    /**
     * Optionally call backend for an initial cloud-assisted plan after welcome.
     * If the network call fails, returns null and leaves local engines as fallback.
     */
    suspend fun syncInitialPlanWithBackend(
        homeId: HomeId,
        date: String,
        mode: Mode,
        profile: UserProfile
    ): Plan? {
        if (!profile.cloudOptIn) return null

        return try {
            val profileJson = buildJsonObject {
                put("name", profile.name)
                putJsonArray("rooms") {
                    profile.rooms.forEach { roomName ->
                        add(
                            buildJsonObject {
                                put("id", roomName)
                                put("name", roomName)
                                put("kind", null as String?)
                            }
                        )
                    }
                }
                put("floors", profile.floors)
                put("has_pets", profile.hasPets)
                putJsonArray("devices") {
                    profile.devices.forEach { device -> add(kotlinx.serialization.json.JsonPrimitive(device)) }
                }

                profile.quietHours?.let { quiet ->
                    putJsonObject("constraints") {
                        putJsonObject("quiet_hours") {
                            put("start", quiet.start)
                            put("end", quiet.end)
                        }
                    }
                }

                putJsonObject("preferences") {
                    put("mode", profile.preference.name.lowercase())
                    put("cloud_opt_in", profile.cloudOptIn)
                    put("auto_optimize", profile.autoOptimize)
                    profile.maxDailyMinutes?.let { put("max_daily_minutes", it) }
                    put("kid_friendly", profile.kidFriendly)
                }
            }

            // Register or update home profile first (best-effort)
            cleanFlowApi.registerHome(
                com.ilyk.cleaningplanner.data.network.api.HomeRegistrationRequest(
                    homeId = homeId,
                    profile = profileJson
                )
            )

            // Request initial plan and cache it locally
            val plan = cleanFlowApi.generateInitialPlan(
                com.ilyk.cleaningplanner.data.network.api.InitialPlanRequest(
                    homeId = homeId,
                    date = date,
                    mode = mode.name.lowercase(),
                    profile = profileJson
                )
            )

            planDao.insert(PlanEntity.fromModel(plan))
            plan
        } catch (e: Exception) {
            null
        }
    }

    private fun createFallbackPlan(homeId: String, date: String, mode: CleaningMode): Plan {
        return Plan(
            id = "fallback-plan-${homeId}-${date}-${mode}",
            homeId = homeId,
            date = date,
            mode = mode,
            version = 0,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            tasks = listOf(
                Task(
                    id = "fallback-task-1",
                    title = "Quick wipe down",
                    description = "Wipe down main surfaces in the most used room.",
                    priority = TaskPriority.NOW,
                    estimatedDurationMinutes = 10,
                    createdAt = Clock.System.now(),
                    updatedAt = Clock.System.now()
                )
            ),
            metadata = PlanMetadata(
                totalEstimatedMinutes = 10,
                taskCount = 1
            )
        )
    }

    /**
     * Trigger backend optimization for the current plan, fetch the optimized version,
     * and cache it locally in Room. Returns the optimized Plan on success.
     */
    suspend fun optimizePlanWithBackend(
        homeId: HomeId,
        date: String,
        mode: Mode,
        reasons: String? = null
    ): Result<Plan> {
        return try {
            val existing = planDao.getByKey(homeId, date, mode.name)
                ?: return Result.failure(IllegalStateException("No plan to optimize for $homeId on $date in mode ${mode.name}"))

            val optimizedPlan = cleanFlowApi.optimizePlan(
                com.ilyk.cleaningplanner.data.network.api.OptimizePlanRequest(
                    homeId = homeId,
                    planId = existing.id,
                    mode = mode.name.lowercase(),
                    reasons = reasons
                )
            )

            // Cache the optimized plan in Room
            planDao.insert(PlanEntity.fromModel(optimizedPlan))

            Result.success(optimizedPlan)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mark task as done with optimistic updates
     */
    suspend fun markTaskDone(taskId: String, deviceId: String? = null) {
        // Create history entry
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val historyEntry = HistoryEntryEntity(
            id = UUID.randomUUID().toString(),
            taskId = taskId,
            date = now.date,
            status = TaskStatus.Done,
            durationMin = null,
            note = null,
            origin = "app",
            deviceId = deviceId,
            source = null,
            createdAt = Clock.System.now().toEpochMilliseconds()
        )
        historyEntryDao.insertHistoryEntry(historyEntry)
        
        // Queue for server sync
        val pendingOp = PendingOp(
            type = PendingOp.OpType.MARK_DONE,
            payload = Json.encodeToString(CompleteTaskRequest.serializer(), CompleteTaskRequest(taskId))
        )
        planDao.insertPendingOp(pendingOp.toEntity())
    }

    /**
     * Mark task as skipped with optimistic updates
     */
    suspend fun markTaskSkipped(taskId: String, deviceId: String? = null) {
        // Create history entry
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val historyEntry = HistoryEntryEntity(
            id = UUID.randomUUID().toString(),
            taskId = taskId,
            date = now.date,
            status = TaskStatus.Skipped,
            durationMin = null,
            note = null,
            origin = "app",
            deviceId = deviceId,
            source = null,
            createdAt = Clock.System.now().toEpochMilliseconds()
        )
        historyEntryDao.insertHistoryEntry(historyEntry)
        
        // Queue for server sync
        val pendingOp = PendingOp(
            type = PendingOp.OpType.MARK_SKIP,
            payload = Json.encodeToString(SkipTaskRequest.serializer(), SkipTaskRequest(taskId))
        )
        planDao.insertPendingOp(pendingOp.toEntity())
    }

    /**
     * Assign task to family member with optimistic updates
     */
    suspend fun assignTask(taskId: String, memberId: String) {
        // Queue for server sync
        val pendingOp = PendingOp(
            type = PendingOp.OpType.ASSIGN,
            payload = Json.encodeToString(AssignTaskRequest.serializer(), AssignTaskRequest(taskId, memberId))
        )
        planDao.insertPendingOp(pendingOp.toEntity())
    }

    /**
     * Reconcile pending operations with server
     */
    suspend fun reconcile(): ReconcileStats {
        // Simplified reconciliation - just return empty stats for now
        return ReconcileStats(0, 0, Clock.System.now().toEpochMilliseconds())
    }

    /**
     * Sync unsynced history entries to the backend in batches.
     * For now, we sync all entries from the last 7 days. In a production system,
     * you'd track which entries have been synced (e.g., with a `synced` flag).
     */
    suspend fun syncHistoryBatch(
        homeId: HomeId,
        deviceId: String
    ): Result<Int> {
        return try {
            val now = Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
            val today = now.date
            // Calculate 7 days ago - use Instant arithmetic then convert to LocalDate
            val sevenDaysAgoMillis = Clock.System.now().toEpochMilliseconds() - (7L * 24 * 60 * 60 * 1000)
            val sevenDaysAgo = kotlinx.datetime.Instant.fromEpochMilliseconds(sevenDaysAgoMillis)
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date

            val entries = historyEntryDao.getHistoryForDateRange(sevenDaysAgo, today)
                .first() // Get the first emission from Flow

            if (entries.isEmpty()) {
                return Result.success(0)
            }

            val payloads = entries.map { entity ->
                HistoryEntryPayload(
                    taskId = entity.taskId,
                    date = entity.date.toString(),
                    status = entity.status.name.lowercase(),
                    durationMin = entity.durationMin,
                    note = entity.note,
                    origin = entity.origin,
                    source = entity.source,
                    createdAt = entity.createdAt?.let {
                        kotlinx.datetime.Instant.fromEpochMilliseconds(it)
                            .toString()
                    }
                )
            }

            val response = cleanFlowApi.syncHistoryBatch(
                HistoryBatchRequest(
                    homeId = homeId,
                    deviceId = deviceId,
                    entries = payloads
                )
            )

            Result.success(response.accepted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class PendingOp(
    val id: UUID = UUID.randomUUID(),
    val ts: Long = System.currentTimeMillis(),
    val type: OpType,
    val payload: String // JSON payload
) {
    enum class OpType {
        MARK_DONE, MARK_SKIP, REORDER, ASSIGN
    }

    fun toEntity(): PendingOpEntity {
        return PendingOpEntity(
            id = id.toString(),
            ts = ts,
            type = type.name,
            payload = payload
        )
    }
}

data class ReconcileStats(
    val appliedOperations: Int,
    val failedOperations: Int,
    val timestamp: Long
)