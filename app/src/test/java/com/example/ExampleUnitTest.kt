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

    @Test
    fun testQrCodeParserWebSocketUrl() {
        val result = com.example.util.QrCodeParser.parse("ws://192.168.1.55:8080/ws?role=device&code=9F3A1C7B")
        assertEquals("192.168.1.55", result.host)
        assertEquals(8080, result.port)
        assertEquals("9F3A1C7B", result.code)
        assertFalse(result.isSecure)
    }

    @Test
    fun testQrCodeParserJson() {
        val json = """{"host":"10.0.0.42","port":9000,"code":"TEST4321","secure":true}"""
        val result = com.example.util.QrCodeParser.parse(json)
        assertEquals("10.0.0.42", result.host)
        assertEquals(9000, result.port)
        assertEquals("TEST4321", result.code)
        assertTrue(result.isSecure)
    }

    @Test
    fun testQrCodeParserRawCode() {
        val result = com.example.util.QrCodeParser.parse("9f3a1c7b")
        assertEquals("9F3A1C7B", result.code)
    }
}
