package com.example.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.database.entity.NotificationLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationLogDao {

    @Query("SELECT * FROM notification_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<NotificationLogEntity>>

    @Query("SELECT * FROM notification_logs WHERE isPending = 1 ORDER BY timestamp ASC")
    fun getPendingLogs(): Flow<List<NotificationLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: NotificationLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<NotificationLogEntity>)

    @Query("UPDATE notification_logs SET isPending = 0, isMirrored = 1 WHERE id = :id")
    suspend fun markAsMirrored(id: String)

    @Query("DELETE FROM notification_logs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM notification_logs")
    suspend fun clearAll()
}
