package com.redasgard.cleaningplanner.data.repository

import com.redasgard.cleaningplanner.core.common.result.Result
import com.redasgard.cleaningplanner.core.model.Task
import com.redasgard.cleaningplanner.core.model.TaskStatus
import com.redasgard.cleaningplanner.data.database.dao.TaskDao
import com.redasgard.cleaningplanner.data.database.entities.toEntity
import com.redasgard.cleaningplanner.data.database.entities.toModel
import com.redasgard.cleaningplanner.data.network.api.CreateTaskRequest
import com.redasgard.cleaningplanner.data.network.api.TaskApi
import com.redasgard.cleaningplanner.data.network.api.UpdateStatusRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val taskApi: TaskApi
) {
    
    fun observeTodayTasks(householdId: String): Flow<List<Task>> {
        return taskDao.observeToday(householdId).map { entities ->
            entities.map { it.toModel() }
        }
    }

    fun observeByStatus(householdId: String, status: TaskStatus): Flow<List<Task>> {
        return taskDao.observeByStatus(householdId, status).map { entities ->
            entities.map { it.toModel() }
        }
    }

    fun observeByAssignee(memberId: String): Flow<List<Task>> {
        return taskDao.observeByAssignee(memberId).map { entities ->
            entities.map { it.toModel() }
        }
    }

    fun observeByRoom(roomId: String): Flow<List<Task>> {
        return taskDao.observeByRoom(roomId).map { entities ->
            entities.map { it.toModel() }
        }
    }

    suspend fun getById(taskId: String): Result<Task> {
        return try {
            val task = taskDao.getById(taskId)?.toModel()
            if (task != null) {
                Result.Success(task)
            } else {
                Result.Error(Exception("Task not found"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun createTask(request: CreateTaskRequest): Result<Task> {
        return try {
            val task = taskApi.create(request)
            taskDao.insert(task.toEntity())
            Result.Success(task)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun updateStatus(taskId: String, status: TaskStatus): Result<Unit> {
        return try {
            // Optimistic update
            taskDao.updateStatus(taskId, status)
            // Network call will be handled by sync worker
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun updateActualTime(taskId: String, minutes: Int): Result<Unit> {
        return try {
            taskDao.updateActualTime(taskId, minutes)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun updateAssignee(taskId: String, memberId: String?): Result<Unit> {
        return try {
            taskDao.updateAssignee(taskId, memberId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun syncFromNetwork(householdId: String): Result<Unit> {
        return try {
            val tasks = taskApi.list(householdId)
            taskDao.insertAll(tasks.map { it.toEntity() })
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun syncPendingChanges(): Result<Unit> {
        return try {
            val pendingTasks = taskDao.getPendingSync()
            pendingTasks.forEach { taskEntity ->
                try {
                    taskApi.updateStatus(
                        taskEntity.id,
                        UpdateStatusRequest(taskEntity.status)
                    )
                    taskDao.clearPendingSync(taskEntity.id)
                } catch (e: Exception) {
                    // Log but don't fail the entire sync
                }
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}

