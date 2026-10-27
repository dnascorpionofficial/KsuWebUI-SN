package io.github.a13e300.ksuwebui

import androidx.annotation.StringRes
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {
    class Update(val title: String, val changelog: String, val changelogHtml: String, val apkUrl: String)

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

    @Volatile
    var status = ""

    private fun fetch(url: String, @StringRes label: Int, accept: String? = null): String {
        val conn = connect(url)
        try {
            accept?.let { conn.setRequestProperty("Accept", it) }
            if (conn.responseCode != 200) throw IOException(App.instance.getString(R.string.update_http_error, conn.responseCode, App.instance.getString(label)))
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun check(): Update? {
        val repo = BuildConfig.UPDATE_REPO
        if (repo.isEmpty()) {
            status = App.instance.getString(R.string.update_repo_not_set)
            return null
        }
        return try {
            val raw = fetch("https://raw.githubusercontent.com/$repo/HEAD/update.txt?t=${System.currentTimeMillis()}", R.string.update_label_version_info)
                .removePrefix("\uFEFF")
            val line2 = raw.lines().getOrNull(1)?.trim().orEmpty()
            val latestCode = line2.toIntOrNull() ?: run {
                status = App.instance.getString(R.string.update_version_invalid, line2.take(20))
                return null
            }
            if (latestCode <= BuildConfig.VERSION_CODE) {
                status = App.instance.getString(R.string.update_up_to_date, latestCode, BuildConfig.VERSION_CODE)
                return null
            }

            val release = JSONObject(
                fetch("https://api.github.com/repos/$repo/releases/latest", R.string.update_label_latest_release, "application/vnd.github.full+json")
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
                status = App.instance.getString(R.string.update_no_apk)
                return null
            }
            status = App.instance.getString(R.string.update_title)
            val body = release.optString("body").replace("\r\n", "\n").trim()
            var html = release.optString("body_html").trim()
            if (html.isEmpty() && body.isNotEmpty()) html = renderMarkdown(repo, body)
            val title = release.optString("name").trim().ifEmpty { release.optString("tag_name").trim() }
            Update(title, body, html, apkUrl)
        } catch (e: Exception) {
            status = App.instance.getString(R.string.update_check_failed, e.message ?: e.javaClass.simpleName)
            null
        }
    }

    private fun renderMarkdown(repo: String, text: String): String {
        val conn = connect("https://api.github.com/markdown")
        return try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("Content-Type", "application/json")
            val payload = JSONObject().put("text", text).put("mode", "gfm").put("context", repo).toString()
            conn.outputStream.use { it.write(payload.toByteArray()) }
            if (conn.responseCode != 200) "" else conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            ""
        } finally {
            conn.disconnect()
        }
    }

    fun cleanup() {
        File(App.instance.cacheDir, "update").deleteRecursively()
    }

    @Suppress("DEPRECATION")
    fun apkVersionCode(apk: File): Int? =
        App.instance.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)?.versionCode

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
            if (!part.renameTo(out)) throw IOException(App.instance.getString(R.string.update_save_failed))
            return out
        } catch (e: Exception) {
            part.delete()
            throw e
        } finally {
            conn.disconnect()
        }
    }

    fun install(apk: File): String? {
        val r = Shell.cmd(
            "pm install -r -S ${apk.length()} < '${apk.absolutePath}' && " +
                "{ am start -n ${App.instance.packageName}/.MainActivity >/dev/null 2>&1; true; }"
        ).exec()
        return if (r.isSuccess) null
        else (r.out + r.err).joinToString("\n").trim().ifEmpty { App.instance.getString(R.string.update_exit_code, r.code) }
    }
}
