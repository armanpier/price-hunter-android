package com.teamwork.pricehunter.core.network

import java.util.UUID

data class NetworkCaptureEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val method: String,
    val url: String,
    val statusCode: Int,
    val durationMs: Long,
    val requestHeaders: Map<String, String> = emptyMap(),
    val responseHeaders: Map<String, String> = emptyMap(),
    val requestBody: String? = null,
    val responseBody: String? = null,
    val responseSnippet: String? = null
) {
    val isSuccess: Boolean get() = statusCode in 200..299
}

object HeaderSanitizer {
    val SENSITIVE_HEADERS = setOf(
        "authorization", "cookie", "set-cookie", "proxy-authorization",
        "x-auth-token", "token", "x-token", "auth-token", "api-key",
        "x-api-key", "apikey", "api_key"
    )

    fun sanitizeHeaders(headers: Map<String, String>): Map<String, String> {
        return headers.mapValues { (key, value) ->
            val lower = key.lowercase()
            if (SENSITIVE_HEADERS.contains(lower) ||
                lower.contains("token") ||
                lower.contains("auth") ||
                lower.contains("cookie") ||
                lower.contains("secret") ||
                lower.contains("api-key") ||
                lower.contains("apikey") ||
                lower.contains("api_key") ||
                lower.contains("key")
            ) {
                "***MASKED***"
            } else {
                value
            }
        }
    }
}

class DebugNetworkCapturePool(val maxCapacity: Int = 100) {
    private val lock = Any()
    private val entries = ArrayDeque<NetworkCaptureEntry>()
    var isDebugEnabled: Boolean = true

    fun record(entry: NetworkCaptureEntry) = logCapture(entry)

    fun logCapture(entry: NetworkCaptureEntry) = synchronized(lock) {
        if (!isDebugEnabled) return
        val sanitizedEntry = entry.copy(
            requestHeaders = HeaderSanitizer.sanitizeHeaders(entry.requestHeaders),
            responseHeaders = HeaderSanitizer.sanitizeHeaders(entry.responseHeaders)
        )
        while (entries.size >= maxCapacity) {
            entries.removeFirst()
        }
        entries.addLast(sanitizedEntry)
    }

    fun getCaptures(): List<NetworkCaptureEntry> = synchronized(lock) {
        entries.toList()
    }

    fun getAllEntries(): List<NetworkCaptureEntry> = getCaptures()

    val size: Int get() = synchronized(lock) { entries.size }

    fun clear() = synchronized(lock) {
        entries.clear()
    }

    companion object {
        fun maskHeaders(headers: Map<String, String>): Map<String, String> {
            return HeaderSanitizer.sanitizeHeaders(headers)
        }
    }
}
