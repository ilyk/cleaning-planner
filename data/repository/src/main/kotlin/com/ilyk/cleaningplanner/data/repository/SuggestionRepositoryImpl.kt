package com.ilyk.cleaningplanner.data.repository

import com.ilyk.cleaningplanner.domain.model.Suggestion
import com.ilyk.cleaningplanner.domain.model.SuggestionAction
import com.ilyk.cleaningplanner.domain.model.SuggestionSource
import com.ilyk.cleaningplanner.data.database.dao.SuggestionDao
import com.ilyk.cleaningplanner.data.database.entities.SuggestionEntity
import com.ilyk.cleaningplanner.data.network.api.CleanFlowApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SuggestionRepositoryImpl @Inject constructor(
    private val suggestionDao: SuggestionDao,
    private val cleanFlowApi: CleanFlowApi
) : SuggestionRepository {
    
    override fun getActiveSuggestions(): Flow<List<Suggestion>> {
        return suggestionDao.getActiveSuggestions().map { entities ->
            entities.map { SuggestionEntity.toModel(it) }
        }
    }

    override suspend fun insertSuggestion(suggestion: Suggestion) {
        suggestionDao.insertSuggestion(SuggestionEntity.fromModel(suggestion))
    }

    override suspend fun updateSuggestion(suggestion: Suggestion) {
        suggestionDao.insertSuggestion(SuggestionEntity.fromModel(suggestion))
    }

    override suspend fun deleteSuggestion(suggestionId: String) {
        suggestionDao.deleteSuggestionById(suggestionId)
    }

    override suspend fun acceptSuggestion(suggestionId: String) {
        suggestionDao.acceptSuggestion(suggestionId)
    }

    override suspend fun dismissSuggestion(suggestionId: String) {
        suggestionDao.dismissSuggestion(suggestionId)
    }
    
    /**
     * Sync suggestions from backend for a given home/plan
     */
    suspend fun syncSuggestionsFromBackend(
        homeId: String,
        planId: String? = null
    ): Result<Int> {
        return try {
            val response = cleanFlowApi.getSuggestions(homeId, planId)
            
            // Convert backend payloads to domain models and cache locally
            val suggestions = response.suggestions.map { payload ->
                Suggestion(
                    id = payload.id,
                    text = payload.text,
                    confidence = payload.confidence,
                    action = try {
                        // Map backend snake_case to enum
                        when (payload.action.lowercase()) {
                            "adjust_schedule" -> SuggestionAction.AdjustSchedule
                            "merge_tasks" -> SuggestionAction.MergeTasks
                            "add_to_shopping_list" -> SuggestionAction.AddToShoppingList
                            "change_frequency" -> SuggestionAction.ChangeFrequency
                            "assign_to_member" -> SuggestionAction.AssignToMember
                            "split_task" -> SuggestionAction.SplitTask
                            else -> SuggestionAction.AdjustSchedule
                        }
                    } catch (e: Exception) {
                        SuggestionAction.AdjustSchedule
                    },
                    source = if (payload.source == "cloud_ai") SuggestionSource.CloudAI else SuggestionSource.LocalAI,
                    target = payload.target?.let { targetJson ->
                        // Parse target from JsonObject
                        com.ilyk.cleaningplanner.domain.model.SuggestionTarget(
                            taskId = targetJson["task_id"]?.toString()?.trim('"'),
                            field = targetJson["field"]?.toString()?.trim('"'),
                            proposedValue = targetJson["proposed_value"] as? kotlinx.serialization.json.JsonObject
                        )
                    },
                    state = com.ilyk.cleaningplanner.domain.model.SuggestionState(
                        accepted = payload.state.accepted,
                        dismissed = payload.state.dismissed,
                        appliedLocally = payload.state.appliedLocally
                    )
                )
            }
            
            // Cache in Room (upsert)
            suggestionDao.insertSuggestions(suggestions.map { SuggestionEntity.fromModel(it) })
            
            Result.success(suggestions.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
