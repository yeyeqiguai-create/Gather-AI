package com.tim.futureai.platform.schedule

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tim.futureai.domain.health.HealthTrigger
import java.time.Duration

fun interface PeriodicHealthRunner {
    suspend operator fun invoke(trigger: HealthTrigger)
}

/**
 * WorkManager-backed periodic health checks. Android does not guarantee exact
 * timing under Doze/battery optimization; the UI states this limitation
 * explicitly instead of promising precise schedules.
 */
class PeriodicHealthWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val runner = HealthWorkScheduler.PeriodicHealthRunnerProvider.resolve?.invoke(applicationContext)
            ?: return Result.retry()
        return runCatching { runner(HealthTrigger.PERIODIC) }.fold({ Result.success() }, { Result.retry() })
    }
}

object HealthWorkScheduler {
    fun schedule(context: Context, interval: Duration = Duration.ofHours(6)) {
        val request = PeriodicWorkRequestBuilder<PeriodicHealthWorker>(interval)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(10))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "gather-periodic-health", ExistingPeriodicWorkPolicy.KEEP, request,
        )
    }

    /** Registered by the application at startup so the worker can reach the engine. */
    object PeriodicHealthRunnerProvider {
        @Volatile var resolve: ((Context) -> PeriodicHealthRunner?)? = null
    }
}
