package com.ilyk.cleaningplanner.ui.planner

import app.cash.turbine.test
import com.ilyk.cleaningplanner.data.repository.PlanRepository
import com.ilyk.cleaningplanner.domain.model.CleaningMode
import com.ilyk.cleaningplanner.domain.model.Plan
import com.ilyk.cleaningplanner.domain.model.PlanMetadata
import com.ilyk.cleaningplanner.domain.model.Task
import com.ilyk.cleaningplanner.domain.model.TaskPriority
import com.ilyk.cleaningplanner.state.PrefsStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlannerViewModelTest {

    private val repo: PlanRepository = mockk(relaxed = true)
    private val prefs: PrefsStore = mockk()

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { prefs.homeIdFlow } returns flowOf("home-1")
        every { prefs.currentModeFlow } returns flowOf("FOCUS")
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun samplePlan(taskCount: Int = 2): Plan {
        val tasks = (0 until taskCount).map { i ->
            Task(
                id = "t-$i",
                title = "Task $i",
                description = null,
                priority = if (i == 0) TaskPriority.NOW else TaskPriority.NEXT,
                estimatedDurationMinutes = 10,
                roomId = "kitchen"
            )
        }
        return Plan(
            id = "plan-1",
            homeId = "home-1",
            date = "2026-05-11",
            mode = CleaningMode.FOCUS,
            version = 1,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            tasks = tasks,
            metadata = PlanMetadata(totalEstimatedMinutes = 10 * taskCount, taskCount = taskCount)
        )
    }

    @Test
    fun `loads today plan on init`() = runTest {
        coEvery { repo.getToday("home-1", any(), CleaningMode.FOCUS) } returns samplePlan(2)

        val vm = PlannerViewModel(repo, prefs)

        vm.uiState.test {
            // First emission may be the initial loading state; consume until plan is set.
            var state = awaitItem()
            while (state.plan == null && state.error == null) state = awaitItem()
            assertNotNull(state.plan)
            assertEquals(2, state.plan!!.tasks.size)
            assertEquals(CleaningMode.FOCUS, state.mode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tab change updates selected tab`() = runTest {
        coEvery { repo.getToday(any(), any(), any()) } returns samplePlan()
        val vm = PlannerViewModel(repo, prefs)

        vm.onTabChange(PlannerTab.Custom)
        assertEquals(PlannerTab.Custom, vm.uiState.value.selectedTab)

        vm.onTabChange(PlannerTab.Weekly)
        assertEquals(PlannerTab.Weekly, vm.uiState.value.selectedTab)
    }

    @Test
    fun `onAddCustomTask appends to customTasks`() = runTest {
        coEvery { repo.getToday(any(), any(), any()) } returns samplePlan(0)
        every { repo.addCustomTask(any(), any(), any(), any(), any()) } answers {
            Task(
                id = "custom-1",
                title = firstArg(),
                description = null,
                priority = arg(3),
                estimatedDurationMinutes = arg(2),
                roomId = secondArg<String?>()
            )
        }

        val vm = PlannerViewModel(repo, prefs)
        vm.onAddCustomTask("Wipe counters", "kitchen", 12, TaskPriority.NOW)

        val state = vm.uiState.value
        assertEquals(1, state.customTasks.size)
        assertEquals("Wipe counters", state.customTasks.first().title)
        assertEquals(12, state.customTasks.first().estimatedDurationMinutes)
        assertEquals(TaskPriority.NOW, state.customTasks.first().priority)
    }

    @Test
    fun `onCompleteTask + onSkipTask invoke repository`() = runTest {
        coEvery { repo.getToday(any(), any(), any()) } returns samplePlan()
        coEvery { repo.markTaskDone(any(), any()) } just Runs
        coEvery { repo.markTaskSkipped(any(), any()) } just Runs

        val vm = PlannerViewModel(repo, prefs)
        vm.onCompleteTask("t-0")
        vm.onSkipTask("t-1")

        coVerify { repo.markTaskDone("t-0", null) }
        coVerify { repo.markTaskSkipped("t-1", null) }
    }

    @Test
    fun `missing homeId surfaces a friendly error`() = runTest {
        every { prefs.homeIdFlow } returns flowOf<String?>(null)
        val vm = PlannerViewModel(repo, prefs)

        vm.uiState.test {
            var state = awaitItem()
            while (state.error == null && state.plan == null && state.isLoading) state = awaitItem()
            assertTrue("Should have surfaced an error", state.error != null)
            assertEquals(null, state.plan)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
