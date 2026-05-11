package com.ilyk.cleaningplanner.data.repository

import com.ilyk.cleaningplanner.core.common.result.Result
import com.ilyk.cleaningplanner.data.remote.api.RoomsApi
import com.ilyk.cleaningplanner.data.remote.dto.CreateRoomRequestV1
import com.ilyk.cleaningplanner.data.remote.dto.UpdateRoomRequestV1
import com.ilyk.cleaningplanner.domain.model.Room
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rooms repository — talks to the v1 backend via [RoomsApi].
 *
 * Network-only at this stage. Offline-first caching (Room DAO write-through, observe flows)
 * is deferred to W6 (Planner) where a UI screen first consumes these reads. The current
 * `RoomEntity` schema is keyed on legacy `householdId` / `qrSlug` / `order` columns that
 * do not map to the backend's `home_id` / `kind` / `metadata` shape — caching honestly
 * requires a schema migration that belongs with the consuming screen's PR.
 */
@Singleton
class RoomRepository @Inject constructor(
    private val roomsApi: RoomsApi
) {

    suspend fun list(homeId: String): Result<List<Room>> =
        runCatching { roomsApi.list(homeId) }.toResult()

    suspend fun get(roomId: String): Result<Room> =
        runCatching { roomsApi.get(roomId) }.toResult()

    suspend fun create(request: CreateRoomRequestV1): Result<Room> =
        runCatching { roomsApi.create(request) }.toResult()

    suspend fun update(roomId: String, request: UpdateRoomRequestV1): Result<Room> =
        runCatching { roomsApi.update(roomId, request) }.toResult()

    suspend fun delete(roomId: String): Result<Unit> =
        runCatching {
            roomsApi.delete(roomId)
            Unit
        }.toResult()
}

private fun <T> kotlin.Result<T>.toResult(): Result<T> = fold(
    onSuccess = { Result.Success(it) },
    onFailure = { Result.Error(it) }
)
