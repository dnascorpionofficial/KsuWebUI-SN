package io.github.a13e300.ksuwebui

import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebView

class WebUIState {
    var webView: WebView? = null
    var webviewInterface: WebViewInterface? = null
    lateinit var moduleDir: String
    var moduleName: String = ""
    var insets: Insets = Insets(0, 0, 0, 0)
    var isEdgeToEdgeEnabled = false
    var filePathCallback: ValueCallback<Array<Uri>>? = null
    var onSaveFileRequest: ((ByteArray, String, String) -> Unit)? = null
}
