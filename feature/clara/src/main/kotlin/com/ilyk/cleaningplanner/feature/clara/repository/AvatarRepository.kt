package com.ilyk.cleaningplanner.feature.clara.repository

import android.content.Context
import com.ilyk.cleaningplanner.core.model.Avatar3DAsset
import com.ilyk.cleaningplanner.data.database.CleaningPlannerDatabase
import com.ilyk.cleaningplanner.data.database.entity.Avatar3DEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AvatarRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: CleaningPlannerDatabase,
    private val okHttpClient: OkHttpClient
) {
    
    private val avatarDao = database.avatar3DDao()
    
    val allAvatars: Flow<List<Avatar3DAsset>> = avatarDao.getAllActiveAvatars()
        .map { entities -> entities.map { it.toAsset() } }
    
    suspend fun getAvatarById(id: String): Avatar3DAsset? {
        return avatarDao.getAvatarById(id)?.toAsset()
    }
    
    fun getAvatarByIdFlow(id: String): Flow<Avatar3DAsset?> {
        return avatarDao.getAvatarByIdFlow(id).map { it?.toAsset() }
    }
    
    suspend fun insertAvatar(entity: Avatar3DEntity) {
        avatarDao.insertAvatar(entity)
    }
    
    /**
     * Import avatar from local file picker.
     */
    suspend fun importFromFile(
        filePath: String,
        displayName: String,
        licenseNote: String
    ): Result<Avatar3DAsset> = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(filePath)
            if (!sourceFile.exists() || !sourceFile.extension.equals("glb", ignoreCase = true)) {
                return@withContext Result.failure(Exception("Invalid GLB file"))
            }
            
            // Validate GLB structure (basic check)
            if (!isValidGLB(sourceFile)) {
                return@withContext Result.failure(Exception("Invalid or corrupted GLB file"))
            }
            
            // Calculate hash
            val hash = calculateFileHash(sourceFile)
            
            // Check if already imported
            val existing = avatarDao.getAvatarById(hash)
            if (existing != null) {
                return@withContext Result.success(existing.toAsset())
            }
            
            // Copy to app storage
            val destDir = File(context.filesDir, "avatars")
            destDir.mkdirs()
            val destFile = File(destDir, "${hash}.glb")
            sourceFile.copyTo(destFile, overwrite = true)
            
            // Create asset entity
            val entity = Avatar3DEntity(
                id = hash,
                displayName = displayName,
                defaultName = displayName,
                glbPath = destFile.absolutePath,
                thumbnailPath = null,
                sourceType = "imported_file",
                sourceLocation = sourceFile.name,
                licenseNote = licenseNote,
                contentHash = hash,
                fileSizeBytes = destFile.length(),
                triangleCount = estimateTriangleCount(destFile),
                hasVisemes = false,
                createdAt = System.currentTimeMillis()
            )
            
            avatarDao.insertAvatar(entity)
            
            Result.success(entity.toAsset())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Import avatar from URL with caching.
     */
    suspend fun importFromUrl(
        url: String,
        displayName: String,
        licenseNote: String
    ): Result<Avatar3DAsset> = withContext(Dispatchers.IO) {
        try {
            if (!url.startsWith("https://")) {
                return@withContext Result.failure(Exception("Only HTTPS URLs are supported"))
            }
            
            // Download file
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to download: ${response.code}"))
            }
            
            val tempFile = File.createTempFile("avatar_download", ".glb", context.cacheDir)
            response.body?.byteStream()?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            // Validate
            if (!isValidGLB(tempFile)) {
                tempFile.delete()
                return@withContext Result.failure(Exception("Downloaded file is not a valid GLB"))
            }
            
            // Calculate hash
            val hash = calculateFileHash(tempFile)
            
            // Check if already cached
            val existing = avatarDao.getAvatarById(hash)
            if (existing != null) {
                tempFile.delete()
                return@withContext Result.success(existing.toAsset())
            }
            
            // Move to permanent storage
            val destDir = File(context.filesDir, "avatars")
            destDir.mkdirs()
            val destFile = File(destDir, "${hash}.glb")
            tempFile.copyTo(destFile, overwrite = true)
            tempFile.delete()
            
            // Create entity
            val entity = Avatar3DEntity(
                id = hash,
                displayName = displayName,
                defaultName = displayName,
                glbPath = destFile.absolutePath,
                thumbnailPath = null,
                sourceType = "imported_url",
                sourceLocation = url,
                licenseNote = licenseNote,
                contentHash = hash,
                fileSizeBytes = destFile.length(),
                triangleCount = estimateTriangleCount(destFile),
                hasVisemes = false,
                createdAt = System.currentTimeMillis()
            )
            
            avatarDao.insertAvatar(entity)
            
            Result.success(entity.toAsset())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun deleteAvatar(id: String) {
        avatarDao.deleteAvatar(id)
        
        // Delete file
        val avatar = avatarDao.getAvatarById(id)
        avatar?.let {
            File(it.glbPath).delete()
            it.thumbnailPath?.let { thumb -> File(thumb).delete() }
        }
    }
    
    suspend fun getTotalStorageUsed(): Long {
        return avatarDao.getTotalStorageUsed() ?: 0L
    }
    
    private fun isValidGLB(file: File): Boolean {
        // GLB files start with magic bytes: "glTF" (0x46546C67)
        return try {
            FileInputStream(file).use { input ->
                val magic = ByteArray(4)
                input.read(magic)
                magic.contentEquals(byteArrayOf(0x67, 0x6C, 0x54, 0x46))
            }
        } catch (e: Exception) {
            false
        }
    }
    
    private fun calculateFileHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            var read = input.read(buffer)
            while (read > 0) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    
    private fun estimateTriangleCount(file: File): Int {
        // Simple heuristic: ~100 triangles per KB for typical models
        return ((file.length() / 1024) * 100).toInt()
    }
    
    private fun Avatar3DEntity.toAsset() = Avatar3DAsset(
        id = id,
        displayName = displayName,
        defaultName = defaultName,
        glbPath = glbPath,
        thumbnailPath = thumbnailPath,
        provider = sourceType,
        generationPrompt = sourceLocation ?: "",
        licenseInfo = licenseNote,
        contentHash = contentHash,
        fileSizeBytes = fileSizeBytes,
        triangleCount = triangleCount,
        createdAt = createdAt,
        version = version
    )
}
