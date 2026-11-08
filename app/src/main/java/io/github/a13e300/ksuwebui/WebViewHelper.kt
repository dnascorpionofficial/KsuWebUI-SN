package io.github.a13e300.ksuwebui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.CookieManager
import android.webkit.WebResourceResponse
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.webkit.WebViewAssetLoader
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.a13e300.ksuwebui.databinding.DialogConfirmBinding
import io.github.a13e300.ksuwebui.databinding.DialogReloadBinding
import com.topjohnwu.superuser.nio.FileSystemManager
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest


private const val WEB_DOMAIN = "mui.kernelsu.org"
private const val KSU_SCHEME = "ksu"
private const val ICON_HOST = "icon"
private const val KEY_ERUDA_CENTERED = "eruda_centered"
private fun Context.isAppDark(): Boolean =
    when (themeMode(getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE))) {
        THEME_DARK -> true
        THEME_LIGHT -> false
        else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

private fun Context.erudaTextJs(): String {
    val entries = mapOf(
        "add" to getString(R.string.eruda_add),
        "edit" to getString(R.string.edit_title),
        "key" to getString(R.string.choose_key),
        "value" to getString(R.string.choose_value),
        "valueFor" to getString(R.string.eruda_value_for, "%s"),
        "exists" to getString(R.string.eruda_exists, "%s"),
        "existsHint" to getString(R.string.eruda_exists_hint, "%s"),
        "deleteItem" to getString(R.string.eruda_delete_item, "%s"),
        "clearLocal" to getString(R.string.eruda_clear_local),
        "clearSession" to getString(R.string.eruda_clear_session),
        "clearConsole" to getString(R.string.eruda_clear_console),
        "clearNetwork" to getString(R.string.eruda_clear_network),
        "restoreDefaults" to getString(R.string.eruda_restore_defaults)
    )
    return "window.ksuText = " + JSONObject(entries).toString() + ";"
}

private fun downloadJs(domain: String) = """
    (function() {
        if (window.ksu_download_enabled) return;
        window.ksu_download_enabled = true;
        const blobMap = new Map();
        const orgCreate = URL.createObjectURL;
        URL.createObjectURL = (obj) => {
            const url = orgCreate(obj);
            if (obj instanceof Blob) blobMap.set(url, obj);
            return url;
        };
        const orgRevoke = URL.revokeObjectURL;
        URL.revokeObjectURL = (url) => {
            setTimeout(() => blobMap.delete(url), 10000);
            orgRevoke(url);
        };
        const handleDownload = async (a) => {
            const urlParsed = new URL(a.href, location.href);
            const url = urlParsed.href;
            const fileName = a.download || url.split("/").pop().split("?")[0] || "download.bin";
            const isInternal = urlParsed.hostname === '$domain' || urlParsed.hostname === '$WEB_DOMAIN';
            if (url.startsWith('blob:') || url.startsWith('data:') || isInternal) {
                const blob = (url.startsWith('blob:') && blobMap.has(url)) ? blobMap.get(url) : await (await fetch(url)).blob();
                if (blob.size > 16 * 1024 * 1024) {
                    console.error("File too large, please use FileOutputStreamInterface instead.");
                    return;
                }
                const reader = new FileReader();
                reader.onload = () => {
                    ksu_download.save(reader.result.split(',')[1], fileName, blob.type);
                };
                reader.readAsDataURL(blob);
            } else {
                ksu_download.download(url, null, null);
            }
        };
        document.addEventListener("click", (e) => {
            const a = e.target.closest("a[download]");
            if (a) {
                e.preventDefault();
                handleDownload(a);
            }
        }, true);
        const orgClick = HTMLAnchorElement.prototype.click;
        HTMLAnchorElement.prototype.click = function () {
            this.hasAttribute("download") ? handleDownload(this) : orgClick.apply(this, arguments);
        };
    })();
"""

private fun moduleDomain(moduleId: String): String {
    val slug = moduleId.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).trim('-')
    val hash = MessageDigest.getInstance("SHA-256")
        .digest(moduleId.toByteArray())
        .take(4)
        .joinToString("") { "%02x".format(it) }
    val label = if (slug.isEmpty()) "m-$hash" else "$slug-$hash"
    return "$label.$WEB_DOMAIN"
}

