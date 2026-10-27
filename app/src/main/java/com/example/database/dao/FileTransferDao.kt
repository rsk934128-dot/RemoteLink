package com.example.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.database.entity.FileTransferEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FileTransferDao {

    @Query("SELECT * FROM file_transfers ORDER BY timestamp DESC")
    fun getAllTransfers(): Flow<List<FileTransferEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(transfer: FileTransferEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transfers: List<FileTransferEntity>)

    @Query("DELETE FROM file_transfers WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM file_transfers")
    suspend fun clearAll()
}
