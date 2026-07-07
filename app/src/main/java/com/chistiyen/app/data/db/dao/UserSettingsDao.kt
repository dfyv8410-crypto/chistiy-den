package com.chistiyen.app.data.db.dao

import androidx.room.*
import com.chistiyen.app.data.db.entity.UserSettings

@Dao
interface UserSettingsDao {
    @Query("SELECT * FROM user_settings WHERE id = 1")
    suspend fun get(): UserSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: UserSettings)

    @Query("UPDATE user_settings SET start_date = :date WHERE id = 1")
    suspend fun updateStartDate(date: String)

    @Query("UPDATE user_settings SET theme = :theme WHERE id = 1")
    suspend fun updateTheme(theme: String)

    @Query("UPDATE user_settings SET notify_vibrate = :vib WHERE id = 1")
    suspend fun updateVibrate(vib: Boolean)

    @Query("UPDATE user_settings SET notify_sound = :snd WHERE id = 1")
    suspend fun updateSound(snd: Boolean)

    @Query("UPDATE user_settings SET plan_reminder_time = :time WHERE id = 1")
    suspend fun updatePlanReminderTime(time: String?)

    @Query("UPDATE user_settings SET launch_count = launch_count + 1 WHERE id = 1")
    suspend fun incrementLaunchCount()

    @Query("UPDATE user_settings SET last_donate_prompt = :ts WHERE id = 1")
    suspend fun updateDonatePrompt(ts: Long)

    @Query("UPDATE user_settings SET jft_cache = :html, jft_cache_date = :date WHERE id = 1")
    suspend fun updateJftCache(html: String?, date: String?)
}
