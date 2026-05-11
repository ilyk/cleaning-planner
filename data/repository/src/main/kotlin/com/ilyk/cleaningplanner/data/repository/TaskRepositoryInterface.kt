package com.ilyk.cleaningplanner.data.repository

import com.ilyk.cleaningplanner.core.common.result.Result
import com.ilyk.cleaningplanner.core.model.Task
import com.ilyk.cleaningplanner.core.model.TaskStatus
import com.ilyk.cleaningplanner.data.network.api.CreateTaskRequest
import kotlinx.coroutines.flow.Flow

interface TaskRepositoryInterface {
    fun observeTodayTasks(householdId: String): Flow<List<Task>>
    fun observeByStatus(householdId: String, status: TaskStatus): Flow<List<Task>>
    fun observeByAssignee(memberId: String): Flow<List<Task>>
    fun observeByRoom(roomId: String): Flow<List<Task>>
    suspend fun getById(taskId: String): Result<Task>
    suspend fun createTask(request: CreateTaskRequest): Result<Task>
    suspend fun insertTasks(tasks: List<com.ilyk.cleaningplanner.domain.model.Task>, householdId: String = "default"): Result<Unit>
    suspend fun updateStatus(taskId: String, status: TaskStatus): Result<Unit>
    suspend fun updateActualTime(taskId: String, minutes: Int): Result<Unit>
    suspend fun updateAssignee(taskId: String, memberId: String?): Result<Unit>
    suspend fun syncFromNetwork(householdId: String): Result<Unit>
    suspend fun syncPendingChanges(): Result<Unit>
    
    // Legacy methods from stub (temporary - will be removed)
    suspend fun insertTask(task: com.ilyk.cleaningplanner.domain.model.Task) = insertTasks(listOf(task))
    suspend fun updateTaskStatus(taskId: String, status: TaskStatus) = updateStatus(taskId, status)
    fun getAllHistoryEntries(): Flow<List<com.ilyk.cleaningplanner.domain.model.HistoryEntry>> = kotlinx.coroutines.flow.flowOf(emptyList())
    fun getTasksForDate(date: kotlinx.datetime.LocalDate): Flow<List<com.ilyk.cleaningplanner.domain.model.Task>>
    suspend fun insertHistoryEntry(entry: com.ilyk.cleaningplanner.domain.model.HistoryEntry) {}
}

