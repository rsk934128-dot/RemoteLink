package com.example.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.database.dao.FileTransferDao
import com.example.database.dao.NotificationLogDao
import com.example.database.entity.FileTransferEntity
import com.example.database.entity.NotificationLogEntity

@Database(
    entities = [
        FileTransferEntity::class,
        NotificationLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class RemoteLinkDatabase : RoomDatabase() {

    abstract fun fileTransferDao(): FileTransferDao
    abstract fun notificationLogDao(): NotificationLogDao

    companion object {
        @Volatile
        private var INSTANCE: RemoteLinkDatabase? = null

        fun getDatabase(context: Context): RemoteLinkDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RemoteLinkDatabase::class.java,
                    "remotelink_local_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
