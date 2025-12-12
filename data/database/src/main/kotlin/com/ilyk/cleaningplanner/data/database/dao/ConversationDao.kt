package com.ilyk.cleaningplanner.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ilyk.cleaningplanner.data.database.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Insert
    suspend fun insertMessage(message: ConversationEntity)
    
    @Query("SELECT * FROM conversations WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getConversationHistory(sessionId: String): Flow<List<ConversationEntity>>
    
    @Query("SELECT * FROM conversations WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getConversationHistorySync(sessionId: String): List<ConversationEntity>
    
    @Query("DELETE FROM conversations WHERE sessionId = :sessionId")
    suspend fun clearSession(sessionId: String)
    
    @Query("DELETE FROM conversations WHERE timestamp < :cutoffTime")
    suspend fun deleteOldMessages(cutoffTime: Long)
}

