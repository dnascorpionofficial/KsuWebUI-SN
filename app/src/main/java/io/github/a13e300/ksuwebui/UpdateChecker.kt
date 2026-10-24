package io.github.a13e300.ksuwebui

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Update check. update.txt in the repo [BuildConfig.UPDATE_REPO]: line 1 = app version name
 * (the workflow patches it into the build), line 2 = version code used here.
 * When line 2 is greater than this build's versionCode, the latest GitHub release supplies
 * the changelog (release notes) and the APK (first .apk asset).
 * Only called when the app is opened; nothing is scheduled or kept running.
 */
object UpdateChecker {
    class Update(val version: String, val changelog: String, val apkUrl: String)

    // result of the last check, so the button survives an activity recreate (e.g. dark mode toggle)
    @Volatile
    var available: Update? = null

    private fun connect(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 15000
            useCaches = false
            setRequestProperty("User-Agent", "KsuWebUI-SN")
            setRequestProperty("Cache-Control", "no-cache")
        }

    // human-readable result of the last check (shown as a toast when Debugging is on)
    @Volatile
    var status = ""

    private fun fetch(url: String, label: String, accept: String? = null): String {
        val conn = connect(url)
        try {
            accept?.let { conn.setRequestProperty("Accept", it) }
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode} ($label)")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** Blocking, call off the main thread. Returns null when up to date or on any failure (see [status]). */
    fun check(): Update? {
        val repo = BuildConfig.UPDATE_REPO
        if (repo.isEmpty()) {
            status = "Update check: repo not set in this build"
            return null
        }
        return try {
            val raw = fetch("https://raw.githubusercontent.com/$repo/HEAD/update.txt?t=${System.currentTimeMillis()}", "update.txt")
                .removePrefix("\uFEFF")
            val line2 = raw.lines().getOrNull(1)?.trim().orEmpty()
            val latestCode = line2.toIntOrNull() ?: run {
                status = "update.txt line 2 is not a number: ${line2.take(20)}"
                return null
            }
            if (latestCode <= BuildConfig.VERSION_CODE) {
                status = "Up to date (update.txt $latestCode, installed ${BuildConfig.VERSION_CODE})"
                return null
            }

            val release = JSONObject(
                fetch("https://api.github.com/repos/$repo/releases/latest", "latest release", "application/vnd.github+json")
            )
            val assets = release.optJSONArray("assets")
            var apkUrl: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apkUrl = a.optString("browser_download_url").ifEmpty { null }
                        if (apkUrl != null) break
                    }
                }
            }
            if (apkUrl == null) {
                status = "Latest release has no .apk file"
                return null
            }
            val tag = release.optString("tag_name").ifEmpty { release.optString("name") }
            status = "Update found: $tag"
            Update(tag, release.optString("body").replace("\r\n", "\n").trim(), apkUrl)
        } catch (e: Exception) {
            status = "Update check failed: ${e.message ?: e.javaClass.simpleName}"
            null
        }
    }

    /** Version code of a downloaded APK, or null if it cannot be read. */
    @Suppress("DEPRECATION")
    fun apkVersionCode(apk: File): Int? =
        App.instance.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)?.versionCode

    /** Downloads the APK into the app cache; onProgress gets 0..100 when the size is known. Run on an IO dispatcher. */
    suspend fun download(update: Update, onProgress: (Int) -> Unit): File {
        val dir = File(App.instance.cacheDir, "update").apply { mkdirs() }
        val part = File(dir, "update.apk.part")
        val out = File(dir, "update.apk")
        val conn = connect(update.apkUrl)
        try {
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var last = -1
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val p = (done * 100 / total).toInt()
                            if (p != last) {
                                last = p
                                onProgress(p)
                            }
                        }
                    }
                }
            }
            out.delete()
            if (!part.renameTo(out)) throw IOException("Could not save the download")
            return out
        } catch (e: Exception) {
            part.delete()
            throw e
        } finally {
            conn.disconnect()
        }
    }

    /** Installs through root (the app already needs it), then reopens the app. Returns null on success, else the error. */
    fun install(apk: File): String? {
        val r = Shell.cmd(
            "pm install -r -S ${apk.length()} < '${apk.absolutePath}' && " +
                "{ am start -n ${App.instance.packageName}/.MainActivity >/dev/null 2>&1; true; }"
        ).exec()
        return if (r.isSuccess) null
        else (r.out + r.err).joinToString("\n").trim().ifEmpty { "exit code ${r.code}" }
    }
}
