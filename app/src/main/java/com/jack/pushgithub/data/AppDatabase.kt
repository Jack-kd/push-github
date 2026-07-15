package com.jack.pushgithub.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PushHistoryEntity::class, RepoConfigEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pushHistoryDao(): PushHistoryDao
    abstract fun repoConfigDao(): RepoConfigDao
}
