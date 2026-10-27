package com.example

import com.example.model.ServerConfig
import com.example.network.RemoteLinkWebSocketManager
import com.example.service.AppNotificationFilterManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testServerConfigWebSocketUrl() {
        val config = ServerConfig(host = "192.168.1.5", port = 8080, code = "9f3a1c7b", isSecure = false)
        val url = config.toWebSocketUrl()
        assertEquals("ws://192.168.1.5:8080/ws?role=device&code=9F3A1C7B", url)
    }

    @Test
    fun testServerConfigSecureWebSocketUrl() {
        val config = ServerConfig(host = "panel.remotelink.org", port = 443, code = "ABCD1234", isSecure = true)
        val url = config.toWebSocketUrl()
        assertEquals("wss://panel.remotelink.org:443/ws?role=device&code=ABCD1234", url)
    }

    @Test
    fun testFormatFileSize() {
        assertEquals("500 B", RemoteLinkWebSocketManager.formatFileSize(500L))
        assertEquals("1.5 KB", RemoteLinkWebSocketManager.formatFileSize(1536L))
        assertEquals("2.0 MB", RemoteLinkWebSocketManager.formatFileSize(2 * 1024 * 1024L))
        assertEquals("1.0 GB", RemoteLinkWebSocketManager.formatFileSize(1024 * 1024 * 1024L))
    }

    @Test
    fun testAppNotificationFilter() {
        // By default, non-blocked app is permitted
        assertTrue(AppNotificationFilterManager.isAppPermitted("com.whatsapp"))

        // Block WhatsApp
        AppNotificationFilterManager.setAppPermitted("com.whatsapp", false)
        assertFalse(AppNotificationFilterManager.isAppPermitted("com.whatsapp"))

        // Unblock WhatsApp
        AppNotificationFilterManager.setAppPermitted("com.whatsapp", true)
        assertTrue(AppNotificationFilterManager.isAppPermitted("com.whatsapp"))
    }
}
