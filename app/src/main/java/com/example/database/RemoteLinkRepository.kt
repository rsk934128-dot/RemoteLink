package com.example.database

import android.net.Uri
import com.example.database.dao.FileTransferDao
import com.example.database.dao.NotificationLogDao
import com.example.database.entity.FileTransferEntity
import com.example.database.entity.NotificationLogEntity
import com.example.model.FileTransferItem
import com.example.model.MirroredNotificationItem
import com.example.model.TransferStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RemoteLinkRepository(
    private val fileTransferDao: FileTransferDao,
    private val notificationLogDao: NotificationLogDao
) {

    val allTransfers: Flow<List<FileTransferItem>> = fileTransferDao.getAllTransfers().map { entities ->
        entities.map { entity -> entity.toDomainModel() }
    }

    val allNotifications: Flow<List<MirroredNotificationItem>> = notificationLogDao.getAllLogs().map { entities ->
        entities.map { entity -> entity.toDomainModel() }
    }

    val pendingNotifications: Flow<List<MirroredNotificationItem>> = notificationLogDao.getPendingLogs().map { entities ->
        entities.map { entity -> entity.toDomainModel() }
    }

    suspend fun saveTransfer(item: FileTransferItem) {
        fileTransferDao.insertOrUpdate(item.toEntity())
    }

    suspend fun deleteTransfer(id: String) {
        fileTransferDao.deleteById(id)
    }

    suspend fun clearTransfers() {
        fileTransferDao.clearAll()
    }

    suspend fun saveNotification(
        item: MirroredNotificationItem,
        isMirrored: Boolean,
        isPending: Boolean
    ) {
        notificationLogDao.insertLog(
            NotificationLogEntity(
                id = item.id,
                packageName = item.packageName,
                appName = item.appName,
                title = item.title,
                text = item.text,
                timestamp = item.timestamp,
                isMirrored = isMirrored,
                isPending = isPending
            )
        )
    }

    suspend fun markNotificationMirrored(id: String) {
        notificationLogDao.markAsMirrored(id)
    }

    suspend fun deleteNotification(id: String) {
        notificationLogDao.deleteById(id)
    }

    suspend fun clearNotifications() {
        notificationLogDao.clearAll()
    }
}

fun FileTransferEntity.toDomainModel(): FileTransferItem {
    return FileTransferItem(
        id = id,
        name = name,
        sizeBytes = sizeBytes,
        transferredBytes = transferredBytes,
        isIncoming = isIncoming,
        status = try {
            TransferStatus.valueOf(status)
        } catch (_: Exception) {
            TransferStatus.COMPLETED
        },
        progress = progress,
        speedBps = speedBps,
        localUri = localUriString?.let { Uri.parse(it) },
        localFilePath = localFilePath,
        errorMsg = errorMsg,
        timestamp = timestamp
    )
}

fun FileTransferItem.toEntity(): FileTransferEntity {
    return FileTransferEntity(
        id = id,
        name = name,
        sizeBytes = sizeBytes,
        transferredBytes = transferredBytes,
        isIncoming = isIncoming,
        status = status.name,
        progress = progress,
        speedBps = speedBps,
        localUriString = localUri?.toString(),
        localFilePath = localFilePath,
        errorMsg = errorMsg,
        timestamp = timestamp
    )
}

fun NotificationLogEntity.toDomainModel(): MirroredNotificationItem {
    return MirroredNotificationItem(
        id = id,
        packageName = packageName,
        appName = appName,
        title = title,
        text = text,
        timestamp = timestamp,
        isOngoing = false
    )
}
