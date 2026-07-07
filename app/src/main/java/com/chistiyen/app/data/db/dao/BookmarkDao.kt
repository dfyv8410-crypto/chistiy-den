package com.chistiyen.app.data.db.dao

import androidx.room.*
import com.chistiyen.app.data.db.entity.Bookmark

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE book_id = :bookId ORDER BY created_at DESC")
    suspend fun getByBookId(bookId: Long): List<Bookmark>

    @Query("SELECT * FROM bookmarks WHERE book_id = :bookId AND chapter_id = :chapterId LIMIT 1")
    suspend fun find(bookId: Long, chapterId: String): Bookmark?

    @Insert
    suspend fun insert(bookmark: Bookmark): Long

    @Delete
    suspend fun delete(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM bookmarks WHERE book_id = :bookId")
    suspend fun deleteByBookId(bookId: Long)
}
