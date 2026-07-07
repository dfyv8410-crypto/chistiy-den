package com.chistiyen.app.data.db.dao

import androidx.room.*
import com.chistiyen.app.data.db.entity.Note

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE book_id = :bookId ORDER BY created_at DESC")
    suspend fun getByBookId(bookId: Long): List<Note>

    @Query("SELECT * FROM notes WHERE book_id = :bookId AND chapter_id = :chapterId ORDER BY created_at DESC")
    suspend fun getByChapter(bookId: Long, chapterId: String): List<Note>

    @Insert
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

    @Delete
    suspend fun delete(note: Note)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM notes WHERE book_id = :bookId")
    suspend fun deleteByBookId(bookId: Long)
}
