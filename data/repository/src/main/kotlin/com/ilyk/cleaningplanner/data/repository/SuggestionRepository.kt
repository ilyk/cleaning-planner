package com.ilyk.cleaningplanner.data.repository

import com.ilyk.cleaningplanner.core.model.domain.Suggestion
import kotlinx.coroutines.flow.Flow

interface SuggestionRepository {
    fun getActiveSuggestions(): Flow<List<Suggestion>>
    suspend fun insertSuggestion(suggestion: Suggestion)
    suspend fun updateSuggestion(suggestion: Suggestion)
    suspend fun deleteSuggestion(suggestionId: String)
    suspend fun acceptSuggestion(suggestionId: String)
    suspend fun dismissSuggestion(suggestionId: String)
}
