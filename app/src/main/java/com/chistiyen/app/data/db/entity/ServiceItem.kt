package com.chistiyen.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "service_items")
data class ServiceItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "day_of_week") val dayOfWeek: Int, // 0=Sun, 1=Mon...
    @ColumnInfo(name = "time") val time: String, // "HH:mm"
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "group_name") val groupName: String = "",
    @ColumnInfo(name = "reminder_enabled") var reminderEnabled: Boolean = false,
    @ColumnInfo(name = "reminder_time") var reminderTime: String = "" // "HH:mm" or same as time
)
