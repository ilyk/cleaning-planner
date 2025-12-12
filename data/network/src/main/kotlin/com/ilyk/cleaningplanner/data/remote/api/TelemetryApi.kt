package com.ilyk.cleaningplanner.data.remote.api

import com.ilyk.cleaningplanner.domain.model.TelemetryEvent
import com.ilyk.cleaningplanner.domain.model.TelemetryResult
import retrofit2.http.*

/**
 * Telemetry API interface for CleanFlow backend
 * Handles analytics and user interaction tracking
 */
interface TelemetryApi {
    
    @POST("/v1/telemetry/complete")
    suspend fun complete(@Body event: TelemetryEvent): TelemetryResult
    
    @POST("/v1/telemetry/batch")
    suspend fun batchComplete(@Body events: List<TelemetryEvent>): TelemetryResult
}