fun clearModuleData(moduleId: String) {
    val origin = "https://${moduleDomain(moduleId)}"
    WebStorage.getInstance().deleteOrigin(origin)
    val cookies = CookieManager.getInstance()
    cookies.getCookie(origin)?.split(';')?.forEach {
        val name = it.substringBefore('=').trim()
        if (name.isNotEmpty()) cookies.setCookie(origin, "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/")
    }
    cookies.flush()
}

private fun WebUIActivity.buttonDialog(root: View, result: JsResult?): AlertDialog =
    MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_KsuWebUI_ButtonDialog)
        .setView(root)
        .setOnCancelListener { result?.cancel() }
        .create()

private fun WebUIActivity.showJsDialog(title: Int, message: String?, result: JsResult?, withCancel: Boolean) {
    MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
        .apply {
            if (withCancel) setNegativeButton(android.R.string.cancel) { _, _ -> result?.cancel() }
        }
        .setOnCancelListener { result?.cancel() }
        .show()
}

fun WebUIActivity.prepareWebView(state: WebUIState): Boolean {
    val moduleId = intent.getStringExtra("id")
    if (moduleId == null) {
        finish()
        return false
    }
    state.moduleName = intent.getStringExtra("name") ?: moduleId
    state.moduleDir = "/data/adb/modules/$moduleId"
    return true
}

