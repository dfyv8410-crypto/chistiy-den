package com.chistiyen.app.data.db.dao

import androidx.room.*
import com.chistiyen.app.data.db.entity.PlanItem

@Dao
interface PlanItemDao {
    @Query("SELECT * FROM plan_items WHERE date_key = :dateKey ORDER BY created_at ASC")
    suspend fun getByDate(dateKey: String): List<PlanItem>

    @Query("SELECT * FROM plan_items ORDER BY created_at DESC")
    suspend fun getAll(): List<PlanItem>

    @Insert
    suspend fun insert(item: PlanItem): Long

    @Update
    suspend fun update(item: PlanItem)

    @Query("UPDATE plan_items SET done = :done WHERE id = :id")
    suspend fun toggleDone(id: Long, done: Boolean)

    @Delete
    suspend fun delete(item: PlanItem)

    @Query("DELETE FROM plan_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM plan_items WHERE date_key = :dateKey")
    suspend fun deleteByDate(dateKey: String)
}
