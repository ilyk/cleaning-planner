package com.ilyk.cleaningplanner.data.network.api

import com.ilyk.cleaningplanner.domain.model.Plan
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * CleanFlow-specific API for home registration and initial plan generation.
 */
interface CleanFlowApi {

    @POST("/v1/cleanflow/home")
    suspend fun registerHome(@Body request: HomeRegistrationRequest): HomeRegistrationResponse

    @POST("/v1/cleanflow/home/initial-plan")
    suspend fun generateInitialPlan(@Body request: InitialPlanRequest): Plan

    @POST("/v1/cleanflow/plan/optimize")
    suspend fun optimizePlan(@Body request: OptimizePlanRequest): Plan

    @POST("/v1/cleanflow/history/batch")
    suspend fun syncHistoryBatch(@Body request: HistoryBatchRequest): HistoryBatchResponse

    @retrofit2.http.GET("/v1/cleanflow/plan/suggestions")
    suspend fun getSuggestions(
        @retrofit2.http.Query("home_id") homeId: String,
        @retrofit2.http.Query("plan_id") planId: String? = null
    ): SuggestionsResponse
}

@Serializable
data class HomeRegistrationRequest(
    @SerialName("home_id")
    val homeId: String,
    val profile: JsonObject,
)

@Serializable
data class HomeRegistrationResponse(
    @SerialName("home_id")
    val homeId: String,
)

@Serializable
data class InitialPlanRequest(
    @SerialName("home_id")
    val homeId: String,
    val date: String,
    val mode: String,
    val profile: JsonObject? = null,
)

@Serializable
data class OptimizePlanRequest(
    @SerialName("home_id")
    val homeId: String,
    @SerialName("plan_id")
    val planId: String,
    val mode: String? = null,
    val reasons: String? = null,
)

@Serializable
data class HistoryEntryPayload(
    @SerialName("task_id")
    val taskId: String,
    val date: String,
    val status: String,
    @SerialName("duration_min")
    val durationMin: Int? = null,
    val note: String? = null,
    val origin: String? = null,
    val source: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
)

@Serializable
data class HistoryBatchRequest(
    @SerialName("home_id")
    val homeId: String,
    @SerialName("device_id")
    val deviceId: String,
    val entries: List<HistoryEntryPayload>,
)

@Serializable
data class HistoryBatchResponse(
    val accepted: Int,
)

@Serializable
data class SuggestionsResponse(
    @SerialName("home_id")
    val homeId: String,
    @SerialName("plan_id")
    val planId: String? = null,
    val suggestions: List<SuggestionPayload>
)

@Serializable
data class SuggestionPayload(
    val id: String,
    val text: String,
    val confidence: Int,
    val action: String,
    val source: String,
    val target: JsonObject? = null,
    val state: SuggestionStatePayload
)

@Serializable
data class SuggestionStatePayload(
    val accepted: Boolean,
    val dismissed: Boolean,
    @SerialName("applied_locally")
    val appliedLocally: Boolean
)

