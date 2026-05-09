package com.cherin.edupsych.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Refreshes the widget at local midnight so that "today's paper" rolls over.
 * Re-enqueues itself each run; first enqueue is scheduled by MainActivity.
 */
class WidgetUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        TodayWidget().updateAll(applicationContext)
        FavoritesWidget().updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "widget_daily_refresh"

        fun schedule(context: Context) {
            val now = LocalDateTime.now(ZoneId.systemDefault())
            val nextMidnight = now.toLocalDate().plusDays(1).atTime(LocalTime.MIN)
            val delay = Duration.between(now, nextMidnight)

            val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(Duration.ofDays(1))
                .setInitialDelay(delay)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
