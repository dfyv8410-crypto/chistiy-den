package com.chistiyen.app.data.db.dao

import androidx.room.*
import com.chistiyen.app.data.db.entity.BookSettings

@Dao
interface BookSettingsDao {
    @Query("SELECT * FROM book_settings WHERE bookId = :bookId")
    suspend fun getByBookId(bookId: Long): BookSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: BookSettings)

    @Query("DELETE FROM book_settings WHERE bookId = :bookId")
    suspend fun deleteByBookId(bookId: Long)
}
