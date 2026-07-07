package com.chistiyen.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.chistiyen.app.ChistiyDenApp
import com.chistiyen.app.R
import java.util.Calendar

object NotificationHelper {

    private var notifId = 1000

    fun showPlanReminder(context: Context) {
        showNotification(
            context = context,
            channelId = ChistiyDenApp.CHANNEL_REMINDERS,
            title = context.getString(R.string.plan_reminder_title),
            body = "",
            id = 100
        )
    }

    fun showServiceReminder(context: Context, serviceName: String, group: String) {
        val body = if (group.isNotEmpty()) "$serviceName в $group" else serviceName
        showNotification(
            context = context,
            channelId = ChistiyDenApp.CHANNEL_ALARMS,
            title = context.getString(R.string.app_name),
            body = body,
            id = 200 + (notifId++ % 100)
        )
    }

    fun showNotification(context: Context, channelId: String, title: String, body: String, id: Int) {
        try {
            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setDefaults(
                    NotificationCompat.DEFAULT_SOUND or
                    NotificationCompat.DEFAULT_VIBRATE or
                    NotificationCompat.DEFAULT_LIGHTS
                )
                .build()

            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }

    /** Schedule an exact alarm for plan reminder */
    fun schedulePlanReminder(context: Context, timeMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.chistiyen.app.action.PLAN_REMINDER"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent
            )
        }
    }

    /** Schedule a repeating weekly alarm for a service */
    fun scheduleServiceReminder(context: Context, serviceId: Long, dayOfWeek: Int, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Calculate next occurrence
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // Find next matching day
            var currentDay = get(Calendar.DAY_OF_WEEK)
            var diff = (dayOfWeek - currentDay + 7) % 7
            if (diff == 0 && timeInMillis <= System.currentTimeMillis()) diff = 7
            add(Calendar.DAY_OF_YEAR, diff)
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.chistiyen.app.action.SERVICE_REMINDER"
            putExtra("service_id", serviceId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, serviceId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Repeat weekly
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY * 7,
            pendingIntent
        )
    }

    fun cancelPlanReminder(context: Context) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.chistiyen.app.action.PLAN_REMINDER"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
    }

    fun cancelServiceReminder(context: Context, serviceId: Long) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.chistiyen.app.action.SERVICE_REMINDER"
            putExtra("service_id", serviceId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, serviceId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
    }
}
