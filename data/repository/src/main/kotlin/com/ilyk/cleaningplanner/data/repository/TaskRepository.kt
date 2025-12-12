package com.ilyk.cleaningplanner.data.repository

import com.ilyk.cleaningplanner.domain.model.Task
import com.ilyk.cleaningplanner.data.database.dao.TaskDao
import com.ilyk.cleaningplanner.data.database.entities.TaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing task data in CleanFlow
 * Simplified version for the new architecture
 */
@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao
) {
    
    /**
     * Get tasks by plan ID
     */
    fun getTasksByPlanId(planId: String): Flow<List<Task>> {
        return taskDao.getTasksByPlanId(planId).map { entities ->
            entities.map { it.toModel() }
        }
    }
    
    /**
     * Get task by ID
     */
    suspend fun getTaskById(taskId: String): Task? {
        return taskDao.getTaskById(taskId)?.toModel()
    }
    
    /**
     * Get tasks by assignee
     */
    fun getTasksByAssignee(memberId: String): Flow<List<Task>> {
        return taskDao.getTasksByAssignee(memberId).map { entities ->
            entities.map { it.toModel() }
        }
    }
    
    /**
     * Insert a task
     */
    suspend fun insertTask(task: Task, planId: String) {
        taskDao.insertTask(TaskEntity.fromModel(task, planId))
    }
    
    /**
     * Insert multiple tasks
     */
    suspend fun insertTasks(tasks: List<Task>, planId: String) {
        taskDao.insertTasks(tasks.map { TaskEntity.fromModel(it, planId) })
    }
    
    /**
     * Update a task
     */
    suspend fun updateTask(task: Task, planId: String) {
        taskDao.updateTask(TaskEntity.fromModel(task, planId))
    }
    
    /**
     * Mark task as completed
     */
    suspend fun markCompleted(taskId: String) {
        taskDao.markCompleted(taskId, kotlinx.datetime.Clock.System.now().toEpochMilliseconds())
    }
    
    /**
     * Mark task as skipped
     */
    suspend fun markSkipped(taskId: String) {
        taskDao.markSkipped(taskId, kotlinx.datetime.Clock.System.now().toEpochMilliseconds())
    }
    
    /**
     * Assign task to member
     */
    suspend fun assignTask(taskId: String, memberId: String?) {
        taskDao.assignTask(taskId, memberId)
    }
    
    /**
     * Delete a task
     */
    suspend fun deleteTask(taskId: String) {
        taskDao.deleteTask(taskId)
    }
    
    /**
     * Delete tasks by plan ID
     */
    suspend fun deleteTasksByPlanId(planId: String) {
        taskDao.deleteTasksByPlanId(planId)
    }
}