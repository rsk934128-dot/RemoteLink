package com.example.ai

import android.util.Log
import com.example.model.MirroredNotificationItem
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val TAG = "GeminiService"
private const val TIMEOUT_MS = 60_000L

object RemoteLinkGeminiService {

    // Lazy initialization of the Gemini model as required by Firebase AI Logic
    private val generativeModel by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    suspend fun summarizeNotifications(notifications: List<MirroredNotificationItem>): String = withContext(Dispatchers.IO) {
        if (notifications.isEmpty()) {
            return@withContext "No notifications to summarize yet."
        }

        try {
            withTimeout(TIMEOUT_MS) {
                val formatted = notifications.take(15).joinToString("\n") { notif ->
                    "[${notif.appName}] ${notif.title}: ${notif.text}"
                }

                val prompt = """
                    You are an intelligent device assistant for RemoteLink.
                    Analyze and summarize the following phone notifications that were mirrored to the PC panel:
                    
                    $formatted
                    
                    Please provide:
                    1. ⚡ Quick 1-2 sentence executive summary of key events.
                    2. 🚨 Urgent / Action Required (e.g., OTP codes, direct questions, meeting updates).
                    3. 💬 Social / Updates (casual messages, newsletters).
                    Keep the tone concise, organized, and helpful.
                """.trimIndent()

                val response = generativeModel.generateContent(prompt)
                response.text ?: "No summary generated."
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error summarizing notifications with Gemini", e)
            "Failed to summarize notifications: ${e.localizedMessage ?: "Unknown error"}"
        }
    }

    suspend fun analyzeFile(fileName: String, sizeStr: String, fileSnippetOrType: String): String = withContext(Dispatchers.IO) {
        try {
            withTimeout(TIMEOUT_MS) {
                val prompt = """
                    You are a security and productivity assistant for RemoteLink file transfers.
                    The user is transferring a file between their PC and Android device:
                    - File Name: $fileName
                    - Declared Size: $sizeStr
                    - Context / Snippet: $fileSnippetOrType
                    
                    Provide:
                    1. What this file appears to be and its common use case.
                    2. Recommended viewer/editor on Android or PC.
                    3. Any security or privacy tips (e.g. executable warning, confidential data).
                    Keep it short and scannable.
                """.trimIndent()

                val response = generativeModel.generateContent(prompt)
                response.text ?: "Analysis unavailable."
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing file with Gemini", e)
            "Failed to analyze file: ${e.localizedMessage ?: "Unknown error"}"
        }
    }

    fun askGeminiStream(prompt: String): Flow<String> = flow {
        withTimeout(TIMEOUT_MS) {
            val responseStream = generativeModel.generateContentStream(prompt)
            responseStream.collect { chunk ->
                chunk.text?.let { emit(it) }
            }
        }
    }.flowOn(Dispatchers.IO)
}
