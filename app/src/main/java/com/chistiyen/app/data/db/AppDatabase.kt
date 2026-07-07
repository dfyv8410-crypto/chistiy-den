package com.chistiyen.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.chistiyen.app.data.db.dao.*
import com.chistiyen.app.data.db.entity.*

@Database(
    entities = [
        UserSettings::class,
        PlanItem::class,
        ServiceItem::class,
        BookEntry::class,
        BookSettings::class,
        Bookmark::class,
        Note::class,
        SosContact::class,
        CravingEntry::class,
        CravingTool::class,
        CravingEvent::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun planItemDao(): PlanItemDao
    abstract fun serviceItemDao(): ServiceItemDao
    abstract fun bookEntryDao(): BookEntryDao
    abstract fun bookSettingsDao(): BookSettingsDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun noteDao(): NoteDao
    abstract fun sosContactDao(): SosContactDao
    abstract fun cravingEntryDao(): CravingEntryDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // Migration 1→2: add notify_sound_type and notify_vibrate_pattern to user_settings
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_settings ADD COLUMN notify_sound_type TEXT NOT NULL DEFAULT 'default'")
                db.execSQL("ALTER TABLE user_settings ADD COLUMN notify_vibrate_pattern TEXT NOT NULL DEFAULT 'default'")
            }
        }

        // Migration 2→3: add craving diary tables
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS craving_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, start_time INTEGER NOT NULL, end_time INTEGER, situation TEXT NOT NULL DEFAULT '', thoughts TEXT NOT NULL DEFAULT '', feelings TEXT NOT NULL DEFAULT '', feelings_other TEXT NOT NULL DEFAULT '', trigger TEXT NOT NULL DEFAULT '', summary TEXT NOT NULL DEFAULT '', is_completed INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS craving_tools (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, entry_id INTEGER NOT NULL, tool_name TEXT NOT NULL, comment TEXT NOT NULL DEFAULT '', timestamp INTEGER NOT NULL, FOREIGN KEY (entry_id) REFERENCES craving_entries(id) ON DELETE CASCADE)")
                db.execSQL("CREATE TABLE IF NOT EXISTS craving_events (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, entry_id INTEGER NOT NULL, event_type TEXT NOT NULL, label TEXT NOT NULL DEFAULT '', timestamp INTEGER NOT NULL, FOREIGN KEY (entry_id) REFERENCES craving_entries(id) ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_craving_tools_entry_id ON craving_tools(entry_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_craving_events_entry_id ON craving_events(entry_id)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chistiy_den.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
