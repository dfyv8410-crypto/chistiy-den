package com.chistiyen.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.chistiyen.app.ChistiyDenApp
import com.chistiyen.app.R
import java.util.Calendar

object NotificationHelper {

    private var notifId = 1000

    fun getSoundUri(context: Context, soundType: String): Uri? {
        val resId = when (soundType) {
            "gentle" -> R.raw.sound_gentle
            "standard" -> R.raw.sound_standard
            "urgent" -> R.raw.sound_urgent
            else -> R.raw.sound_default
        }
        return Uri.parse("android.resource://${context.packageName}/$resId")
    }

    fun getVibratePattern(pattern: String): LongArray {
        return when (pattern) {
            "short" -> longArrayOf(0, 100, 50, 100)
            "long" -> longArrayOf(0, 400, 100, 400)
            "double" -> longArrayOf(0, 150, 100, 150, 100, 150)
            else -> longArrayOf(0, 200, 100, 200)
        }
    }

    fun showPlanReminder(context: Context, soundType: String = "default", vibratePattern: String = "default") {
        showNotification(
            context = context,
            channelId = ChistiyDenApp.CHANNEL_REMINDERS,
            title = context.getString(R.string.plan_reminder_title),
            body = "",
            id = 100,
            soundType = soundType,
            vibratePattern = vibratePattern
        )
    }

    fun showServiceReminder(context: Context, serviceName: String, group: String,
                            soundType: String = "default", vibratePattern: String = "default") {
        val body = if (group.isNotEmpty()) "$serviceName в $group" else serviceName
        showNotification(
            context = context,
            channelId = ChistiyDenApp.CHANNEL_ALARMS,
            title = context.getString(R.string.app_name),
            body = body,
            id = 200 + (notifId++ % 100),
            soundType = soundType,
            vibratePattern = vibratePattern
        )
    }

    fun showNotification(context: Context, channelId: String, title: String, body: String, id: Int,
                         soundType: String = "default", vibratePattern: String = "default") {
        try {
            val soundUri = getSoundUri(context, soundType)
            val vibrate = getVibratePattern(vibratePattern)

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))

            if (soundUri != null) {
                builder.setSound(soundUri)
            }
            if (vibrate.isNotEmpty()) {
                builder.setVibrate(vibrate)
            }

            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }

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
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
        }
    }

    fun scheduleServiceReminder(context: Context, serviceId: Long, dayOfWeek: Int, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
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
