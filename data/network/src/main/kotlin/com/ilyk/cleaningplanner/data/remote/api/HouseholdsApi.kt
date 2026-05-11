package com.ilyk.cleaningplanner.data.remote.api

import com.ilyk.cleaningplanner.data.remote.dto.CreateHomeRequest
import com.ilyk.cleaningplanner.data.remote.dto.OkResponse
import com.ilyk.cleaningplanner.data.remote.dto.UpdateHomeRequest
import com.ilyk.cleaningplanner.domain.model.Home
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * v1 Households API — matches backend handlers in `crates/api/src/households.rs`.
 * Endpoints scoped via the `home_id` claim in the JWT extension; mismatch returns 403,
 * unknown id returns 404. All routes live under the `/v1/households` prefix.
 */
interface HouseholdsApi {

    @GET("/v1/households")
    suspend fun list(): List<Home>

    @GET("/v1/households/{home_id}")
    suspend fun get(@Path("home_id") homeId: String): Home

    @POST("/v1/households")
    suspend fun create(@Body request: CreateHomeRequest): Home

    @PUT("/v1/households/{home_id}")
    suspend fun update(
        @Path("home_id") homeId: String,
        @Body request: UpdateHomeRequest
    ): Home

    @DELETE("/v1/households/{home_id}")
    suspend fun delete(@Path("home_id") homeId: String): OkResponse
}
