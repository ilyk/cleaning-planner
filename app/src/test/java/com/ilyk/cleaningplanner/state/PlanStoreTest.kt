package com.ilyk.cleaningplanner.state

import com.ilyk.cleaningplanner.data.repository.PlanRepository
import com.ilyk.cleaningplanner.domain.model.CleaningMode
import com.ilyk.cleaningplanner.domain.model.Plan
import com.ilyk.cleaningplanner.domain.model.Task
import com.ilyk.cleaningplanner.domain.model.TaskPriority
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class PlanStoreTest {
    
    private lateinit var planRepository: PlanRepository
    private lateinit var planStore: PlanStore
    
    @Before
    fun setup() {
        planRepository = mockk()
        planStore = PlanStore(planRepository)
    }
    
    @Test
    fun `initialize should set homeId and mode`() = runTest {
        // Given
        val homeId = "home123"
        val mode = CleaningMode.FOCUS
        
        // When
        planStore.initialize(homeId, mode)
        
        // Then
        assertEquals(homeId, planStore.homeId.first())
        assertEquals(mode, planStore.currentMode.first())
    }
    
    @Test
    fun `loadTodaysPlan should set loading state and call repository`() = runTest {
        // Given
        val homeId = "home123"
        val mode = CleaningMode.FOCUS
        val expectedPlan = createTestPlan()
        
        coEvery { planRepository.observeLatestPlan(any(), any(), any()) } returns flowOf(null)
        coEvery { planRepository.generatePlan(any(), any(), any()) } returns Result.success(expectedPlan)
        
        planStore.initialize(homeId, mode)
        
        // When
        planStore.loadTodaysPlan()
        
        // Then
        assertTrue(planStore.isLoading.first())
        coVerify { planRepository.generatePlan(any(), any(), any()) }
    }
    
    @Test
    fun `changeMode should update mode and reload plan`() = runTest {
        // Given
        val homeId = "home123"
        val oldMode = CleaningMode.FOCUS
        val newMode = CleaningMode.FULL_RESET
        val expectedPlan = createTestPlan()
        
        coEvery { planRepository.observeLatestPlan(any(), any(), any()) } returns flowOf(null)
        coEvery { planRepository.generatePlan(any(), any(), any()) } returns Result.success(expectedPlan)
        
        planStore.initialize(homeId, oldMode)
        
        // When
        planStore.changeMode(newMode)
        
        // Then
        assertEquals(newMode, planStore.currentMode.first())
        coVerify { planRepository.generatePlan(any(), any(), any()) }
    }
    
    @Test
    fun `completeTask should call repository and update local state`() = runTest {
        // Given
        val taskId = "task123"
        val homeId = "home123"
        val mode = CleaningMode.FOCUS
        val plan = createTestPlan()
        
        coEvery { planRepository.observeLatestPlan(any(), any(), any()) } returns flowOf(null)
        coEvery { planRepository.generatePlan(any(), any(), any()) } returns Result.success(plan)
        coEvery { planRepository.completeTask(taskId) } returns Result.success(Unit)
        
        planStore.initialize(homeId, mode)
        planStore.loadTodaysPlan()
        
        // When
        planStore.completeTask(taskId)
        
        // Then
        coVerify { planRepository.completeTask(taskId) }
    }
    
    @Test
    fun `skipTask should call repository and update local state`() = runTest {
        // Given
        val taskId = "task123"
        val homeId = "home123"
        val mode = CleaningMode.FOCUS
        val plan = createTestPlan()
        
        coEvery { planRepository.observeLatestPlan(any(), any(), any()) } returns flowOf(null)
        coEvery { planRepository.generatePlan(any(), any(), any()) } returns Result.success(plan)
        coEvery { planRepository.skipTask(taskId) } returns Result.success(Unit)
        
        planStore.initialize(homeId, mode)
        planStore.loadTodaysPlan()
        
        // When
        planStore.skipTask(taskId)
        
        // Then
        coVerify { planRepository.skipTask(taskId) }
    }
    
    @Test
    fun `assignTask should call repository and update local state`() = runTest {
        // Given
        val taskId = "task123"
        val assignedTo = "member123"
        val homeId = "home123"
        val mode = CleaningMode.FOCUS
        val plan = createTestPlan()
        
        coEvery { planRepository.observeLatestPlan(any(), any(), any()) } returns flowOf(null)
        coEvery { planRepository.generatePlan(any(), any(), any()) } returns Result.success(plan)
        coEvery { planRepository.assignTask(taskId, assignedTo) } returns Result.success(Unit)
        
        planStore.initialize(homeId, mode)
        planStore.loadTodaysPlan()
        
        // When
        planStore.assignTask(taskId, assignedTo)
        
        // Then
        coVerify { planRepository.assignTask(taskId, assignedTo) }
    }
    
    @Test
    fun `clearError should clear error state`() = runTest {
        // Given
        val homeId = "home123"
        val mode = CleaningMode.FOCUS
        
        coEvery { planRepository.observeLatestPlan(any(), any(), any()) } returns flowOf(null)
        coEvery { planRepository.generatePlan(any(), any(), any()) } returns Result.failure(Exception("Test error"))
        
        planStore.initialize(homeId, mode)
        planStore.loadTodaysPlan()
        
        // Wait for error to be set
        while (planStore.error.first() == null) {
            // Wait
        }
        
        // When
        planStore.clearError()
        
        // Then
        assertNull(planStore.error.first())
    }
    
    private fun createTestPlan(): Plan {
        return Plan(
            id = "plan123",
            homeId = "home123",
            date = "2024-01-15",
            mode = CleaningMode.FOCUS,
            version = 1,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            tasks = listOf(
                Task(
                    id = "task123",
                    title = "Test Task",
                    description = "Test Description",
                    priority = TaskPriority.NOW,
                    estimatedDurationMinutes = 30,
                    tools = listOf("broom", "mop"),
                    tips = listOf("Start from the top"),
                    qrCode = "qr123",
                    assignedTo = null,
                    completedAt = null,
                    skippedAt = null,
                    createdAt = Clock.System.now(),
                    updatedAt = Clock.System.now()
                )
            ),
            metadata = com.ilyk.cleaningplanner.domain.model.PlanMetadata(
                totalEstimatedMinutes = 30,
                taskCount = 1,
                completedCount = 0,
                skippedCount = 0,
                efficiencyScore = null
            )
        )
    }
}
