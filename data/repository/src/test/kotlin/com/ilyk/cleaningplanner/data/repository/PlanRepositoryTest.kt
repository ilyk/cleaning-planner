package com.ilyk.cleaningplanner.data.repository

import com.ilyk.cleaningplanner.core.model.TaskStatus
import com.ilyk.cleaningplanner.data.database.dao.HistoryEntryDao
import com.ilyk.cleaningplanner.data.database.dao.PlanDao
import com.ilyk.cleaningplanner.data.database.entities.HistoryEntryEntity
import com.ilyk.cleaningplanner.data.database.entities.PendingOpEntity
import com.ilyk.cleaningplanner.data.database.entities.PlanEntity
import com.ilyk.cleaningplanner.data.network.api.CleanFlowApi
import com.ilyk.cleaningplanner.data.remote.api.PlansApi
import com.ilyk.cleaningplanner.data.remote.dto.AssignTaskRequest
import com.ilyk.cleaningplanner.data.remote.dto.CompleteTaskRequest
import com.ilyk.cleaningplanner.data.remote.dto.SkipTaskRequest
import com.ilyk.cleaningplanner.domain.model.CleaningMode
import com.ilyk.cleaningplanner.domain.model.Plan
import com.ilyk.cleaningplanner.domain.model.PlanMetadata
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/**
 * Exercises the offline-first contract:
 *  - `getToday` returns the cached plan when one exists, without hitting the network.
 *  - `getToday` falls back to a local plan when the cache misses and the API errors.
 *  - `markTaskDone` / `markTaskSkipped` write a HistoryEntry locally AND queue a server
 *    sync op via PlanDao, even before any network round-trip.
 *  - `assignTask` enqueues an ASSIGN pending op (no local mutation needed today).
 *
 * Anything DB-bound is mocked with mockk. No real Room / Retrofit / network needed.
 */
class PlanRepositoryTest {

    private val plansApi: PlansApi = mockk()
    private val planDao: PlanDao = mockk()
    private val historyEntryDao: HistoryEntryDao = mockk()
    private val cleanFlowApi: CleanFlowApi = mockk()

    private val repo = PlanRepository(plansApi, planDao, historyEntryDao, cleanFlowApi)

    private fun samplePlan(): Plan = Plan(
        id = "plan-123",
        homeId = "home-1",
        date = "2026-05-11",
        mode = CleaningMode.FOCUS,
        version = 1,
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now(),
        tasks = emptyList(),
        metadata = PlanMetadata(totalEstimatedMinutes = 0, taskCount = 0)
    )

    private fun samplePlanEntity(): PlanEntity = PlanEntity(
        id = "plan-123",
        homeId = "home-1",
        date = "2026-05-11",
        mode = "FOCUS",
        version = 1,
        createdAt = Clock.System.now().toEpochMilliseconds(),
        updatedAt = Clock.System.now().toEpochMilliseconds(),
        tasksJson = "[]",
        metadataJson = "{}"
    )

    @Test
    fun `getToday returns cached plan without calling network`() = runTest {
        coEvery { planDao.getByKey("home-1", "2026-05-11", "FOCUS") } returns samplePlanEntity()

        val plan = repo.getToday("home-1", "2026-05-11", CleaningMode.FOCUS)

        assertEquals("plan-123", plan.id)
        coVerify(exactly = 0) { plansApi.generate(any()) }
    }

    @Test
    fun `getToday falls back to a local plan when cache misses and the API throws`() = runTest {
        coEvery { planDao.getByKey(any(), any(), any()) } returns null
        coEvery { plansApi.generate(any()) } throws RuntimeException("network offline")

        val plan = repo.getToday("home-1", "2026-05-11", CleaningMode.FOCUS)

        // Fallback plan has a stable shape — single seed task, identifies its mode in the id.
        assertNotNull(plan)
        assertTrue("fallback plan id encodes the mode", plan.id.contains("FOCUS"))
        assertEquals(1, plan.tasks.size)
    }

    @Test
    fun `markTaskDone writes a Done history entry and queues a MARK_DONE pending op`() = runTest {
        val entrySlot = slot<HistoryEntryEntity>()
        val opSlot = slot<PendingOpEntity>()
        coEvery { historyEntryDao.insertHistoryEntry(capture(entrySlot)) } just Runs
        coEvery { planDao.insertPendingOp(capture(opSlot)) } just Runs

        repo.markTaskDone(taskId = "t-1", deviceId = "device-A")

        assertEquals("t-1", entrySlot.captured.taskId)
        assertEquals(TaskStatus.Done, entrySlot.captured.status)
        assertEquals("device-A", entrySlot.captured.deviceId)

        assertEquals("MARK_DONE", opSlot.captured.type)
        val payload = Json.decodeFromString(CompleteTaskRequest.serializer(), opSlot.captured.payload)
        assertEquals("t-1", payload.taskId)
    }

    @Test
    fun `markTaskSkipped writes a Skipped history entry and queues a MARK_SKIP pending op`() = runTest {
        val entrySlot = slot<HistoryEntryEntity>()
        val opSlot = slot<PendingOpEntity>()
        coEvery { historyEntryDao.insertHistoryEntry(capture(entrySlot)) } just Runs
        coEvery { planDao.insertPendingOp(capture(opSlot)) } just Runs

        repo.markTaskSkipped(taskId = "t-2")

        assertEquals(TaskStatus.Skipped, entrySlot.captured.status)
        assertEquals("MARK_SKIP", opSlot.captured.type)
        val payload = Json.decodeFromString(SkipTaskRequest.serializer(), opSlot.captured.payload)
        assertEquals("t-2", payload.taskId)
    }

    @Test
    fun `assignTask queues an ASSIGN pending op with task and member`() = runTest {
        val opSlot = slot<PendingOpEntity>()
        coEvery { planDao.insertPendingOp(capture(opSlot)) } just Runs

        repo.assignTask(taskId = "t-3", memberId = "m-mom")

        assertEquals("ASSIGN", opSlot.captured.type)
        val payload = Json.decodeFromString(AssignTaskRequest.serializer(), opSlot.captured.payload)
        assertEquals("t-3", payload.taskId)
        assertEquals("m-mom", payload.memberId)
    }
}
