package com.chistiyen.app.data.db.dao

import androidx.room.*
import com.chistiyen.app.data.db.entity.ServiceItem

@Dao
interface ServiceItemDao {
    @Query("SELECT * FROM service_items ORDER BY day_of_week, time ASC")
    suspend fun getAll(): List<ServiceItem>

    @Query("SELECT * FROM service_items WHERE day_of_week = :day ORDER BY time ASC")
    suspend fun getByDay(day: Int): List<ServiceItem>

    @Insert
    suspend fun insert(item: ServiceItem): Long

    @Update
    suspend fun update(item: ServiceItem)

    @Delete
    suspend fun delete(item: ServiceItem)

    @Query("DELETE FROM service_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE service_items SET reminder_enabled = :enabled, reminder_time = :time WHERE id = :id")
    suspend fun updateReminder(id: Long, enabled: Boolean, time: String)

    @Query("SELECT * FROM service_items WHERE reminder_enabled = 1")
    suspend fun getWithReminders(): List<ServiceItem>
}
