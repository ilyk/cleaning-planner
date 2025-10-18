package com.ilyk.cleaningplanner.feature.clara.initialization

import android.content.Context
import com.ilyk.cleaningplanner.data.database.entity.Avatar3DEntity
import com.ilyk.cleaningplanner.feature.clara.repository.AvatarRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Initializes bundled avatars on first run.
 */
@Singleton
class AvatarInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val avatarRepository: AvatarRepository
) {
    
    suspend fun initializeBundledAvatars() = withContext(Dispatchers.IO) {
        try {
            // Check if Clara already exists
            val existing = avatarRepository.getAvatarById("clara_default")
            if (existing != null) return@withContext
            
            // Copy bundled avatar to app storage
            val assetPath = "avatars/clara_default.glb"
            val destDir = File(context.filesDir, "avatars")
            destDir.mkdirs()
            val destFile = File(destDir, "clara_default.glb")
            
            context.assets.open(assetPath).use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            // Calculate hash
            val hash = calculateFileHash(destFile)
            
            // Read license info
            val licenseInfo = context.assets.open("avatars/LICENSE.txt").bufferedReader().use {
                it.readText()
            }
            
            // Create entity
            val entity = Avatar3DEntity(
                id = "clara_default",
                displayName = "Clara",
                defaultName = "Clara",
                glbPath = destFile.absolutePath,
                thumbnailPath = null,
                sourceType = "bundled",
                sourceLocation = assetPath,
                licenseNote = licenseInfo,
                contentHash = hash,
                fileSizeBytes = destFile.length(),
                triangleCount = 80000, // Estimated from spec
                hasVisemes = false, // Will be determined at runtime
                createdAt = System.currentTimeMillis(),
                version = 1,
                isActive = true
            )
            
            avatarRepository.insertAvatar(entity)
        } catch (e: Exception) {
            // Log but don't crash
            e.printStackTrace()
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
}

