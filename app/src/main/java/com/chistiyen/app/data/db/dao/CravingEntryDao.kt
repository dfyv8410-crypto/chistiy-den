package com.chistiyen.app.data.db.dao

import androidx.room.*
import com.chistiyen.app.data.db.entity.CravingEntry
import com.chistiyen.app.data.db.entity.CravingEvent
import com.chistiyen.app.data.db.entity.CravingTool

@Dao
interface CravingEntryDao {
    @Query("SELECT * FROM craving_entries ORDER BY created_at DESC")
    suspend fun getAll(): List<CravingEntry>

    @Query("SELECT * FROM craving_entries WHERE id = :id")
    suspend fun getById(id: Long): CravingEntry?

    @Query("SELECT * FROM craving_entries WHERE is_completed = 0 ORDER BY created_at DESC LIMIT 1")
    suspend fun getUnfinished(): CravingEntry?

    @Query("SELECT * FROM craving_entries WHERE situation LIKE '%' || :q || '%' OR thoughts LIKE '%' || :q || '%' OR feelings LIKE '%' || :q || '%' OR trigger LIKE '%' || :q || '%' OR summary LIKE '%' || :q || '%' ORDER BY created_at DESC")
    suspend fun search(q: String): List<CravingEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CravingEntry): Long

    @Update
    suspend fun update(entry: CravingEntry)

    @Delete
    suspend fun delete(entry: CravingEntry)

    @Query("DELETE FROM craving_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    // Events
    @Insert
    suspend fun insertEvent(event: CravingEvent): Long

    @Query("SELECT * FROM craving_events WHERE entry_id = :entryId ORDER BY timestamp ASC")
    suspend fun getEvents(entryId: Long): List<CravingEvent>

    @Query("DELETE FROM craving_events WHERE entry_id = :entryId")
    suspend fun deleteEvents(entryId: Long)

    // Tools
    @Insert
    suspend fun insertTool(tool: CravingTool): Long

    @Query("SELECT * FROM craving_tools WHERE entry_id = :entryId ORDER BY timestamp ASC")
    suspend fun getTools(entryId: Long): List<CravingTool>

    @Query("DELETE FROM craving_tools WHERE entry_id = :entryId")
    suspend fun deleteTools(entryId: Long)

    @Query("DELETE FROM craving_tools WHERE id = :id")
    suspend fun deleteToolById(id: Long)

    // Stats
    @Query("SELECT COUNT(*) FROM craving_entries WHERE is_completed = 1")
    suspend fun countCompleted(): Int

    @Query("SELECT AVG((end_time - start_time) / 60000.0) FROM craving_entries WHERE is_completed = 1 AND end_time IS NOT NULL")
    suspend fun avgDurationMinutes(): Double?

    @Query("SELECT MAX((end_time - start_time) / 60000.0) FROM craving_entries WHERE is_completed = 1 AND end_time IS NOT NULL")
    suspend fun maxDurationMinutes(): Double?

    @Query("SELECT MIN((end_time - start_time) / 60000.0) FROM craving_entries WHERE is_completed = 1 AND end_time IS NOT NULL")
    suspend fun minDurationMinutes(): Double?

    @Query("SELECT tool_name, COUNT(*) AS cnt FROM craving_tools GROUP BY tool_name ORDER BY cnt DESC")
    suspend fun toolStats(): List<ToolStat>

    @Query("SELECT * FROM craving_entries WHERE is_completed = 1 AND created_at >= :since ORDER BY created_at DESC")
    suspend fun getSince(since: Long): List<CravingEntry>

    @Query("SELECT CAST(strftime('%H', created_at / 1000, 'unixepoch') AS INTEGER) AS hour, COUNT(*) AS cnt FROM craving_entries GROUP BY hour ORDER BY cnt DESC")
    suspend fun hourlyStats(): List<HourlyStat>
}

data class ToolStat(val tool_name: String, val cnt: Int)
data class HourlyStat(val hour: Int, val cnt: Int)
