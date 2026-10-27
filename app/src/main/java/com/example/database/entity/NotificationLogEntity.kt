package com.example.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notification_logs")
data class NotificationLogEntity(
    @PrimaryKey
    val id: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val timestamp: Long,
    val isMirrored: Boolean,
    val isPending: Boolean
)
