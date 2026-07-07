package com.chistiyen.app.data.db.dao

import androidx.room.*
import com.chistiyen.app.data.db.entity.SosContact

@Dao
interface SosContactDao {
    @Query("SELECT * FROM sos_contacts ORDER BY sort_order ASC, id ASC")
    suspend fun getAll(): List<SosContact>

    @Insert
    suspend fun insert(contact: SosContact): Long

    @Update
    suspend fun update(contact: SosContact)

    @Delete
    suspend fun delete(contact: SosContact)

    @Query("DELETE FROM sos_contacts WHERE id = :id")
    suspend fun deleteById(id: Long)
}
