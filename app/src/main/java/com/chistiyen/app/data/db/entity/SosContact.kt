package com.chistiyen.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sos_contacts")
data class SosContact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "phone") val phone: String,
    @ColumnInfo(name = "type") val type: String = "Другое", // Наставник, Спонсор, Доверенное лицо
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0
)
