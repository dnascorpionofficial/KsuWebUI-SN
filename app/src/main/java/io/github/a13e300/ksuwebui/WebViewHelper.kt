package io.github.a13e300.ksuwebui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import androidx.webkit.WebViewAssetLoader
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.a13e300.ksuwebui.databinding.DialogReloadBinding
import com.topjohnwu.superuser.nio.FileSystemManager
import java.io.File


private const val WEB_DOMAIN = "mui.kernelsu.org"
private const val KSU_SCHEME = "ksu"
private const val ICON_HOST = "icon"
private const val ERUDA_CENTER_JS = """
try {
  eruda.position({ x: 99999, y: 99999 });
  var p = eruda.position();
  var s = window.innerHeight - p.y - 10;
  eruda.position({ x: p.x, y: Math.round((window.innerHeight - s) / 2) });
} catch (e) {}
"""
private const val ERUDA_POS_SAVE_JS = """
var __ksuPos = null;
try {
  var __raw = localStorage.getItem('eruda-entry-button');
  if (__raw) {
    var __o = JSON.parse(__raw);
    if (__o && __o.pos && typeof __o.pos.x === 'number' && typeof __o.pos.y === 'number') __ksuPos = __o.pos;
  }
} catch (e) {}
"""
private const val ERUDA_POS_FIX_JS = """
try {
  var __eb = eruda._entryBtn;
  if (__eb && !__eb.__ksuFixed) {
    __eb.__ksuFixed = true;
    __eb.setPos = function(p) {
      var d = this._getDefPos();
      var x = Math.min(Math.max(p.x, 0), Math.max(d.x, 0));
      var y = Math.min(Math.max(p.y, 0), Math.max(d.y, 0));
      this._${'$'}el.css({ left: x, top: y });
      if (p.x >= 0 && p.x <= d.x + 10 && p.y >= 0 && p.y <= d.y + 10) this.config.set('pos', p);
    };
    __eb._resetPos = function() {
      var p = this.config.get('rememberPos') ? this.config.get('pos') : this._getDefPos();
      this.setPos(p);
    };
    if (__ksuRestore && __ksuPos) {
      __eb.config.set('pos', __ksuPos);
      __eb.setPos(__ksuPos);
    }
  }
} catch (e) {}
"""
private const val ERUDA_INSET_JS = """
try {
  var __h = document.getElementById('eruda');
  var __r = __h && (__h.shadowRoot || __h);
  if (__r && !__r.querySelector('#ksu-eruda-inset')) {
    var __st = document.createElement('style');
    __st.id = 'ksu-eruda-inset';
    __st.textContent = '.eruda-dev-tools{bottom:var(--safe-area-inset-bottom,0px)!important;}';
    __r.appendChild(__st);
  }
} catch (e) {}
"""
private const val ERUDA_STORAGE_JS = """
(function() {
  try {
    if (window.__erudaAddStorage) return;
    window.__erudaAddStorage = true;
    var ICON_ADD = '<svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor" style="display:block"><path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/></svg>';
    var ICON_EDIT = '<svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor" style="display:block"><path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/></svg>';
    function makeBtn(cls, title, icon) {
      var b = document.createElement('div');
      b.className = 'eruda-btn ' + cls;
      b.title = title;
      b.innerHTML = icon;
      return b;
    }
    function install(section, storeName, area) {
      var host = document.getElementById('eruda');
      var root = host && (host.shadowRoot || host);
      var refresh = root && root.querySelector('.eruda-' + section + ' .eruda-refresh-storage');
      var tool = window.eruda && eruda.get && eruda.get('resources');
      var store = tool && tool[storeName];
      if (!refresh || !store) return false;
      if (root.querySelector('.eruda-' + section + ' .eruda-add-storage')) return true;
      var add = makeBtn('eruda-add-storage', 'Add', ICON_ADD);
      var edit = makeBtn('eruda-edit-storage eruda-btn-disabled', 'Edit', ICON_EDIT);
      refresh.insertAdjacentElement('afterend', edit);
      refresh.insertAdjacentElement('afterend', add);
      refresh.addEventListener('click', function(e) {
        e.preventDefault();
        e.stopImmediatePropagation();
        var choice = window.prompt('__ksu_reload__');
        if (choice === 'webui') {
          location.reload();
        } else if (choice === 'table') {
          store.refresh();
          sync();
        }
      }, true);
      function sync() {
        if (store._selectedItem) edit.classList.remove('eruda-btn-disabled');
        else edit.classList.add('eruda-btn-disabled');
      }
      var orig = store._updateButtons;
      store._updateButtons = function() {
        orig.apply(this, arguments);
        sync();
      };
      function save(key, value) {
        try {
          area.setItem(key, value);
        } catch (e) {
          window.alert(String(e));
          return;
        }
        store.refresh();
        sync();
      }
      add.addEventListener('click', function() {
        var key = window.prompt('Key');
        if (key === null) return;
        key = key.trim();
        if (!key) return;
        if (area.getItem(key) !== null) {
          window.alert('"' + key + '" already exists. Select it and use the edit button.');
          return;
        }
        var value = window.prompt('Value', '');
        if (value === null) return;
        save(key, value);
      });
      edit.addEventListener('click', function() {
        var key = store._selectedItem;
        if (!key) return;
        var value = window.prompt('Value for "' + key + '"', area.getItem(key) || '');
        if (value === null) return;
        save(key, value);
      });
      return true;
    }
    var tries = 0;
    var timer = setInterval(function() {
      var a = install('local-storage', '_localStorage', localStorage);
      var b = install('session-storage', '_sessionStorage', sessionStorage);
      if ((a && b) || ++tries > 40) clearInterval(timer);
    }, 250);
  } catch (e) {}
})();
"""
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
    val hash = java.security.MessageDigest.getInstance("SHA-256")
        .digest(moduleId.toByteArray())
        .take(4)
        .joinToString("") { "%02x".format(it) }
    val label = if (slug.isEmpty()) "m-$hash" else "$slug-$hash"
    return "$label.$WEB_DOMAIN"
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

    val eruda by lazy { assets.open("eruda.min.js").bufferedReader().use { it.readText() } }

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
                        val stream = java.io.ByteArrayOutputStream()
                        icon.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                        val inputStream = java.io.ByteArrayInputStream(stream.toByteArray())
                        return WebResourceResponse("image/png", null, inputStream)
                    }
                }
            }

            return webViewAssetLoader.shouldInterceptRequest(url)
                ?: internalAssetLoader.shouldInterceptRequest(url)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            val prefs = getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
            val consoleModules = prefs.getStringSet("console_modules", emptySet()).orEmpty()
            if (moduleId in consoleModules) {
                val centered = prefs.getStringSet("eruda_centered", emptySet()).orEmpty()
                val center = moduleId !in centered
                view?.evaluateJavascript(
                    "if (!window.__erudaInit) {\nwindow.__erudaInit = true;\n$ERUDA_POS_SAVE_JS\n$eruda\n;eruda.init();\n${if (center) ERUDA_CENTER_JS else ""}\nvar __ksuRestore = ${!center};\n$ERUDA_POS_FIX_JS\n$ERUDA_INSET_JS\n$ERUDA_STORAGE_JS\n}",
                    null
                )
                if (center) prefs.edit().putStringSet("eruda_centered", centered + moduleId).apply()
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

                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
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
                MaterialAlertDialogBuilder(this@initWebView)
                    .setTitle(R.string.dialog_alert)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                MaterialAlertDialogBuilder(this@initWebView)
                    .setTitle(R.string.dialog_confirm)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }

            override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
                if (message == "__ksu_reload__") {
                    val view = DialogReloadBinding.inflate(layoutInflater)
                    val dialog = MaterialAlertDialogBuilder(this@initWebView, R.style.ThemeOverlay_KsuWebUI_ButtonDialog)
                        .setView(view.root)
                        .setOnCancelListener { result?.cancel() }
                        .create()
                    view.table.setOnClickListener {
                        result?.confirm("table")
                        dialog.dismiss()
                    }
                    view.webui.setOnClickListener {
                        result?.confirm("webui")
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
