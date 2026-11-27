package com.tablauncher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset

object Net {
    private const val UA = "Mozilla/5.0 (Linux; Android 14; Tablet) TabLauncher/1.0"

    /** Simple GET that returns the body as text, or null on any failure. */
    suspend fun get(url: String, timeoutMs: Int = 10_000): String? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                instanceFollowRedirects = true
            }
            if (conn.responseCode !in 200..299) return@withContext null
            val charset = conn.contentType?.substringAfter("charset=", "")?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
            conn.inputStream.use { it.readBytes().toString(charset) }
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
