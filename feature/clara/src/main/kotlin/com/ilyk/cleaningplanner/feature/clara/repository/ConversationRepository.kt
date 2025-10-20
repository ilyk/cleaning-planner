package com.ilyk.cleaningplanner.feature.clara.repository

import com.ilyk.cleaningplanner.core.model.OpenAIMessage
import com.ilyk.cleaningplanner.data.database.CleaningPlannerDatabase
import com.ilyk.cleaningplanner.data.database.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing conversation history
 */
@Singleton
class ConversationRepository @Inject constructor(
    private val database: CleaningPlannerDatabase
) {
    private val conversationDao = database.conversationDao()
    
    // Current session ID (persists across app lifecycle)
    private var currentSessionId: String = UUID.randomUUID().toString()
    
    /**
     * Get conversation history as OpenAI messages
     */
    fun getConversationHistory(): Flow<List<OpenAIMessage>> {
        return conversationDao.getConversationHistory(currentSessionId)
            .map { entities ->
                entities.map { entity ->
                    OpenAIMessage(
                        role = entity.role,
                        content = entity.content
                    )
                }
            }
    }
    
    /**
     * Get conversation history synchronously for API calls
     */
    suspend fun getConversationHistorySync(): List<OpenAIMessage> {
        return conversationDao.getConversationHistorySync(currentSessionId)
            .map { entity ->
                OpenAIMessage(
                    role = entity.role,
                    content = entity.content
                )
            }
    }
    
    /**
     * Add user message to history
     */
    suspend fun addUserMessage(content: String) {
        conversationDao.insertMessage(
            ConversationEntity(
                role = "user",
                content = content,
                sessionId = currentSessionId
            )
        )
    }
    
    /**
     * Add assistant (Clara) message to history
     */
    suspend fun addAssistantMessage(content: String) {
        conversationDao.insertMessage(
            ConversationEntity(
                role = "assistant",
                content = content,
                sessionId = currentSessionId
            )
        )
    }
    
    /**
     * Start a new conversation session
     */
    fun startNewSession() {
        currentSessionId = UUID.randomUUID().toString()
    }
    
    /**
     * Clear current session history
     */
    suspend fun clearCurrentSession() {
        conversationDao.clearSession(currentSessionId)
    }
    
    /**
     * Clean up old conversations (older than 7 days)
     */
    suspend fun cleanupOldConversations() {
        val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        conversationDao.deleteOldMessages(sevenDaysAgo)
    }
}

