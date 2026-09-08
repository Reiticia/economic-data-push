package com.macroresearch.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CachedEventEntity::class, FollowedEventEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class MacroDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cached_event ADD COLUMN eventZhCn TEXT")
                db.execSQL("ALTER TABLE cached_event ADD COLUMN eventZhTw TEXT")
            }
        }
    }
}
