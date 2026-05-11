package com.ilyk.cleaningplanner.domain.engine

import com.ilyk.cleaningplanner.domain.model.*
import com.ilyk.cleaningplanner.core.model.TaskStatus
import kotlinx.datetime.LocalDate
import org.junit.Test
import org.junit.Assert.*

class LearningEngineTest {

    private val learningEngine = LearningEngine()

    @Test
    fun `onTaskRecorded should track task history`() {
        val entry = HistoryEntry(
            taskId = "task1",
            date = LocalDate(2024, 1, 1),
            status = TaskStatus.Done,
            durationMin = 10,
            note = null
        )

        learningEngine.onTaskRecorded(entry)

        val successRate = learningEngine.getSuccessRate("task1")
        assertEquals("Success rate should be 1.0", 1.0f, successRate, 0.01f)
    }

    @Test
    fun `adjustedEstimate should return original estimate for new task`() {
        val task = Task(
            id = "new_task",
            title = "New Task",
            priority = TaskPriority.NOW,
            estimatedDurationMinutes = 15,
            roomId = "kitchen"
        )

        val adjustedEstimate = learningEngine.adjustedEstimate(task)

        assertEquals("Should return original estimate for new task", 15, adjustedEstimate)
    }

    @Test
    fun `adjustedEstimate should adjust based on history`() {
        val task = Task(
            id = "task1",
            title = "Task 1",
            priority = TaskPriority.NOW,
            estimatedDurationMinutes = 15,
            roomId = "kitchen"
        )

        // Record successful completion with shorter duration
        val entry = HistoryEntry(
            taskId = "task1",
            date = LocalDate(2024, 1, 1),
            status = TaskStatus.Done,
            durationMin = 8,
            note = null
        )

        learningEngine.onTaskRecorded(entry)

        val adjustedEstimate = learningEngine.adjustedEstimate(task)

        assertTrue("Should adjust estimate based on actual duration", adjustedEstimate < 15)
        assertTrue("Should add buffer to actual duration", adjustedEstimate > 8)
    }

    @Test
    fun `getSuccessRate should calculate correctly`() {
        val taskId = "task1"

        // Add successful completion
        learningEngine.onTaskRecorded(HistoryEntry(
            taskId = taskId,
            date = LocalDate(2024, 1, 1),
            status = TaskStatus.Done,
            durationMin = 10,
            note = null
        ))

        // Add skipped task
        learningEngine.onTaskRecorded(HistoryEntry(
            taskId = taskId,
            date = LocalDate(2024, 1, 2),
            status = TaskStatus.Skipped,
            durationMin = null,
            note = null
        ))

        val successRate = learningEngine.getSuccessRate(taskId)

        assertEquals("Success rate should be 0.5", 0.5f, successRate, 0.01f)
    }
}
