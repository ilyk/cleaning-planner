package com.ilyk.cleaningplanner.domain.engine

import com.ilyk.cleaningplanner.domain.model.CleaningMode
import com.ilyk.cleaningplanner.domain.model.HistoryEntry
import com.ilyk.cleaningplanner.domain.model.Task
import com.ilyk.cleaningplanner.domain.model.TaskPriority
import com.ilyk.cleaningplanner.domain.model.UserProfile
import kotlinx.datetime.LocalDate

/**
 * Pure-Kotlin daily plan generator. Deterministic: same inputs always produce the same output.
 *
 * Per-room templates drive task synthesis. Mode shapes the selection:
 *  - FOCUS:      keep only quick-win templates (<=15 min).
 *  - FULL_RESET: all templates per room.
 *  - LOW_ENERGY: light templates only (a strict subset of FULL_RESET).
 *  - PET_MODE:   FULL_RESET plus pet-specific tasks when profile.hasPets.
 *
 * History is currently passed in but used only to inform future scheduling decisions (see
 * [LearningEngine]). The current generator does not yet bias on history; it is left as a
 * deliberate hook for W6 / W8 work.
 */
class PlanEngine {

    fun generateDailyPlan(
        profile: UserProfile,
        history: List<HistoryEntry>,
        mode: CleaningMode,
        date: LocalDate
    ): List<Task> {
        val templates = templatesFor(mode)
        val tasks = mutableListOf<Task>()

        profile.rooms.forEachIndexed { roomIndex, room ->
            templates.forEachIndexed { templateIndex, template ->
                tasks += template.toTask(
                    id = stableId(mode, date, "room$roomIndex", templateIndex),
                    room = room
                )
            }
        }

        if (mode == CleaningMode.PET_MODE && profile.hasPets) {
            PET_TEMPLATES.forEachIndexed { i, template ->
                tasks += template.toTask(
                    id = stableId(mode, date, "pet", i),
                    room = profile.rooms.firstOrNull()
                )
            }
        }

        return tasks
    }

    private fun templatesFor(mode: CleaningMode): List<TaskTemplate> = when (mode) {
        CleaningMode.FOCUS -> ROOM_TEMPLATES.filter { it.durationMin <= 15 }
        CleaningMode.LOW_ENERGY -> ROOM_TEMPLATES.filter { it.energy == Energy.LIGHT }
        CleaningMode.FULL_RESET -> ROOM_TEMPLATES
        CleaningMode.PET_MODE -> ROOM_TEMPLATES
    }

    private fun stableId(mode: CleaningMode, date: LocalDate, scope: String, index: Int): String =
        "task-${mode.name.lowercase()}-$date-$scope-$index"

    private enum class Energy { LIGHT, HEAVY }

    private data class TaskTemplate(
        val titleTemplate: String,
        val durationMin: Int,
        val priority: TaskPriority,
        val energy: Energy
    ) {
        fun toTask(id: String, room: String?): Task {
            val title = if (room != null) titleTemplate.replace("{room}", room) else titleTemplate
            return Task(
                id = id,
                title = title,
                description = null,
                priority = priority,
                estimatedDurationMinutes = durationMin,
                roomId = room
            )
        }
    }

    private companion object {
        // Ordered light-to-heavy. FOCUS keeps the <=15 min slice; LOW_ENERGY keeps LIGHT only.
        val ROOM_TEMPLATES: List<TaskTemplate> = listOf(
            TaskTemplate("Quick wipe down in the {room}", 5, TaskPriority.NOW, Energy.LIGHT),
            TaskTemplate("Tidy up the {room}", 10, TaskPriority.NOW, Energy.LIGHT),
            TaskTemplate("Sweep the {room} floor", 12, TaskPriority.NEXT, Energy.HEAVY),
            TaskTemplate("Deep clean the {room}", 25, TaskPriority.NEXT, Energy.HEAVY),
            TaskTemplate("Reorganize {room} storage", 30, TaskPriority.LATER, Energy.HEAVY),
        )

        val PET_TEMPLATES: List<TaskTemplate> = listOf(
            TaskTemplate("Vacuum pet hair", 10, TaskPriority.NOW, Energy.LIGHT),
            TaskTemplate("Refresh pet water and food area", 5, TaskPriority.NOW, Energy.LIGHT),
            TaskTemplate("Wash pet bedding", 20, TaskPriority.NEXT, Energy.HEAVY),
        )
    }
}
