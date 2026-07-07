package com.chistiyen.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_settings")
data class UserSettings(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "start_date") var startDate: String? = null,
    @ColumnInfo(name = "theme") var theme: String = "light",
    @ColumnInfo(name = "notify_vibrate") var notifyVibrate: Boolean = true,
    @ColumnInfo(name = "notify_sound") var notifySound: Boolean = true,
    @ColumnInfo(name = "plan_reminder_time") var planReminderTime: String? = null,
    @ColumnInfo(name = "launch_count") var launchCount: Int = 0,
    @ColumnInfo(name = "last_donate_prompt") var lastDonatePrompt: Long = 0L,
    @ColumnInfo(name = "jft_cache") var jftCache: String? = null,
    @ColumnInfo(name = "jft_cache_date") var jftCacheDate: String? = null,
    @ColumnInfo(name = "notify_sound_type") var notifySoundType: String = "default",
    @ColumnInfo(name = "notify_vibrate_pattern") var notifyVibratePattern: String = "default"
)
