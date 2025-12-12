package com.ilyk.cleaningplanner.domain.engine

import com.ilyk.cleaningplanner.core.model.domain.*
import kotlinx.datetime.LocalDate
import org.junit.Test
import org.junit.Assert.*

class SuggestionEngineTest {

    private val suggestionEngine = SuggestionEngine()

    @Test
    fun `buildSuggestions should return suggestions`() {
        val profile = UserProfile(
            name = "Test User",
            rooms = listOf("kitchen", "bathroom"),
            floors = 1,
            hasPets = false,
            devices = emptyList(),
            preference = Preference.Minimalist
        )
        val history = listOf(
            HistoryEntry(
                taskId = "task1",
                date = LocalDate(2024, 1, 1),
                status = TaskStatus.Done,
                durationMin = 10,
                note = null
            )
        )

        val suggestions = suggestionEngine.buildSuggestions(history, profile)

        assertTrue("Should generate suggestions", suggestions.isNotEmpty())
        assertTrue("All suggestions should have valid confidence", suggestions.all { it.confidence in 0..100 })
        assertTrue("All suggestions should have valid actions", suggestions.all { it.action != null })
    }

    @Test
    fun `buildSuggestions should generate room-specific suggestions`() {
        val profile = UserProfile(
            name = "Test User",
            rooms = listOf("bathroom"),
            floors = 1,
            hasPets = false,
            devices = emptyList(),
            preference = Preference.Minimalist
        )
        val history = emptyList<HistoryEntry>()

        val suggestions = suggestionEngine.buildSuggestions(history, profile)

        assertTrue("Should generate bathroom-specific suggestions", 
            suggestions.any { it.text.contains("bathroom", ignoreCase = true) })
    }

    @Test
    fun `buildSuggestions should generate shopping suggestions for cleaning supplies`() {
        val profile = UserProfile(
            name = "Test User",
            rooms = listOf("kitchen"),
            floors = 1,
            hasPets = false,
            devices = emptyList(),
            preference = Preference.Minimalist
        )
        val history = listOf(
            HistoryEntry(
                taskId = "task1",
                date = LocalDate(2024, 1, 1),
                status = TaskStatus.Done,
                durationMin = 10,
                note = "out of detergent"
            )
        )

        val suggestions = suggestionEngine.buildSuggestions(history, profile)

        assertTrue("Should generate shopping suggestions", 
            suggestions.any { it.action == SuggestionAction.AddToShoppingList })
    }
}
