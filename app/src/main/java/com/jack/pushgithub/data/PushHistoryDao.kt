package com.jack.pushgithub.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PushHistoryDao {

    @Query("SELECT * FROM push_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<PushHistoryEntity>>

    @Query("SELECT * FROM push_history WHERE success = :success ORDER BY timestamp DESC")
    fun getHistoryBySuccess(success: Boolean): Flow<List<PushHistoryEntity>>

    @Insert
    suspend fun insert(history: PushHistoryEntity): Long

    @Update
    suspend fun update(history: PushHistoryEntity)

    @Delete
    suspend fun delete(history: PushHistoryEntity)

    @Query("DELETE FROM push_history")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM push_history")
    suspend fun count(): Int
}
