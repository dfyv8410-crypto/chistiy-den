package com.chestny.den

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log

object NotificationHelper {
    private const val TAG = "NotificationHelper"
    private const val PREFS_NAME = "notifications"
    private const val CHANNEL_GENERAL = "general"
    private const val CHANNEL_REMINDER = "reminder"
    private const val CHANNEL_CRISIS = "crisis"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channels = listOf(
            android.app.NotificationChannel(
                CHANNEL_GENERAL,
                context.getString(R.string.notification_channel_general),
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            ),
            android.app.NotificationChannel(
                CHANNEL_REMINDER,
                context.getString(R.string.notification_channel_reminder),
                android.app.NotificationManager.IMPORTANCE_HIGH
            ),
            android.app.NotificationChannel(
                CHANNEL_CRISIS,
                context.getString(R.string.notification_channel_crisis),
                android.app.NotificationManager.IMPORTANCE_HIGH
            )
        )
        channels.forEach { manager.createNotificationChannel(it) }
    }

    fun scheduleReminder(context: Context, hour: Int, minute: Int, title: String, body: String, requestCode: Int) {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("title", title)
            putExtra("body", body)
            putExtra("channel", CHANNEL_REMINDER)
        }
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context, requestCode, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            if (before(java.util.Calendar.getInstance())) {
                add(java.util.Calendar.DAY_OF_MONTH, 1)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                android.app.AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                android.app.AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }

    fun showNotification(context: Context, title: String, body: String, channel: String = CHANNEL_GENERAL) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val notification = android.app.Notification.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(android.app.Notification.PRIORITY_HIGH)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification.setChannelId(channel)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val id = System.currentTimeMillis().toInt()
        manager.notify(id, notification.build())
    }
}
