package com.macroresearch.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CachedEventEntity::class, FollowedEventEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class MacroDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
}

