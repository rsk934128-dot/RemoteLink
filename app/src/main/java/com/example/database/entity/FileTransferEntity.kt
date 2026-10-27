package com.example.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "file_transfers")
data class FileTransferEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val transferredBytes: Long,
    val isIncoming: Boolean,
    val status: String,
    val progress: Float,
    val speedBps: Long,
    val localUriString: String?,
    val localFilePath: String?,
    val errorMsg: String?,
    val timestamp: Long
)
