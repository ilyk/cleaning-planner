package com.ilyk.cleaningplanner.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenAIRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    val temperature: Double = 0.4,
    @SerialName("top_p")
    val topP: Double = 0.9,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    @SerialName("max_completion_tokens")
    val maxCompletionTokens: Int? = null
) {
    companion object {
        fun create(
            model: String,
            messages: List<OpenAIMessage>,
            temperature: Double = 0.4,
            topP: Double = 0.9,
            maxTokens: Int = 150
        ): OpenAIRequest {
            // GPT-5 and o-series models use max_completion_tokens
            // Earlier models use max_tokens
            val isNewModel = model.startsWith("gpt-5") || model.startsWith("o1") || model.startsWith("o3")
            
            return if (isNewModel) {
                OpenAIRequest(
                    model = model,
                    messages = messages,
                    temperature = temperature,
                    topP = topP,
                    maxTokens = null,
                    maxCompletionTokens = maxTokens
                )
            } else {
                OpenAIRequest(
                    model = model,
                    messages = messages,
                    temperature = temperature,
                    topP = topP,
                    maxTokens = maxTokens,
                    maxCompletionTokens = null
                )
            }
        }
    }
}

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

