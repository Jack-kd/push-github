package com.jack.pushgithub.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "push_history")
data class PushHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val repoUrl: String,
    val branch: String,
    val sourcePath: String,
    val success: Boolean,
    val timestamp: Long,
    val message: String = ""
)
