package com.cherin.edupsych.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Debug-only receiver that posts the daily notification immediately when triggered.
 * Trigger from a connected dev machine:
 *
 *   adb shell am broadcast -a com.cherin.edupsych.TEST_NOTIFY \
 *       -n com.cherin.edupsych/.notify.TestNotifyReceiver
 *
 * Production users never hit this — the action string is project-private and the
 * receiver is only useful with `adb`. Kept in main source to make it accessible
 * without a separate debug build variant for V1.
 */
class TestNotifyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DailyNotifier.postToday(context)
    }
}
