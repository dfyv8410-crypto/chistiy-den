package com.chistiyen.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chistiy_den.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
