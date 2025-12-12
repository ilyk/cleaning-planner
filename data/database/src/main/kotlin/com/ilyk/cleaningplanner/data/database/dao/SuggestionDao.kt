package com.ilyk.cleaningplanner.data.database.dao

import androidx.room.*
import com.ilyk.cleaningplanner.data.database.entities.SuggestionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SuggestionDao {
    @Query("SELECT * FROM suggestions WHERE isDismissed = 0 ORDER BY confidence DESC, createdAt DESC")
    fun getActiveSuggestions(): Flow<List<SuggestionEntity>>

    @Query("SELECT * FROM suggestions WHERE isAccepted = 1 ORDER BY createdAt DESC")
    fun getAcceptedSuggestions(): Flow<List<SuggestionEntity>>

    @Query("SELECT * FROM suggestions WHERE isDismissed = 1 ORDER BY createdAt DESC")
    fun getDismissedSuggestions(): Flow<List<SuggestionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSuggestion(suggestion: SuggestionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSuggestions(suggestions: List<SuggestionEntity>)

    @Query("UPDATE suggestions SET isAccepted = 1 WHERE id = :suggestionId")
    suspend fun acceptSuggestion(suggestionId: String)

    @Query("UPDATE suggestions SET isDismissed = 1 WHERE id = :suggestionId")
    suspend fun dismissSuggestion(suggestionId: String)

    @Delete
    suspend fun deleteSuggestion(suggestion: SuggestionEntity)

    @Query("DELETE FROM suggestions WHERE id = :suggestionId")
    suspend fun deleteSuggestionById(suggestionId: String)
}
