package com.ilyk.cleaningplanner.data.remote.api

import com.ilyk.cleaningplanner.data.remote.dto.CreateRoomRequestV1
import com.ilyk.cleaningplanner.data.remote.dto.OkResponse
import com.ilyk.cleaningplanner.data.remote.dto.UpdateRoomRequestV1
import com.ilyk.cleaningplanner.domain.model.Room
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * v1 Rooms API — matches backend handlers in `crates/api/src/rooms.rs`.
 * List is home-scoped via the `home_id` query parameter; the handler enforces that
 * the value matches the JWT's `home_id` claim and returns 403 on mismatch.
 */
interface RoomsApi {

    @GET("/v1/rooms")
    suspend fun list(@Query("home_id") homeId: String): List<Room>

    @GET("/v1/rooms/{room_id}")
    suspend fun get(@Path("room_id") roomId: String): Room

    @POST("/v1/rooms")
    suspend fun create(@Body request: CreateRoomRequestV1): Room

    @PUT("/v1/rooms/{room_id}")
    suspend fun update(
        @Path("room_id") roomId: String,
        @Body request: UpdateRoomRequestV1
    ): Room

    @DELETE("/v1/rooms/{room_id}")
    suspend fun delete(@Path("room_id") roomId: String): OkResponse
}
