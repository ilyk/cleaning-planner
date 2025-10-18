package com.ilyk.cleaningplanner.data.repository

import com.ilyk.cleaningplanner.core.common.result.Result
import com.ilyk.cleaningplanner.core.model.RoomX
import com.ilyk.cleaningplanner.data.database.dao.RoomDao
import com.ilyk.cleaningplanner.data.database.entities.toEntity
import com.ilyk.cleaningplanner.data.database.entities.toModel
import com.ilyk.cleaningplanner.data.network.api.CreateRoomRequest
import com.ilyk.cleaningplanner.data.network.api.RoomApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomRepository @Inject constructor(
    private val roomDao: RoomDao,
    private val roomApi: RoomApi
) {
    
    fun observeRooms(householdId: String): Flow<List<RoomX>> {
        return roomDao.observeAll(householdId).map { entities ->
            entities.map { it.toModel() }
        }
    }

    suspend fun getByQrSlug(qrSlug: String): Result<RoomX> {
        return try {
            // Try local first
            val localRoom = roomDao.getByQrSlug(qrSlug)
            if (localRoom != null) {
                return Result.Success(localRoom.toModel())
            }

            // Fetch from network
            val room = roomApi.getByQrSlug(qrSlug)
            roomDao.insert(room.toEntity())
            Result.Success(room)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun createRoom(request: CreateRoomRequest): Result<RoomX> {
        return try {
            val room = roomApi.create(request)
            roomDao.insert(room.toEntity())
            Result.Success(room)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun syncFromNetwork(householdId: String): Result<Unit> {
        return try {
            val rooms = roomApi.list(householdId)
            roomDao.insertAll(rooms.map { it.toEntity() })
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}

