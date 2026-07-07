package com.chistiyen.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.chistiyen.app.action.PLAN_REMINDER" -> {
                NotificationHelper.showPlanReminder(context)
                // Re-schedule for tomorrow
                val dao = com.chistiyen.app.data.db.AppDatabase
                    .getInstance(context).userSettingsDao()
                CoroutineScope(Dispatchers.IO).launch {
                    val settings = dao.get()
                    if (settings?.planReminderTime != null) {
                        val parts = settings.planReminderTime.split(":")
                        if (parts.size == 2) {
                            val h = parts[0].toIntOrNull() ?: return@launch
                            val m = parts[1].toIntOrNull() ?: return@launch
                            val cal = java.util.Calendar.getInstance().apply {
                                set(java.util.Calendar.HOUR_OF_DAY, h)
                                set(java.util.Calendar.MINUTE, m)
                                set(java.util.Calendar.SECOND, 0)
                                set(java.util.Calendar.MILLISECOND, 0)
                                add(java.util.Calendar.DAY_OF_YEAR, 1)
                            }
                            NotificationHelper.schedulePlanReminder(context, cal.timeInMillis)
                        }
                    }
                }
            }

            "com.chistiyen.app.action.SERVICE_REMINDER" -> {
                val serviceName = intent.getStringExtra("service_name") ?: ""
                val serviceGroup = intent.getStringExtra("service_group") ?: ""
                NotificationHelper.showServiceReminder(context, serviceName, serviceGroup)
            }
        }
    }
}
