package com.chistiyen.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Restores all scheduled alarms after device reboot.
 * This is the PWA-incompatible feature — native Android only.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            CoroutineScope(Dispatchers.IO).launch {
                val db = com.chistiyen.app.data.db.AppDatabase.getInstance(context)

                // Restore plan reminder
                val settings = db.userSettingsDao().get()
                if (settings?.planReminderTime != null) {
                    val parts = settings.planReminderTime.split(":")
                    if (parts.size == 2) {
                        val h = parts[0].toIntOrNull()
                        val m = parts[1].toIntOrNull()
                        if (h != null && m != null) {
                            val cal = java.util.Calendar.getInstance().apply {
                                set(java.util.Calendar.HOUR_OF_DAY, h)
                                set(java.util.Calendar.MINUTE, m)
                                set(java.util.Calendar.SECOND, 0)
                                set(java.util.Calendar.MILLISECOND, 0)
                                if (timeInMillis <= System.currentTimeMillis()) {
                                    add(java.util.Calendar.DAY_OF_YEAR, 1)
                                }
                            }
                            NotificationHelper.schedulePlanReminder(context, cal.timeInMillis)
                        }
                    }
                }

                // Restore all service reminders
                val services = db.serviceItemDao().getWithReminders()
                services.forEach { service ->
                    if (service.reminderEnabled && service.reminderTime.isNotEmpty()) {
                        val parts = service.reminderTime.split(":")
                        if (parts.size == 2) {
                            val h = parts[0].toIntOrNull()
                            val m = parts[1].toIntOrNull()
                            if (h != null && m != null) {
                                NotificationHelper.scheduleServiceReminder(
                                    context, service.id,
                                    service.dayOfWeek, h, m
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
