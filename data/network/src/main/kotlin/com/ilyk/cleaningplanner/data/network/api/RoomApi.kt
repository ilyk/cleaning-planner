package com.ilyk.cleaningplanner.data.network.api

import com.ilyk.cleaningplanner.core.model.RoomX
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface RoomApi {
    
    @GET("rooms")
    suspend fun list(@Query("householdId") householdId: String): List<RoomX>

    @GET("rooms/{id}")
    suspend fun getById(@Path("id") id: String): RoomX

    @GET("rooms/qr/{slug}")
    suspend fun getByQrSlug(@Path("slug") qrSlug: String): RoomX

    @POST("rooms")
    suspend fun create(@Body request: CreateRoomRequest): RoomX

    @PUT("rooms/{id}")
    suspend fun update(@Path("id") id: String, @Body request: UpdateRoomRequest): RoomX

    @DELETE("rooms/{id}")
    suspend fun delete(@Path("id") id: String)
}

data class CreateRoomRequest(
    val householdId: String,
    val name: String,
    val order: Int = 0
)

data class UpdateRoomRequest(
    val name: String?,
    val order: Int?
)

