package com.ilyk.cleaningplanner.domain.engine

import com.ilyk.cleaningplanner.domain.model.HistoryEntry
import com.ilyk.cleaningplanner.domain.model.Suggestion
import com.ilyk.cleaningplanner.domain.model.SuggestionAction
import com.ilyk.cleaningplanner.domain.model.UserProfile

/**
 * Builds Accept / Dismiss suggestion cards from a user's history and profile.
 *
 * Pure Kotlin, deterministic. Three families today:
 *  - Room-specific maintenance nudges, one per configured room.
 *  - Shopping-list adds when a history note signals running out of a supply.
 *  - A frequency / encouragement card when there is history but nothing else triggered.
 */
class SuggestionEngine {

    fun buildSuggestions(history: List<HistoryEntry>, profile: UserProfile): List<Suggestion> {
        val out = mutableListOf<Suggestion>()

        profile.rooms.forEach { room ->
            out += Suggestion(
                id = "room-${slug(room)}",
                text = "Consider a quick wipe-down in the $room to stay ahead",
                confidence = 60,
                action = SuggestionAction.AdjustSchedule
            )
        }

        history.forEach { entry ->
            val note = entry.note?.lowercase() ?: return@forEach
            val supply = extractRunningLowSupply(note) ?: return@forEach
            out += Suggestion(
                id = "shop-${entry.taskId}-${slug(supply)}",
                text = "Add $supply to the shopping list",
                confidence = 80,
                action = SuggestionAction.AddToShoppingList
            )
        }

        if (history.isNotEmpty() && out.none { it.action == SuggestionAction.AddToShoppingList }) {
            out += Suggestion(
                id = "freq-default",
                text = "You're keeping a steady rhythm — keep at it!",
                confidence = 50,
                action = SuggestionAction.ChangeFrequency
            )
        }

        return out
    }

    private fun slug(s: String): String = s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    private fun extractRunningLowSupply(noteLower: String): String? {
        // "out of <supply>"
        val outOf = noteLower.substringAfter("out of ", "").substringBefore('.').trim()
        if (outOf.isNotEmpty() && !outOf.startsWith("out of")) {
            return outOf.substringBefore(' ').takeIf { it.isNotBlank() }
        }
        // "need more <supply>"
        val needMore = noteLower.substringAfter("need more ", "").substringBefore('.').trim()
        if (needMore.isNotEmpty() && !needMore.startsWith("need more")) {
            return needMore.substringBefore(' ').takeIf { it.isNotBlank() }
        }
        // "running low on <supply>"
        val low = noteLower.substringAfter("running low on ", "").substringBefore('.').trim()
        if (low.isNotEmpty() && !low.startsWith("running low")) {
            return low.substringBefore(' ').takeIf { it.isNotBlank() }
        }
        return null
    }
}
