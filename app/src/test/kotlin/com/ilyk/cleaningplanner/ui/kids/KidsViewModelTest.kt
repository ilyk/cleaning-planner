package com.ilyk.cleaningplanner.ui.kids

import app.cash.turbine.test
import com.ilyk.cleaningplanner.data.repository.PlanRepository
import com.ilyk.cleaningplanner.domain.model.CleaningMode
import com.ilyk.cleaningplanner.domain.model.Plan
import com.ilyk.cleaningplanner.domain.model.PlanMetadata
import com.ilyk.cleaningplanner.domain.model.Task
import com.ilyk.cleaningplanner.domain.model.TaskPriority
import com.ilyk.cleaningplanner.state.PrefsStore
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class KidsViewModelTest {

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

    private fun task(id: String, room: String? = "kitchen", title: String = "Wipe") = Task(
        id = id,
        title = title,
        description = null,
        priority = TaskPriority.NOW,
        estimatedDurationMinutes = 10,
        roomId = room
    )

    private fun plan(tasks: List<Task>) = Plan(
        id = "p-1",
        homeId = "home-1",
        date = "2026-05-12",
        mode = CleaningMode.FOCUS,
        version = 1,
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now(),
        tasks = tasks,
        metadata = PlanMetadata(totalEstimatedMinutes = tasks.sumOf { it.estimatedDurationMinutes }, taskCount = tasks.size)
    )

    @Test
    fun `loadRooms surfaces distinct room ids from the plan`() = runTest {
        coEvery { repo.getToday(any(), any(), any()) } returns plan(
            listOf(
                task("a", "kitchen"),
                task("b", "kitchen"),
                task("c", "bathroom"),
                task("d", null) // null roomId is filtered out
            )
        )
        val vm = KidsViewModel(repo, prefs)
        vm.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertEquals(listOf("kitchen", "bathroom"), state.rooms)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onSelectRoom filters tasks to room and resets completion`() = runTest {
        coEvery { repo.getToday(any(), any(), any()) } returns plan(
            listOf(
                task("k1", "kitchen", "Wipe"),
                task("k2", "kitchen", "Sweep"),
                task("b1", "bathroom", "Scrub")
            )
        )
        val vm = KidsViewModel(repo, prefs)
        vm.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        vm.onSelectRoom("kitchen")
        val state = vm.uiState.value
        assertEquals("kitchen", state.selectedRoom)
        assertEquals(listOf("k1", "k2"), state.tasks.map { it.id })
        assertTrue(state.completedIds.isEmpty())
        assertFalse(state.showCelebration)
    }

    @Test
    fun `onTaskDone marks done in repo and updates progress`() = runTest {
        coEvery { repo.getToday(any(), any(), any()) } returns plan(
            listOf(task("k1", "kitchen"), task("k2", "kitchen"))
        )
        coEvery { repo.markTaskDone(any(), any()) } just Runs

        val vm = KidsViewModel(repo, prefs)
        vm.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        vm.onSelectRoom("kitchen")
        vm.onTaskDone("k1")

        val state = vm.uiState.value
        assertEquals(1, state.doneCount)
        assertEquals(setOf("k1"), state.completedIds)
        assertFalse("Single task done shouldn't celebrate when more remain", state.showCelebration)
        coVerify { repo.markTaskDone("k1", null) }
    }

    @Test
    fun `finishing all tasks in room flips showCelebration true`() = runTest {
        coEvery { repo.getToday(any(), any(), any()) } returns plan(
            listOf(task("k1", "kitchen"), task("k2", "kitchen"))
        )
        coEvery { repo.markTaskDone(any(), any()) } just Runs

        val vm = KidsViewModel(repo, prefs)
        vm.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        vm.onSelectRoom("kitchen")
        vm.onTaskDone("k1")
        vm.onTaskDone("k2")

        val state = vm.uiState.value
        assertEquals(2, state.doneCount)
        assertTrue(state.allDone)
        assertTrue(state.showCelebration)
    }

    @Test
    fun `onBackToRoomPicker clears selection and progress`() = runTest {
        coEvery { repo.getToday(any(), any(), any()) } returns plan(listOf(task("k1", "kitchen")))
        coEvery { repo.markTaskDone(any(), any()) } just Runs

        val vm = KidsViewModel(repo, prefs)
        vm.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        vm.onSelectRoom("kitchen")
        vm.onTaskDone("k1")
        vm.onBackToRoomPicker()

        val state = vm.uiState.value
        assertNull(state.selectedRoom)
        assertTrue(state.tasks.isEmpty())
        assertTrue(state.completedIds.isEmpty())
        assertFalse(state.showCelebration)
    }

    @Test
    fun `missing homeId surfaces a friendly error`() = runTest {
        every { prefs.homeIdFlow } returns flowOf<String?>(null)
        val vm = KidsViewModel(repo, prefs)
        vm.uiState.test {
            var state = awaitItem()
            while (state.isLoading && state.error == null) state = awaitItem()
            assertTrue(state.error != null)
            assertTrue(state.rooms.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
