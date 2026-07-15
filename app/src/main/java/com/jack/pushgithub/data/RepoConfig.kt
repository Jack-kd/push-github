package com.jack.pushgithub.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "repo_config")
data class RepoConfigEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val repoUrl: String,
    val branch: String,
    val createTime: Long
)
