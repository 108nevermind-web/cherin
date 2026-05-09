package com.cherin.edupsych.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Posts the "오늘의 논문" notification at the user's chosen time, every day.
 * Re-enqueues automatically via PeriodicWorkRequest. Default 09:00.
 *
 * Time persists in SharedPreferences (`notify_hour`, `notify_minute`); call
 * [reschedule] after the user picks a new time to update the alarm.
 */
class DailyNotificationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        DailyNotifier.postToday(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "daily_paper_notification"
        private const val PREFS = "edupsych"
        private const val KEY_HOUR = "notify_hour"
        private const val KEY_MINUTE = "notify_minute"
        private const val KEY_ENABLED = "notify_enabled"
        const val DEFAULT_HOUR = 9
        const val DEFAULT_MINUTE = 0
        const val DEFAULT_ENABLED = true

        fun savedHour(context: Context): Int =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_HOUR, DEFAULT_HOUR)

        fun savedMinute(context: Context): Int =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_MINUTE, DEFAULT_MINUTE)

        fun savedEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, DEFAULT_ENABLED)

        /**
         * First-run scheduling — re-applies the user's saved time + enabled
         * state. If disabled, cancels any pending work instead of enqueuing.
         */
        fun schedule(context: Context) {
            if (!savedEnabled(context)) {
                WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
                return
            }
            scheduleAt(
                context,
                savedHour(context),
                savedMinute(context),
                policy = ExistingPeriodicWorkPolicy.UPDATE,
            )
        }

        /**
         * User picked a new time: persist + re-enqueue with UPDATE so the
         * pending work item is cancelled and the new schedule takes effect.
         * Also flips enabled=true so picking a time implicitly turns it on.
         */
        fun reschedule(context: Context, hour: Int, minute: Int) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_HOUR, hour)
                .putInt(KEY_MINUTE, minute)
                .putBoolean(KEY_ENABLED, true)
                .apply()
            scheduleAt(context, hour, minute, policy = ExistingPeriodicWorkPolicy.UPDATE)
        }

        /** Toggle daily notifications. Persists + applies immediately. */
        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, enabled)
                .apply()
            if (enabled) {
                scheduleAt(
                    context,
                    savedHour(context),
                    savedMinute(context),
                    policy = ExistingPeriodicWorkPolicy.UPDATE,
                )
            } else {
                WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
            }
        }

        private fun scheduleAt(
            context: Context,
            hour: Int,
            minute: Int,
            policy: ExistingPeriodicWorkPolicy,
        ) {
            DailyNotifier.ensureChannel(context)

            val now = LocalDateTime.now(ZoneId.systemDefault())
            val notifyAt = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
            val target = LocalDate.now().atTime(notifyAt).let {
                if (it.isAfter(now)) it else it.plusDays(1)
            }
            val delay = Duration.between(now, target)

            val request = PeriodicWorkRequestBuilder<DailyNotificationWorker>(Duration.ofDays(1))
                .setInitialDelay(delay)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                policy,
                request,
            )
        }
    }
}
