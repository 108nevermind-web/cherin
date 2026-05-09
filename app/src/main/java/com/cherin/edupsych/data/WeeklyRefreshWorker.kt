package com.cherin.edupsych.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration

/**
 * Once a week, ask [RemotePaperFetcher] whether the published papers.json
 * has changed. If REMOTE_URL is unset (the default), refresh() is a silent
 * no-op so the worker stays cheap. WorkManager throttles & retries on its
 * own — we just need network connectivity.
 */
class WeeklyRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        RemotePaperFetcher.refresh(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "papers_weekly_refresh"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<WeeklyRefreshWorker>(Duration.ofDays(7))
                .setConstraints(constraints)
                .setInitialDelay(Duration.ofHours(1))  // give the app time to settle
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
