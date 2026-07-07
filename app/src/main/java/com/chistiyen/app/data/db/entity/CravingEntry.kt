package com.chistiyen.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "craving_entries")
data class CravingEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "start_time") var startTime: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "end_time") var endTime: Long? = null,
    @ColumnInfo(name = "situation") var situation: String = "",
    @ColumnInfo(name = "thoughts") var thoughts: String = "",
    @ColumnInfo(name = "feelings") var feelings: String = "",
    @ColumnInfo(name = "feelings_other") var feelingsOther: String = "",
    @ColumnInfo(name = "trigger") var trigger: String = "",
    @ColumnInfo(name = "summary") var summary: String = "",
    @ColumnInfo(name = "is_completed") var isCompleted: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") var updatedAt: Long = System.currentTimeMillis()
)
