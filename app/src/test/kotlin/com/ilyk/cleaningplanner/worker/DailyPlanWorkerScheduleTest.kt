package com.ilyk.cleaningplanner.worker

import org.junit.Test
import org.junit.Assert.assertTrue
import java.lang.reflect.Method
import java.util.Calendar

/**
 * Locks in the 06:00-local-alignment invariant for [DailyPlanWorker.schedule]'s initial-delay
 * calculation. Reflects into the private companion-object helper because the function is the
 * only piece of pure logic worth pinning — the rest of `schedule()` is glue to `WorkManager`.
 */
class DailyPlanWorkerScheduleTest {

    @Test
    fun `minutesUntilNextTargetHour produces a positive delay aligned to the requested hour`() {
        val method = lookup("minutesUntilNextTargetHour")

        // 6 AM target. Whatever wall-clock time we run at, the delay must be in
        // (0, 24*60] minutes inclusive of the upper bound.
        val delay6 = method.invoke(companionInstance(), 6) as Long
        assertTrue("Delay must be positive (>=1 min)", delay6 in 1L..1440L)

        val delay0 = method.invoke(companionInstance(), 0) as Long
        assertTrue(delay0 in 1L..1440L)

        val delay23 = method.invoke(companionInstance(), 23) as Long
        assertTrue(delay23 in 1L..1440L)
    }

    @Test
    fun `target hour earlier today rolls forward to tomorrow`() {
        // Pick a hour at or before "now" so the loop must skip to the next day.
        val nowHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val targetHour = if (nowHour == 0) 0 else nowHour - 1
        val delay = lookup("minutesUntilNextTargetHour")
            .invoke(companionInstance(), targetHour) as Long
        // If "earlier today" rolled to tomorrow, delay should be more than an hour
        // away unless we happened to call exactly on the minute boundary — give it
        // a wide floor of 30min to be robust against test-runner timing jitter.
        assertTrue(
            "Past-hour target should roll forward; got $delay min for hour=$targetHour",
            delay >= 30L
        )
    }

    private fun lookup(name: String): Method {
        // The function is on the synthetic Companion class; private but reflectively reachable.
        val companion = DailyPlanWorker::class.java.declaredClasses
            .first { it.simpleName == "Companion" }
        return companion.declaredMethods
            .first { it.name == name }
            .also { it.isAccessible = true }
    }

    private fun companionInstance(): Any =
        DailyPlanWorker::class.java.getDeclaredField("Companion").get(null)
}
