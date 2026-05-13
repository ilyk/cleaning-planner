package com.ilyk.cleaningplanner.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ilyk.cleaningplanner.data.repository.PlanRepository
import com.ilyk.cleaningplanner.data.repository.UserProfileRepository
import com.ilyk.cleaningplanner.domain.engine.PlanEngine
import com.ilyk.cleaningplanner.domain.model.CleaningMode
import com.ilyk.cleaningplanner.domain.model.Plan
import com.ilyk.cleaningplanner.domain.model.PlanMetadata
import com.ilyk.cleaningplanner.state.PrefsStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Periodic worker that rebuilds the day's plan at ~06:00 local time.
 *
 * The flow:
 *  1. Read the current home id, mode, and a minimal UserProfile (from PrefsStore).
 *  2. Call [PlanRepository.getToday] to ensure a cached plan exists (network or fallback).
 *  3. Run [PlanEngine.generateDailyPlan] for a fresh local synthesis.
 *  4. Write the engine output back through [PlanRepository.cacheLocalPlan] so the UI
 *     sees the regenerated plan on next read.
 *
 * No notification is fired today — adding one means picking an icon and a deep-link
 * target; that is intentionally deferred to a follow-up W6.x once Home is finalized.
 */
@HiltWorker
class DailyPlanWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val prefsStore: PrefsStore,
    private val planRepository: PlanRepository,
    private val planEngine: PlanEngine,
    private val userProfileRepository: UserProfileRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val homeId = prefsStore.homeIdFlow.first() ?: return Result.success()
            val modeName = prefsStore.currentModeFlow.first()
            val mode = runCatching { CleaningMode.valueOf(modeName) }.getOrDefault(CleaningMode.FOCUS)

            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val todayIso = today.toString()

            // Ensure a plan exists in cache (cheap if one is already there).
            val cached = planRepository.getToday(homeId, todayIso, mode)

            // Real UserProfile from the welcome flow (or repo default if onboarding hasn't
            // populated it yet). Honors rooms / hasPets / preference so PlanEngine produces
            // user-tailored output instead of a one-size-fits-all hardcoded shape.
            val profile = userProfileRepository.userProfile.first()
            val engineTasks = planEngine.generateDailyPlan(profile, emptyList(), mode, today)

            // Merge: prefer cached plan id/version when present, otherwise build fresh.
            val merged = Plan(
                id = cached.id,
                homeId = cached.homeId,
                date = cached.date,
                mode = cached.mode,
                version = cached.version + 1,
                createdAt = cached.createdAt,
                updatedAt = Clock.System.now(),
                tasks = engineTasks,
                metadata = PlanMetadata(
                    totalEstimatedMinutes = engineTasks.sumOf { it.estimatedDurationMinutes },
                    taskCount = engineTasks.size
                )
            )
            planRepository.cacheLocalPlan(merged)
            Result.success()
        } catch (t: Throwable) {
            // Transient failures (network, etc.) retry. Permanent ones still log as success
            // so we don't spin forever; the next periodic tick will retry naturally.
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "cleanflow.daily-plan"

        /**
         * Schedules a daily run aligned to ~06:00 local. Safe to call repeatedly —
         * [ExistingPeriodicWorkPolicy.KEEP] is a no-op when already scheduled.
         */
        fun schedule(workManager: WorkManager) {
            val initialDelayMinutes = minutesUntilNextTargetHour(targetHour = 6)
            val request = PeriodicWorkRequestBuilder<DailyPlanWorker>(
                repeatInterval = 24, repeatIntervalTimeUnit = TimeUnit.HOURS,
                flexTimeInterval = 1, flexTimeIntervalUnit = TimeUnit.HOURS
            )
                .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .build()
            workManager.enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        private fun minutesUntilNextTargetHour(targetHour: Int): Long {
            val now = Calendar.getInstance()
            val next = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, targetHour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
            }
            return ((next.timeInMillis - now.timeInMillis) / 60_000L).coerceAtLeast(1L)
        }
    }
}
