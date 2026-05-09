package com.cherin.edupsych.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.cherin.edupsych.MainActivity
import com.cherin.edupsych.R
import com.cherin.edupsych.data.PaperRepository

object DailyNotifier {
    const val CHANNEL_ID = "daily_paper"
    const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService<NotificationManager>() ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "오늘의 논문",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "매일 오전 9시에 오늘의 논문을 알려줍니다."
            setShowBadge(true)
        }
        mgr.createNotificationChannel(channel)
    }

    /** Build and post the notification for today's paper. Safe to call from a Worker. */
    fun postToday(context: Context) {
        ensureChannel(context)

        val prefs = context.getSharedPreferences("edupsych", Context.MODE_PRIVATE)
        val paper = PaperRepository.paperForToday(context, prefs)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = "오늘의 논문 · 인용 ${paper.citedBy} · ${paper.year}"
        val content = paper.title

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }
}
