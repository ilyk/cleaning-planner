package com.ilyk.cleaningplanner.data.network.api

import com.ilyk.cleaningplanner.core.model.Task
import com.ilyk.cleaningplanner.core.model.TaskStatus
import kotlinx.datetime.Instant
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface TaskApi {
    
    @GET("tasks")
    suspend fun list(@Query("householdId") householdId: String): List<Task>

    @GET("tasks/{id}")
    suspend fun getById(@Path("id") id: String): Task

    @POST("tasks")
    suspend fun create(@Body request: CreateTaskRequest): Task

    @PATCH("tasks/{id}/status")
    suspend fun updateStatus(@Path("id") id: String, @Body request: UpdateStatusRequest): Task

    @PATCH("tasks/{id}/time")
    suspend fun logTime(@Path("id") id: String, @Body request: LogTimeRequest): Task

    @PATCH("tasks/{id}/notes")
    suspend fun addNote(@Path("id") id: String, @Body request: AddNoteRequest): Task

    @POST("tasks/{id}/chips")
    suspend fun applyChip(@Path("id") taskId: String, @Body request: ApplyChipRequest): Task
}

data class CreateTaskRequest(
    val householdId: String,
    val title: String,
    val roomId: String? = null,
    val templateId: String? = null,
    val assigneeId: String? = null,
    val dueDate: Instant? = null,
    val estMin: Int? = null
)

data class UpdateStatusRequest(val status: TaskStatus)
data class LogTimeRequest(val actualMin: Int)
data class AddNoteRequest(val notes: String)
data class ApplyChipRequest(val chipId: String)

