package com.chistiyen.app.data.db.dao

import androidx.room.*
import com.chistiyen.app.data.db.entity.BookEntry

@Dao
interface BookEntryDao {
    @Query("SELECT * FROM book_entries ORDER BY imported_at DESC")
    suspend fun getAll(): List<BookEntry>

    @Query("SELECT * FROM book_entries WHERE id = :id")
    suspend fun getById(id: Long): BookEntry?

    @Insert
    suspend fun insert(book: BookEntry): Long

    @Update
    suspend fun update(book: BookEntry)

    @Delete
    suspend fun delete(book: BookEntry)

    @Query("DELETE FROM book_entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}
