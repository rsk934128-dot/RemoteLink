package com.example.util

import android.net.Uri
import org.json.JSONObject

data class ParsedQrResult(
    val host: String,
    val port: Int,
    val code: String,
    val isSecure: Boolean = false,
    val rawPayload: String
)

object QrCodeParser {

    /**
     * Parses diverse QR code payload formats produced by the web panel or custom servers:
     * - WebSocket URLs: ws://192.168.1.50:8080/ws?role=device&code=9F3A1C7B
     * - Secure WebSocket URLs: wss://myserver.com:443/ws?code=9F3A1C7B
     * - HTTP URLs: http://192.168.1.50:8080/?code=9F3A1C7B
     * - JSON strings: {"host":"192.168.1.50","port":8080,"code":"9F3A1C7B"}
     * - Colon-delimited: 192.168.1.50:8080:9F3A1C7B
     * - Raw alphanumeric pairing codes: 9F3A1C7B
     */
    fun parse(rawText: String, defaultHost: String = "192.168.1.5", defaultPort: Int = 8080): ParsedQrResult {
        val trimmed = rawText.trim()

        // 1. JSON Payload
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                val json = JSONObject(trimmed)
                val code = json.optString("code").ifBlank { json.optString("pairing_code", "") }
                val host = json.optString("host", defaultHost)
                val port = json.optInt("port", defaultPort)
                val isSecure = json.optBoolean("secure", json.optBoolean("ssl", false))
                if (code.isNotBlank()) {
                    return ParsedQrResult(
                        host = host,
                        port = if (port in 1..65535) port else defaultPort,
                        code = code.uppercase(),
                        isSecure = isSecure,
                        rawPayload = trimmed
                    )
                }
            } catch (_: Exception) {}
        }

        // 2. URI Schemes (ws://, wss://, http://, https://)
        if (trimmed.startsWith("ws://", ignoreCase = true) ||
            trimmed.startsWith("wss://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            try {
                val uri = Uri.parse(trimmed)
                val host = uri.host?.takeIf { it.isNotBlank() } ?: defaultHost
                val isSecure = uri.scheme?.equals("wss", ignoreCase = true) == true ||
                        uri.scheme?.equals("https", ignoreCase = true) == true
                val port = if (uri.port != -1) uri.port else if (isSecure) 443 else defaultPort

                // Check query params: code, pairingCode, key, id
                val codeParam = uri.getQueryParameter("code")
                    ?: uri.getQueryParameter("pairingCode")
                    ?: uri.getQueryParameter("id")
                    ?: uri.lastPathSegment?.takeIf { it.length in 4..16 && !it.contains(".") }

                val finalCode = codeParam?.takeIf { it.isNotBlank() } ?: trimmed.takeLast(8)

                return ParsedQrResult(
                    host = host,
                    port = port,
                    code = finalCode.uppercase(),
                    isSecure = isSecure,
                    rawPayload = trimmed
                )
            } catch (_: Exception) {}
        }

        // 3. Delimited: host:port:code (e.g. 192.168.1.20:8080:9F3A1C7B)
        if (trimmed.contains(":")) {
            val parts = trimmed.split(":")
            if (parts.size == 3) {
                val hostPart = parts[0].trim()
                val portPart = parts[1].trim().toIntOrNull()
                val codePart = parts[2].trim()
                if (hostPart.isNotBlank() && portPart != null && codePart.isNotBlank()) {
                    return ParsedQrResult(
                        host = hostPart,
                        port = portPart,
                        code = codePart.uppercase(),
                        rawPayload = trimmed
                    )
                }
            }
        }

        // 4. Raw pairing code (e.g., 9F3A1C7B or generated 8-char hex code)
        val cleanCode = trimmed.filter { it.isLetterOrDigit() }.uppercase()
        return ParsedQrResult(
            host = defaultHost,
            port = defaultPort,
            code = cleanCode.ifEmpty { trimmed },
            rawPayload = trimmed
        )
    }
}
