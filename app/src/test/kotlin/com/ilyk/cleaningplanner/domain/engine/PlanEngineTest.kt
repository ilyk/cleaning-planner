package com.ilyk.cleaningplanner.domain.engine

import com.ilyk.cleaningplanner.domain.model.*
import com.ilyk.cleaningplanner.core.model.TaskStatus
import kotlinx.datetime.LocalDate
import org.junit.Test
import org.junit.Assert.*

class PlanEngineTest {

    private val planEngine = PlanEngine()

    @Test
    fun `generateDailyPlan should return tasks for Focus mode`() {
        val profile = UserProfile(
            name = "Test User",
            rooms = listOf("kitchen", "bathroom"),
            floors = 1,
            hasPets = false,
            devices = emptyList(),
            preference = Preference.Minimalist
        )
        val history = emptyList<HistoryEntry>()
        val date = LocalDate(2024, 1, 1)

        val result = planEngine.generateDailyPlan(profile, history, CleaningMode.FOCUS, date)

        assertTrue("Should generate tasks", result.isNotEmpty())
        assertTrue("All tasks should be 15 minutes or less", result.all { it.estimatedDurationMinutes <= 15 })
        assertTrue("Should have tasks for each room", result.any { it.roomId == "kitchen" })
        assertTrue("Should have tasks for each room", result.any { it.roomId == "bathroom" })
    }

    @Test
    fun `generateDailyPlan should include pet tasks in PetMode`() {
        val profile = UserProfile(
            name = "Test User",
            rooms = listOf("kitchen"),
            floors = 1,
            hasPets = true,
            devices = emptyList(),
            preference = Preference.Minimalist
        )
        val history = emptyList<HistoryEntry>()
        val date = LocalDate(2024, 1, 1)

        val result = planEngine.generateDailyPlan(profile, history, CleaningMode.PET_MODE, date)

        assertTrue("Should include pet-related tasks", result.any { it.title.contains("pet", ignoreCase = true) })
    }

    @Test
    fun `generateDailyPlan should reduce task count in LowEnergy mode`() {
        val profile = UserProfile(
            name = "Test User",
            rooms = listOf("kitchen", "bathroom", "bedroom", "living room"),
            floors = 1,
            hasPets = false,
            devices = emptyList(),
            preference = Preference.Minimalist
        )
        val history = emptyList<HistoryEntry>()
        val date = LocalDate(2024, 1, 1)

        val fullResetResult = planEngine.generateDailyPlan(profile, history, CleaningMode.FULL_RESET, date)
        val lowEnergyResult = planEngine.generateDailyPlan(profile, history, CleaningMode.LOW_ENERGY, date)

        assertTrue("LowEnergy should have fewer tasks", lowEnergyResult.size < fullResetResult.size)
    }
}
