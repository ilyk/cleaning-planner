package com.ilyk.cleaningplanner.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Separate request types for different model families
@Serializable
data class OpenAIRequestGPT5(
    val model: String,
    val messages: List<OpenAIMessage>,
    @SerialName("max_completion_tokens")
    val maxCompletionTokens: Int
    // GPT-5 only supports default temperature (1) and top_p - custom values not allowed
)

@Serializable
data class OpenAIRequestLegacy(
    val model: String,
    val messages: List<OpenAIMessage>,
    val temperature: Double = 0.4,
    @SerialName("top_p")
    val topP: Double = 0.9,
    @SerialName("max_tokens")
    val maxTokens: Int
)

// Keep legacy class name for compatibility
typealias OpenAIRequest = OpenAIRequestLegacy

@Serializable
data class OpenAIMessage(
    val role: String,
    val content: String
)

@Serializable
data class OpenAIResponse(
    val id: String,
    val choices: List<OpenAIChoice>,
    val usage: OpenAIUsage? = null
)

@Serializable
data class OpenAIChoice(
    val index: Int,
    val message: OpenAIMessage,
    @SerialName("finish_reason")
    val finishReason: String
)

@Serializable
data class OpenAIUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int,
    @SerialName("completion_tokens")
    val completionTokens: Int,
    @SerialName("total_tokens")
    val totalTokens: Int
)

@Serializable
data class OpenAIConfig(
    val provider: String = "OpenAI",
    val model: String = "gpt-5",
    val apiKey: String = ""
)

@Serializable
data class WhisperResponse(
    val text: String
)
