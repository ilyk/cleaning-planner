package com.ilyk.cleaningplanner.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ilyk.cleaningplanner.core.model.domain.SuggestionAction
import com.ilyk.cleaningplanner.core.model.domain.SuggestionSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@Entity(tableName = "suggestions")
data class SuggestionEntity(
    @PrimaryKey val id: String,
    val text: String,
    val confidence: Int,
    val action: SuggestionAction,
    val source: SuggestionSource = SuggestionSource.LocalAI,
    val targetJson: String? = null, // JSON serialized SuggestionTarget
    val isAccepted: Boolean = false,
    val isDismissed: Boolean = false,
    val appliedLocally: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun fromModel(suggestion: com.ilyk.cleaningplanner.core.model.domain.Suggestion): SuggestionEntity {
            return SuggestionEntity(
                id = suggestion.id,
                text = suggestion.text,
                confidence = suggestion.confidence,
                action = suggestion.action,
                source = suggestion.source,
                targetJson = suggestion.target?.let { 
                    kotlinx.serialization.json.Json.encodeToString(
                        com.ilyk.cleaningplanner.core.model.domain.SuggestionTarget.serializer(),
                        it
                    )
                },
                isAccepted = suggestion.state.accepted,
                isDismissed = suggestion.state.dismissed,
                appliedLocally = suggestion.state.appliedLocally,
                createdAt = System.currentTimeMillis()
            )
        }
        
        fun toModel(entity: SuggestionEntity): com.ilyk.cleaningplanner.core.model.domain.Suggestion {
            val target = entity.targetJson?.let {
                kotlinx.serialization.json.Json.decodeFromString(
                    com.ilyk.cleaningplanner.core.model.domain.SuggestionTarget.serializer(),
                    it
                )
            }
            
            return com.ilyk.cleaningplanner.core.model.domain.Suggestion(
                id = entity.id,
                text = entity.text,
                confidence = entity.confidence,
                action = entity.action,
                source = entity.source,
                target = target,
                state = com.ilyk.cleaningplanner.core.model.domain.SuggestionState(
                    accepted = entity.isAccepted,
                    dismissed = entity.isDismissed,
                    appliedLocally = entity.appliedLocally
                )
            )
        }
    }
}
