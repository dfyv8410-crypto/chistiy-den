package com.chistiyen.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "craving_events",
    foreignKeys = [ForeignKey(
        entity = CravingEntry::class,
        parentColumns = ["id"],
        childColumns = ["entry_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("entry_id")]
)
data class CravingEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "entry_id") val entryId: Long,
    @ColumnInfo(name = "event_type") val eventType: String,
    @ColumnInfo(name = "label") val label: String = "",
    @ColumnInfo(name = "timestamp") var timestamp: Long = System.currentTimeMillis()
)
