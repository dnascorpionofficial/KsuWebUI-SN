package io.github.a13e300.ksuwebui

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ModuleUpdateChecker {
    private const val TIMEOUT_MS = 6000
    private const val MAX_BYTES = 256 * 1024

    fun latestVersionCode(url: String): Long? {
        if (!url.startsWith("http", ignoreCase = true)) return null
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = TIMEOUT_MS
            c.readTimeout = TIMEOUT_MS
            c.setRequestProperty("User-Agent", "KsuWebUI-SN")
            try {
                if (c.responseCode != 200) return null
                val body = c.inputStream.use {
                    val buf = ByteArray(MAX_BYTES + 1)
                    var n = 0
                    while (n < buf.size) {
                        val r = it.read(buf, n, buf.size - n)
                        if (r < 0) break
                        n += r
                    }
                    if (n > MAX_BYTES) return null
                    String(buf, 0, n, Charsets.UTF_8)
                }
                JSONObject(body).optString("versionCode").trim().toLongOrNull()
            } finally {
                c.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }
}
