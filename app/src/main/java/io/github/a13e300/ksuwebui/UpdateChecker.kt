package io.github.a13e300.ksuwebui

import android.content.Context
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
    var available = false

    @Volatile
    var status = ""

    private fun connect(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 10000
            useCaches = false
            setRequestProperty("User-Agent", "KsuWebUI-SN")
            setRequestProperty("Cache-Control", "no-cache")
        }

    private val cachePrefs get() = App.instance.getSharedPreferences("update_cache", Context.MODE_PRIVATE)

    private val versionRegex = Regex("""(\d+)\.(\d+)-(\d+)""")

    private fun encode(match: MatchResult): Int {
        val (major, minor, suffix) = match.destructured
        return major.toInt() * 1_000_000 + minor.toInt().coerceAtMost(999) * 1_000 + suffix.toInt().coerceAtMost(999)
    }

    fun installedLabel() = "${BuildConfig.VERSION_NAME}-${BuildConfig.BUILD_SUFFIX}"

    private fun installedVersion(): Int? = versionRegex.matchEntire(installedLabel())?.let { encode(it) }

    fun cached(): Boolean {
        val code = cachePrefs.getInt("latest", 0)
        val installed = installedVersion() ?: return false
        return code > installed
    }

    private fun saveCache(etag: String?, code: Int, label: String) {
        cachePrefs.edit().putString("etag", etag).putInt("latest", code).putString("latest_label", label).apply()
    }

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

    private class Latest(val code: Int, val label: String)

    private fun apkAssets(release: JSONObject): Sequence<JSONObject> {
        val assets = release.optJSONArray("assets") ?: return emptySequence()
        return (0 until assets.length()).asSequence()
            .map { assets.getJSONObject(it) }
            .filter { it.optString("name").endsWith(".apk", ignoreCase = true) }
    }

    private fun latestVersion(repo: String): Latest? {
        val conn = connect("https://api.github.com/repos/$repo/releases/latest")
        try {
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            val etag = cachePrefs.getString("etag", null)
            val savedCode = cachePrefs.getInt("latest", 0)
            val savedLabel = cachePrefs.getString("latest_label", null)
            val canReuse = etag != null && savedCode != 0 && savedLabel != null
            if (canReuse) conn.setRequestProperty("If-None-Match", etag)
            val http = conn.responseCode
            if (http == 304 && canReuse) return Latest(savedCode, savedLabel)
            if (http != 200) throw IOException(App.instance.getString(R.string.update_http_error, http, App.instance.getString(R.string.update_label_latest_release)))
            val release = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val apkName = apkAssets(release).firstOrNull()?.optString("name")
            if (apkName == null) {
                status = App.instance.getString(R.string.update_no_apk)
                return null
            }
            val match = versionRegex.find(apkName)
                ?: versionRegex.find(release.optString("tag_name"))
                ?: versionRegex.find(release.optString("name"))
            if (match == null) {
                status = App.instance.getString(R.string.update_version_invalid, apkName.take(20))
                return null
            }
            val latest = Latest(encode(match), match.value)
            saveCache(conn.getHeaderField("ETag"), latest.code, latest.label)
            return latest
        } finally {
            conn.disconnect()
        }
    }

    fun check(): Boolean {
        val repo = BuildConfig.UPDATE_REPO
        if (repo.isEmpty()) {
            status = App.instance.getString(R.string.update_repo_not_set)
            return false
        }
        val installed = installedVersion()
        if (installed == null) {
            status = App.instance.getString(R.string.update_version_invalid, installedLabel().take(20))
            return false
        }
        return try {
            val latest = latestVersion(repo) ?: return false
            if (latest.code <= installed) {
                status = App.instance.getString(R.string.update_up_to_date, latest.label, installedLabel())
                return false
            }
            status = App.instance.getString(R.string.update_title)
            true
        } catch (e: Exception) {
            status = App.instance.getString(R.string.update_check_failed, e.message ?: e.javaClass.simpleName)
            cached()
        }
    }

    fun fetchRelease(): Update? {
        val repo = BuildConfig.UPDATE_REPO
        return try {
            val release = JSONObject(
                fetch("https://api.github.com/repos/$repo/releases/latest", R.string.update_label_latest_release, "application/vnd.github.full+json")
            )
            val apkUrl = apkAssets(release)
                .map { it.optString("browser_download_url") }
                .firstOrNull { it.isNotEmpty() }
            if (apkUrl == null) {
                status = App.instance.getString(R.string.update_no_apk)
                return null
            }
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
