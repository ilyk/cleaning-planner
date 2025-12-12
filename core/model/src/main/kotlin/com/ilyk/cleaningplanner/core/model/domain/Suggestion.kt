package com.ilyk.cleaningplanner.core.model.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonObject

@Serializable
data class Suggestion(
    val id: String,
    val text: String,
    val confidence: Int, // 0..100
    val action: SuggestionAction,
    val source: SuggestionSource = SuggestionSource.LocalAI,
    val target: SuggestionTarget? = null,
    val state: SuggestionState = SuggestionState()
)

@Serializable
enum class SuggestionAction {
    @SerialName("adjust_schedule")
    AdjustSchedule,
    @SerialName("merge_tasks")
    MergeTasks,
    @SerialName("add_to_shopping_list")
    AddToShoppingList,
    @SerialName("change_frequency")
    ChangeFrequency,
    @SerialName("assign_to_member")
    AssignToMember,
    @SerialName("split_task")
    SplitTask
}

@Serializable
enum class SuggestionSource {
    @SerialName("local_ai")
    LocalAI,
    @SerialName("cloud_ai")
    CloudAI
}

@Serializable
data class SuggestionTarget(
    @SerialName("task_id")
    val taskId: String? = null,
    val field: String? = null,
    @SerialName("proposed_value")
    val proposedValue: JsonObject? = null
)

@Serializable
data class SuggestionState(
    val accepted: Boolean = false,
    val dismissed: Boolean = false,
    @SerialName("applied_locally")
    val appliedLocally: Boolean = false
)
