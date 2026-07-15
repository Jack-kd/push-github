package com.jack.pushgithub.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RepoConfigDao {

    @Query("SELECT * FROM repo_config ORDER BY createTime DESC")
    fun getAllConfigs(): Flow<List<RepoConfigEntity>>

    @Insert
    suspend fun insert(config: RepoConfigEntity): Long

    @Update
    suspend fun update(config: RepoConfigEntity)

    @Delete
    suspend fun delete(config: RepoConfigEntity)

    @Query("SELECT * FROM repo_config WHERE id = :id")
    suspend fun getById(id: Long): RepoConfigEntity?
}
