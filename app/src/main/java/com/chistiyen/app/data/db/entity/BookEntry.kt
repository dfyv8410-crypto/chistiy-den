package com.chistiyen.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "book_entries")
data class BookEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "author") val author: String = "",
    @ColumnInfo(name = "format") val format: String = "", // fb2, epub, pdf, txt, docx, rtf
    @ColumnInfo(name = "file_path") val filePath: String = "", // internal storage path
    @ColumnInfo(name = "icon") val icon: String = "\uD83D\uDCD6",
    @ColumnInfo(name = "color") val color: String = "#4A7FB5",
    @ColumnInfo(name = "imported_at") val importedAt: Long = System.currentTimeMillis()
)
