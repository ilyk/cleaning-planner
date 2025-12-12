package com.ilyk.cleaningplanner.data.remote.api

import com.ilyk.cleaningplanner.data.remote.dto.*
import com.ilyk.cleaningplanner.domain.model.Plan
import retrofit2.http.*

/**
 * Plans API interface for CleanFlow backend
 * Handles plan generation, revision, and retrieval
 */
interface PlansApi {
    
    @POST("/v1/plan/generate")
    suspend fun generate(@Body request: GeneratePlanRequest): Plan
    
    @POST("/v1/plan/revise")
    suspend fun revise(@Body request: RevisePlanRequest): Plan
    
    @GET("/v1/plan/{planId}")
    suspend fun get(@Path("planId") planId: String): Plan
    
    @POST("/v1/plan/printable")
    suspend fun printable(@Body request: PrintableRequest): PrintableResult
    
    @POST("/v1/plan/complete")
    suspend fun completeTask(@Body request: CompleteTaskRequest): Unit
    
    @POST("/v1/plan/skip")
    suspend fun skipTask(@Body request: SkipTaskRequest): Unit
    
    @POST("/v1/plan/assign")
    suspend fun assignTask(@Body request: AssignTaskRequest): Unit
}
