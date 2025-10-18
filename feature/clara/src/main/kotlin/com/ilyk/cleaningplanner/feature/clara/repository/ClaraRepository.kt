package com.ilyk.cleaningplanner.feature.clara.repository

import com.ilyk.cleaningplanner.core.model.AvatarPrefs
import com.ilyk.cleaningplanner.core.model.OpenAIConfig
import com.ilyk.cleaningplanner.core.model.OpenAIMessage
import com.ilyk.cleaningplanner.core.model.OpenAIRequest
import com.ilyk.cleaningplanner.data.network.api.OpenAIApi
import com.ilyk.cleaningplanner.feature.clara.data.AvatarPrefsDataStore
import com.ilyk.cleaningplanner.feature.clara.data.OpenAIConfigDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

sealed class ClaraResult {
    data class Success(val message: String) : ClaraResult()
    data class Error(val message: String, val fallback: String) : ClaraResult()
}

@Singleton
class ClaraRepository @Inject constructor(
    private val avatarPrefsDataStore: AvatarPrefsDataStore,
    private val openAIConfigDataStore: OpenAIConfigDataStore,
    private val openAIApi: OpenAIApi
) {
    companion object {
        private const val SYSTEM_PROMPT = "You are Clara, a warm, concise, emotionally intelligent household planning assistant. You speak naturally, avoid robotic instructions, and never mention internal processes. Keep replies short (1–2 sentences), positive, and helpful. Avoid emojis unless user uses them first."
        
        private val FALLBACK_RESPONSES = mapOf(
            "lets_chat" to "Great — tell me a little about your home whenever you're ready.",
            "type_info" to "Perfect, type away in your own words and I'll take it from there.",
            "general" to "I'm here to help you plan your cleaning routine. What would you like to talk about?"
        )
    }

    val avatarPrefs: Flow<AvatarPrefs> = avatarPrefsDataStore.avatarPrefs
    val openAIConfig: Flow<OpenAIConfig> = openAIConfigDataStore.openAIConfig

    suspend fun updateAvatarPrefs(prefs: AvatarPrefs) {
        avatarPrefsDataStore.updateAvatarPrefs(prefs)
    }

    suspend fun updateOpenAIConfig(config: OpenAIConfig) {
        openAIConfigDataStore.updateConfig(config)
    }

    suspend fun validateOpenAIConfig(): Result<String> {
        return try {
            val config = openAIConfig.first()
            if (config.apiKey.isBlank()) {
                return Result.failure(Exception("API key is required"))
            }

            val request = OpenAIRequest(
                model = config.model,
                messages = listOf(
                    OpenAIMessage(role = "system", content = SYSTEM_PROMPT),
                    OpenAIMessage(role = "user", content = "Hi")
                ),
                maxTokens = 20
            )

            val response = openAIApi.createChatCompletion(
                authorization = "Bearer ${config.apiKey}",
                request = request
            )

            Result.success("Configuration validated successfully")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getClaraResponse(context: String, userMessage: String = ""): ClaraResult {
        return try {
            val config = openAIConfig.first()
            
            if (config.apiKey.isBlank()) {
                return ClaraResult.Error(
                    message = "OpenAI API key not configured",
                    fallback = getFallbackResponse(context)
                )
            }

            val messages = mutableListOf(
                OpenAIMessage(role = "system", content = SYSTEM_PROMPT)
            )
            
            if (userMessage.isNotEmpty()) {
                messages.add(OpenAIMessage(role = "user", content = userMessage))
            } else {
                messages.add(OpenAIMessage(role = "user", content = "The user selected: $context"))
            }

            val request = OpenAIRequest(
                model = config.model,
                messages = messages,
                temperature = 0.4,
                topP = 0.9,
                maxTokens = 150
            )

            val response = openAIApi.createChatCompletion(
                authorization = "Bearer ${config.apiKey}",
                request = request
            )

            val message = response.choices.firstOrNull()?.message?.content
                ?: return ClaraResult.Error(
                    message = "Empty response from OpenAI",
                    fallback = getFallbackResponse(context)
                )

            ClaraResult.Success(message)
        } catch (e: Exception) {
            ClaraResult.Error(
                message = e.message ?: "Unknown error",
                fallback = getFallbackResponse(context)
            )
        }
    }

    private fun getFallbackResponse(context: String): String {
        return FALLBACK_RESPONSES[context] ?: FALLBACK_RESPONSES["general"]!!
    }
}

