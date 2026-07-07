package com.chistiyen.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class ChistiyDenApp : Application() {
    companion object {
        const val CHANNEL_REMINDERS = "reminders"
        const val CHANNEL_ALARMS = "alarms"
        lateinit var instance: ChistiyDenApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // Reminders channel — no default sound, sound set per-notification
            val reminders = NotificationChannel(
                CHANNEL_REMINDERS,
                getString(R.string.notification_channel_reminders),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_reminders_desc)
                enableVibration(true)
                enableLights(true)
            }
            manager.createNotificationChannel(reminders)

            // Alarms channel — no default sound, sound set per-notification
            val alarms = NotificationChannel(
                CHANNEL_ALARMS,
                getString(R.string.notification_channel_alarms),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_alarms_desc)
                enableVibration(true)
                enableLights(true)
                setBypassDnd(true)
            }
            manager.createNotificationChannel(alarms)
        }
    }
}
