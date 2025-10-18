package com.ilyk.cleaningplanner.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "avatar_assets")
data class Avatar3DEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    
    @ColumnInfo(name = "display_name")
    val displayName: String,
    
    @ColumnInfo(name = "default_name")
    val defaultName: String,
    
    @ColumnInfo(name = "glb_path")
    val glbPath: String,
    
    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String?,
    
    @ColumnInfo(name = "source_type")
    val sourceType: String, // "bundled", "imported_file", "imported_url"
    
    @ColumnInfo(name = "source_location")
    val sourceLocation: String?,
    
    @ColumnInfo(name = "license_note")
    val licenseNote: String,
    
    @ColumnInfo(name = "content_hash")
    val contentHash: String,
    
    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long,
    
    @ColumnInfo(name = "triangle_count")
    val triangleCount: Int,
    
    @ColumnInfo(name = "has_visemes")
    val hasVisemes: Boolean,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    
    @ColumnInfo(name = "version")
    val version: Int = 1,
    
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true
)

