package com.chestny.den

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Чистый день"
        val body = intent.getStringExtra("body") ?: ""
        val channel = intent.getStringExtra("channel") ?: NotificationHelper.CHANNEL_GENERAL
        NotificationHelper.showNotification(context, title, body, channel)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val nextIntent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("title", title)
            putExtra("body", body)
            putExtra("channel", channel)
            putExtra("requestCode", intent.getIntExtra("requestCode", 0))
        }
    }
}
