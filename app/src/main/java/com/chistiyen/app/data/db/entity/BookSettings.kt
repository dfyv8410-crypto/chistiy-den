package com.chistiyen.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "book_settings")
data class BookSettings(
    @PrimaryKey val bookId: Long,
    @ColumnInfo(name = "font_size") var fontSize: Int = 16,
    @ColumnInfo(name = "line_height") var lineHeight: Float = 1.7f,
    @ColumnInfo(name = "theme") var theme: String = "light",
    @ColumnInfo(name = "scroll_pos") var scrollPos: Int = 0,
    @ColumnInfo(name = "current_chapter") var currentChapter: String? = null,
    @ColumnInfo(name = "current_chapter_index") var currentChapterIndex: Int = 0
)
