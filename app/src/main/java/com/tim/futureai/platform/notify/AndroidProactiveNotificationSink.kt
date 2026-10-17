package com.tim.futureai.platform.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.tim.futureai.domain.proactive.ProactiveEvent
import com.tim.futureai.domain.proactive.ProactiveNotificationSink

/**
 * Posts proactive events as system notifications. When permission is missing
 * or posting fails it returns false so the engine records a degraded, in-app
 * only event instead of pretending the user was notified.
 */
class AndroidProactiveNotificationSink(private val context: Context) : ProactiveNotificationSink {
    override suspend fun notify(event: ProactiveEvent): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "主动提醒", when (event.severity) {
                    3 -> NotificationManager.IMPORTANCE_HIGH
                    2 -> NotificationManager.IMPORTANCE_DEFAULT
                    else -> NotificationManager.IMPORTANCE_LOW
                }).apply { description = "Gather AI 健康检查、任务与更新提醒" },
            )
        }
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pending = PendingIntent.getActivity(
            context, event.eventId.hashCode(),
            Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error.takeIf { event.severity >= 2 } ?: android.R.drawable.ic_dialog_info)
            .setContentTitle(event.title)
            .setContentText(event.detail.take(160))
            .setStyle(NotificationCompat.BigTextStyle().bigText(event.detail.take(500)))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        return try {
            manager.notify(event.eventId.hashCode(), notification)
            true
        } catch (error: Exception) {
            false
        }
    }

    fun notificationsEnabled(): Boolean =
        Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private companion object { const val CHANNEL_ID = "gather_proactive" }
}
