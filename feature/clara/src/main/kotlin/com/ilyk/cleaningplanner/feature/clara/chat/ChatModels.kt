package com.ilyk.cleaningplanner.feature.clara.chat

import java.util.UUID

/**
 * Chat message in conversation
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false
)

/**
 * Message sender role
 */
enum class MessageRole {
    USER,
    ASSISTANT
}

/**
 * Topic to be covered during onboarding
 */
data class OnboardingTopic(
    val id: String,
    val name: String,
    val description: String,
    val covered: Boolean = false
)

/**
 * Default onboarding topics Clara should cover
 */
object OnboardingTopics {
    val DEFAULT_TOPICS = listOf(
        OnboardingTopic(
            id = "rooms",
            name = "Rooms",
            description = "Which rooms need cleaning attention"
        ),
        OnboardingTopic(
            id = "people",
            name = "People",
            description = "Who lives in the household"
        ),
        OnboardingTopic(
            id = "pets",
            name = "Pets",
            description = "Any pets and their habits"
        ),
        OnboardingTopic(
            id = "schedule",
            name = "Schedule",
            description = "Available time and preferences"
        ),
        OnboardingTopic(
            id = "problem_areas",
            name = "Problem Areas",
            description = "Spots needing extra attention"
        ),
        OnboardingTopic(
            id = "style",
            name = "Cleaning Style",
            description = "Thorough vs quick preferences"
        )
    )
}

/**
 * UI state for text chat screen
 */
data class ClaraTextChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val currentStreamingText: String = "",
    val topics: List<OnboardingTopic> = OnboardingTopics.DEFAULT_TOPICS,
    val error: String? = null,
    val onboardingComplete: Boolean = false,
    val sessionId: String? = null,
    val turnId: String? = null
)

/**
 * Result of onboarding session
 */
data class OnboardingResult(
    val sessionId: String,
    val rooms: List<String> = emptyList(),
    val peopleCount: Int = 0,
    val hasPets: Boolean = false,
    val petTypes: List<String> = emptyList(),
    val preferredSchedule: String = "",
    val problemAreas: List<String> = emptyList(),
    val cleaningStyle: String = "",
    val conversationTranscript: List<ChatMessage> = emptyList()
)

/**
 * Events that can be emitted from the chat
 */
sealed class ChatEvent {
    data class Error(val message: String) : ChatEvent()
    data class OnboardingCompleted(val result: OnboardingResult) : ChatEvent()
    data object SessionStarted : ChatEvent()
    data object Disconnected : ChatEvent()
}
