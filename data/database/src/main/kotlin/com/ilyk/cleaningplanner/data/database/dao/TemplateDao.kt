package com.ilyk.cleaningplanner.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ilyk.cleaningplanner.data.database.entities.TemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {
    
    @Query("SELECT * FROM templates WHERE roomId = :roomId")
    fun observeByRoom(roomId: String): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates WHERE id = :templateId")
    suspend fun getById(templateId: String): TemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: TemplateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(templates: List<TemplateEntity>)

    @Update
    suspend fun update(template: TemplateEntity)

    @Query("DELETE FROM templates WHERE id = :templateId")
    suspend fun delete(templateId: String)

    @Query("DELETE FROM templates WHERE roomId = :roomId")
    suspend fun deleteAllByRoom(roomId: String)
}

