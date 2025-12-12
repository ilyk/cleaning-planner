package com.ilyk.cleaningplanner.data.repository

import com.ilyk.cleaningplanner.data.local.database.PlanDao
import com.ilyk.cleaningplanner.data.remote.api.PlansApi
import com.ilyk.cleaningplanner.data.remote.dto.GeneratePlanRequest
import com.ilyk.cleaningplanner.data.remote.dto.RevisePlanRequest
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

class PlanRepositoryTest {
    
    private lateinit var plansApi: PlansApi
    private lateinit var planDao: PlanDao
    private lateinit var cleanFlowApi: com.ilyk.cleaningplanner.data.network.api.CleanFlowApi
    private lateinit var planRepository: PlanRepository
    
    @Before
    fun setup() {
        plansApi = mockk()
        planDao = mockk()
        cleanFlowApi = mockk()
        planRepository = PlanRepository(plansApi, planDao, cleanFlowApi)
    }
    
    @Test
    fun `generatePlan should return success when API call succeeds`() = runTest {
        // Given
        val homeId = "home123"
        val date = "2024-01-15"
        val mode = CleaningMode.FOCUS
        val expectedPlan = createTestPlan()
        
        coEvery { plansApi.generate(any()) } returns expectedPlan
        coEvery { planDao.insertPlan(any()) } just Runs
        coEvery { planDao.insertTasks(any()) } just Runs
        
        // When
        val result = planRepository.generatePlan(homeId, date, mode)
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(expectedPlan, result.getOrNull())
        
        coVerify { plansApi.generate(GeneratePlanRequest(homeId, date, mode)) }
        coVerify { planDao.insertPlan(any()) }
        coVerify { planDao.insertTasks(any()) }
    }
    
    @Test
    fun `generatePlan should return failure when API call fails`() = runTest {
        // Given
        val homeId = "home123"
        val date = "2024-01-15"
        val mode = CleaningMode.FOCUS
        val exception = Exception("Network error")
        
        coEvery { plansApi.generate(any()) } throws exception
        
        // When
        val result = planRepository.generatePlan(homeId, date, mode)
        
        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }
    
    @Test
    fun `getPlan should return cached plan when API fails`() = runTest {
        // Given
        val planId = "plan123"
        val cachedPlan = createTestPlanEntity()
        val exception = Exception("Network error")
        
        coEvery { plansApi.get(planId) } throws exception
        coEvery { planDao.getLatestPlan(planId, "", "") } returns cachedPlan
        
        // When
        val result = planRepository.getPlan(planId)
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(planId, result.getOrNull()?.id)
    }
    
    @Test
    fun `completeTask should update local state optimistically`() = runTest {
        // Given
        val taskId = "task123"
        
        coEvery { planDao.markTaskCompleted(taskId, any()) } just Runs
        
        // When
        val result = planRepository.completeTask(taskId)
        
        // Then
        assertTrue(result.isSuccess)
        coVerify { planDao.markTaskCompleted(taskId, any()) }
    }
    
    @Test
    fun `observeLatestPlan should return flow from DAO`() = runTest {
        // Given
        val homeId = "home123"
        val date = "2024-01-15"
        val mode = CleaningMode.FOCUS
        val expectedEntity = createTestPlanEntity()
        
        every { planDao.observeLatestPlan(homeId, date, mode.name) } returns flowOf(expectedEntity)
        
        // When
        val result = planRepository.observeLatestPlan(homeId, date, mode)
        
        // Then
        val plan = result.first()
        assertNotNull(plan)
        assertEquals(expectedEntity.id, plan?.id)
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
    
    private fun createTestPlanEntity(): com.ilyk.cleaningplanner.data.local.database.PlanEntity {
        return com.ilyk.cleaningplanner.data.local.database.PlanEntity(
            id = "plan123",
            homeId = "home123",
            date = "2024-01-15",
            mode = CleaningMode.FOCUS,
            version = 1,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            totalEstimatedMinutes = 30,
            taskCount = 1,
            completedCount = 0,
            skippedCount = 0,
            efficiencyScore = null
        )
    }
}
