package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.database.RemoteLinkDatabase
import com.example.database.entity.FileTransferEntity
import com.example.database.entity.NotificationLogEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var db: RemoteLinkDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RemoteLinkDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("RemoteLink", appName)
    }

    @Test
    fun `insert and retrieve file transfer from Room`() = runBlocking {
        val transferDao = db.fileTransferDao()
        val item = FileTransferEntity(
            id = "tf-101",
            name = "report.pdf",
            sizeBytes = 204800L,
            transferredBytes = 204800L,
            isIncoming = true,
            status = "COMPLETED",
            progress = 1.0f,
            speedBps = 50000L,
            localUriString = "content://downloads/report.pdf",
            localFilePath = "/storage/emulated/0/Download/report.pdf",
            errorMsg = null,
            timestamp = System.currentTimeMillis()
        )

        transferDao.insertOrUpdate(item)

        val transfers = transferDao.getAllTransfers().first()
        assertEquals(1, transfers.size)
        assertEquals("report.pdf", transfers[0].name)
        assertEquals(204800L, transfers[0].sizeBytes)
        assertEquals("COMPLETED", transfers[0].status)
    }

    @Test
    fun `insert and mark notification log as mirrored in Room`() = runBlocking {
        val notifDao = db.notificationLogDao()
        val notif = NotificationLogEntity(
            id = "notif-001",
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            title = "Team Lead",
            text = "Meeting starts at 3 PM",
            timestamp = System.currentTimeMillis(),
            isMirrored = false,
            isPending = true
        )

        notifDao.insertLog(notif)

        val pending = notifDao.getPendingLogs().first()
        assertEquals(1, pending.size)
        assertTrue(pending[0].isPending)

        // Mark as mirrored
        notifDao.markAsMirrored("notif-001")

        val allLogs = notifDao.getAllLogs().first()
        assertEquals(1, allLogs.size)
        assertTrue(allLogs[0].isMirrored)

        val pendingAfter = notifDao.getPendingLogs().first()
        assertEquals(0, pendingAfter.size)
    }
}
