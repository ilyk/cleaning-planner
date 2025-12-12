package com.ilyk.cleaningplanner.data.database.dao

import androidx.room.*
import com.ilyk.cleaningplanner.data.database.entities.HistoryEntryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

@Dao
interface HistoryEntryDao {
    @Query("SELECT * FROM history_entries ORDER BY date DESC, createdAt DESC")
    fun getAllHistoryEntries(): Flow<List<HistoryEntryEntity>>

    @Query("SELECT * FROM history_entries WHERE taskId = :taskId ORDER BY date DESC")
    fun getHistoryForTask(taskId: String): Flow<List<HistoryEntryEntity>>

    @Query("SELECT * FROM history_entries WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getHistoryForDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<HistoryEntryEntity>>

    @Query("SELECT * FROM history_entries WHERE date = :date ORDER BY createdAt DESC")
    fun getHistoryForDate(date: LocalDate): Flow<List<HistoryEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryEntry(entry: HistoryEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryEntries(entries: List<HistoryEntryEntity>)

    @Delete
    suspend fun deleteHistoryEntry(entry: HistoryEntryEntity)

    @Query("DELETE FROM history_entries WHERE id = :entryId")
    suspend fun deleteHistoryEntryById(entryId: String)
}