fun WebUIActivity.initWebView(fs: FileSystemManager, state: WebUIState) {
    val webRoot = File("${state.moduleDir}/webroot")
    val moduleId = state.moduleDir.substringAfterLast('/')
    val domain = moduleDomain(moduleId)
    val pathHandler = RemoteFsPathHandler(
        webRoot,
        fs,
        { state.insets },
        { enable ->
            enableEdgeToEdge(enable)
        }
    )
    val webViewAssetLoader = WebViewAssetLoader.Builder()
        .setDomain(domain)
        .addPathHandler("/", pathHandler)
        .build()
    val internalAssetLoader = WebViewAssetLoader.Builder()
        .setDomain(WEB_DOMAIN)
        .addPathHandler("/internal/") { path -> pathHandler.handle("internal/$path") }
        .build()

    val erudaParts = HashMap<String, String>()
    fun erudaJs(name: String) = erudaParts.getOrPut(name) {
        assets.open("eruda/$name.js").bufferedReader().use { it.readText() }
    }
    fun erudaSetupJs(center: Boolean, dark: Boolean) = """
        eruda.init();
        ${if (center) erudaJs("center") else ""}
        var __ksuRestore = ${!center};
        var __ksuDark = $dark;
        ${erudaJs("pos_fix")}
        ${erudaJs("inset")}
        ${erudaTextJs()}
        ${erudaJs("guard")}
        ${erudaJs("storage")}
        ${erudaJs("ux")}
        ${erudaJs("theme")}
    """
    val erudaSource by lazy { assets.open("eruda.min.js").bufferedReader().use { it.readText() } }
    fun erudaBootJs(center: Boolean, dark: Boolean) = """
        if (!window.__erudaInit) {
        window.__erudaInit = true;
        ${erudaJs("pos_save")}
        $erudaSource
        ;
        ${erudaSetupJs(center, dark)}
        }
    """

    val webViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            val url = request.url

            if (url.scheme.equals(KSU_SCHEME, ignoreCase = true) && url.host.equals(ICON_HOST, ignoreCase = true)) {
                val packageName = url.path?.substring(1)
                if (!packageName.isNullOrEmpty()) {
                    val icon = AppIconUtil.loadAppIconSync(this@initWebView, packageName, 512)
                    if (icon != null) {
                        val stream = ByteArrayOutputStream()
                        icon.compress(Bitmap.CompressFormat.PNG, 100, stream)
                        val inputStream = ByteArrayInputStream(stream.toByteArray())
                        return WebResourceResponse("image/png", null, inputStream)
                    }
                }
            }

            return webViewAssetLoader.shouldInterceptRequest(url)
                ?: internalAssetLoader.shouldInterceptRequest(url)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            val prefs = getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
            val consoleModules = prefs.getStringSet(KEY_CONSOLE_MODULES, emptySet()).orEmpty()
            if (moduleId in consoleModules) {
                val centered = prefs.getStringSet(KEY_ERUDA_CENTERED, emptySet()).orEmpty()
                val center = moduleId !in centered
                view?.evaluateJavascript(erudaBootJs(center, isAppDark()), null)
                if (center) prefs.edit { putStringSet(KEY_ERUDA_CENTERED, centered + moduleId) }
            }
            view?.evaluateJavascript(downloadJs(domain), null)
            view?.evaluateJavascript(SYSTEM_BARS_JS, null)
        }
    }

    state.webView?.apply {
        val webviewInterface = WebViewInterface(state)
        state.webviewInterface = webviewInterface
        addJavascriptInterface(webviewInterface, "ksu")

        val downloadInterface = DownloadInterface(state)
        addJavascriptInterface(downloadInterface, "ksu_download")
        addJavascriptInterface(SystemBarsInterface(this@initWebView), "ksu_bars")

        setDownloadListener { url, _, contentDisposition, mimetype, _ ->
            downloadInterface.download(url, contentDisposition, mimetype)
        }

        setWebViewClient(webViewClient)
        webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                state.filePathCallback?.onReceiveValue(null)
                state.filePathCallback = filePathCallback
                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    if (intent.type?.startsWith(".") == true) {
                        intent.type = "*/*"
                    }
                }

                if (fileChooserParams?.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                try {
                    fileChooserLauncher.launch(intent)
                } catch (_: ActivityNotFoundException) {
                    state.filePathCallback?.onReceiveValue(null)
                    state.filePathCallback = null
                    return false
                }
                return true
            }

            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                showJsDialog(R.string.dialog_alert, message, result, withCancel = false)
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                showJsDialog(R.string.dialog_confirm, message, result, withCancel = true)
                return true
            }

            override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
                val msg = message.orEmpty()
                if (msg == "__ksu_confirm__") {
                    val confirm = DialogConfirmBinding.inflate(layoutInflater)
                    confirm.title.text = defaultValue.orEmpty()
                    confirm.message.visibility = View.GONE
                    val dialog = buttonDialog(confirm.root, result)
                    confirm.no.setOnClickListener {
                        result?.cancel()
                        dialog.dismiss()
                    }
                    confirm.yes.setOnClickListener {
                        result?.confirm("yes")
                        dialog.dismiss()
                    }
                    dialog.show()
                    return true
                }
                if (msg == "__ksu_copy__") {
                    val clipboard = this@initWebView.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("text", defaultValue.orEmpty()))
                    Toast.makeText(this@initWebView, R.string.copied, Toast.LENGTH_SHORT).show()
                    result?.confirm("")
                    return true
                }
                if (msg == "__ksu_reload__" || msg.startsWith("__ksu_pick__:")) {
                    val reload = msg == "__ksu_reload__"
                    val choice = DialogReloadBinding.inflate(layoutInflater)
                    if (!reload) {
                        choice.title.setText(
                            when (msg.removePrefix("__ksu_pick__:")) {
                                "copy" -> R.string.copy_title
                                "view" -> R.string.view_title
                                else -> R.string.edit_title
                            }
                        )
                        choice.first.setText(R.string.choose_key)
                        choice.second.setText(R.string.choose_value)
                    }
                    val dialog = buttonDialog(choice.root, result)
                    choice.first.setOnClickListener {
                        result?.confirm(if (reload) "table" else "key")
                        dialog.dismiss()
                    }
                    choice.second.setOnClickListener {
                        result?.confirm(if (reload) "webui" else "value")
                        dialog.dismiss()
                    }
                    dialog.show()
                    return true
                }
                val input = EditText(this@initWebView).apply {
                    if (defaultValue != null) setText(defaultValue)
                }
                val container = FrameLayout(this@initWebView)
                val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                params.leftMargin = resources.getDimensionPixelSize(androidx.appcompat.R.dimen.abc_dialog_padding_material)
                params.rightMargin = resources.getDimensionPixelSize(androidx.appcompat.R.dimen.abc_dialog_padding_material)
                input.layoutParams = params
                container.addView(input)

                MaterialAlertDialogBuilder(this@initWebView)
                    .setTitle(R.string.dialog_prompt)
                    .setMessage(message)
                    .setView(container)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm(input.text.toString()) }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }
        }
        loadUrl("https://$domain/index.html")
    }
}
