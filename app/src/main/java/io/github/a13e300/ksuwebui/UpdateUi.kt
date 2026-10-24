package io.github.a13e300.ksuwebui

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.text.TextUtils
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Changelog (rendered like on GitHub) + Download / Later. */
@SuppressLint("SetJavaScriptEnabled")
fun AppCompatActivity.showUpdateDialog(update: UpdateChecker.Update) {
    val density = resources.displayMetrics.density
    val maxHeight = (resources.displayMetrics.heightPixels * 0.5).toInt()
    val minHeight = (96 * density).toInt()
    val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    val web = WebView(this).apply {
        setBackgroundColor(Color.TRANSPARENT)
        overScrollMode = android.view.View.OVER_SCROLL_NEVER
        settings.javaScriptEnabled = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, minHeight)
        webViewClient = object : WebViewClient() {
            // links open in the browser; same-page anchors do nothing
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.toString().startsWith("https://github.com/#")) return true
                if (url.scheme == "http" || url.scheme == "https") {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {
                    }
                }
                return true
            }

            // fit the dialog to the content, up to half the screen (then it scrolls)
            override fun onPageFinished(view: WebView, url: String?) {
                view.post {
                    val h = (view.contentHeight * density).toInt() + (8 * density).toInt()
                    view.layoutParams = view.layoutParams.apply { height = h.coerceIn(minHeight, maxHeight) }
                }
            }
        }
    }
    val box = FrameLayout(this).apply {
        val pad = (24 * density).toInt()
        setPadding(pad, (8 * density).toInt(), pad, 0)
        addView(web)
    }
    val textColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.WHITE)
    web.loadDataWithBaseURL("https://github.com/", changelogPage(update, textColor, dark), "text/html", "utf-8", null)

    MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_KsuWebUI_ButtonDialog)
        .setTitle(R.string.update_title)
        .setView(box)
        .setPositiveButton(R.string.update_download) { _, _ -> startUpdate(update) }
        .setNegativeButton(R.string.update_later, null)
        .setOnDismissListener {
            (web.parent as? ViewGroup)?.removeView(web)
            web.destroy()
        }
        .show()
}

// GitHub's rendered HTML inside a page styled like GitHub's markdown
private fun AppCompatActivity.changelogPage(update: UpdateChecker.Update, textColor: Int, dark: Boolean): String {
    val body = when {
        update.changelogHtml.isNotBlank() -> update.changelogHtml
        // GitHub could not render it: show the raw notes
        update.changelog.isNotBlank() -> "<div style=\"white-space:pre-wrap\">${TextUtils.htmlEncode(update.changelog)}</div>"
        else -> TextUtils.htmlEncode(getString(R.string.update_no_changelog))
    }
    val text = String.format("#%06X", 0xFFFFFF and textColor)
    val link = if (dark) "#4493f8" else "#0969da"
    val border = if (dark) "#3d444d" else "#d1d9e0"
    val codeBg = if (dark) "rgba(101,108,118,0.2)" else "rgba(129,139,152,0.12)"
    val muted = if (dark) "#9198a1" else "#59636e"
    val tip = if (dark) "#3fb950" else "#1a7f37"
    val important = if (dark) "#ab7df8" else "#8250df"
    val warning = if (dark) "#d29922" else "#9a6700"
    val caution = if (dark) "#f85149" else "#d1242f"
    // syntax highlighting colours for GitHub's code blocks (pl-* classes)
    val plComment = muted
    val plKeyword = if (dark) "#ff7b72" else "#cf222e"
    val plString = if (dark) "#a5d6ff" else "#0a3069"
    val plConst = if (dark) "#79c0ff" else "#0550ae"
    val plEntity = if (dark) "#d2a8ff" else "#8250df"
    val plVar = if (dark) "#ffa657" else "#953800"
    val plTag = if (dark) "#7ee787" else "#116329"
    val plAdd = if (dark) "#aff5b4" else "#1a7f37"
    val plDel = if (dark) "#ffdcd7" else "#82071e"
    return """
        <!DOCTYPE html><html><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
        body{margin:0;padding:0;font:14px/1.5 -apple-system,system-ui,sans-serif;color:$text;word-wrap:break-word}
        body>:first-child{margin-top:0!important} body>:last-child{margin-bottom:0!important}
        a{color:$link;text-decoration:none}
        h1,h2,h3,h4,h5,h6{margin:20px 0 10px;font-weight:600;line-height:1.25}
        h1{font-size:1.6em;padding-bottom:.3em;border-bottom:1px solid $border}
        h2{font-size:1.3em;padding-bottom:.3em;border-bottom:1px solid $border}
        h3{font-size:1.1em} h4{font-size:1em} h5,h6{font-size:.9em}
        h6{color:$muted}
        .anchor{display:none}
        p,ul,ol,blockquote,pre,table,details,dl{margin:0 0 12px}
        ul,ol{padding-left:2em} li+li{margin-top:.25em} li>p{margin-top:12px}
        ul.contains-task-list,ul.contains-task-list ul,ul.contains-task-list ol{list-style:none;padding-left:1.4em}
        .task-list-item input{margin:0 .5em .25em -1.4em;vertical-align:middle}
        code,tt,kbd,samp{font:12px ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;background:$codeBg;padding:.2em .4em;border-radius:6px}
        kbd{border:1px solid $border;border-bottom-width:2px;background:none}
        pre{background:$codeBg;padding:12px;border-radius:6px;overflow:auto;line-height:1.45}
        pre code,pre tt{background:none;padding:0;font-size:12px;white-space:pre}
        blockquote{padding:0 1em;color:$muted;border-left:.25em solid $border}
        blockquote>:last-child{margin-bottom:0}
        table{border-collapse:collapse;display:block;overflow:auto;max-width:100%}
        th,td{border:1px solid $border;padding:6px 12px} th{font-weight:600}
        tr:nth-child(2n){background:$codeBg}
        img{max-width:100%;height:auto} img[align=right]{padding-left:20px} img[align=left]{padding-right:20px}
        hr{height:.25em;padding:0;margin:20px 0;background:$border;border:0}
        details>summary{font-weight:600}
        del{opacity:.8} mark{background:#bb800966;color:inherit}
        sub,sup{font-size:.75em}
        .user-mention,.issue-link,.commit-link,.team-mention{font-weight:500}
        .highlight{margin:0 0 12px} .highlight pre{margin:0}
        .pl-c{color:$plComment} .pl-k{color:$plKeyword} .pl-s,.pl-pds,.pl-sr{color:$plString}
        .pl-c1,.pl-sra,.pl-smi{color:$plConst} .pl-en{color:$plEntity} .pl-v,.pl-smw{color:$plVar}
        .pl-ent{color:$plTag} .pl-mi1{color:$plAdd;background:rgba(46,160,67,.15)}
        .pl-md{color:$plDel;background:rgba(248,81,73,.15)} .pl-mi{font-style:italic}
        .markdown-alert{padding:2px 16px;margin-bottom:12px;border-left:.25em solid $border}
        .markdown-alert>:last-child{margin-bottom:0}
        .markdown-alert-title{display:flex;align-items:center;gap:6px;font-weight:600;margin:6px 0 8px!important}
        .markdown-alert-title svg{fill:currentColor}
        .markdown-alert-note{border-color:$link} .markdown-alert-note .markdown-alert-title{color:$link}
        .markdown-alert-tip{border-color:$tip} .markdown-alert-tip .markdown-alert-title{color:$tip}
        .markdown-alert-important{border-color:$important} .markdown-alert-important .markdown-alert-title{color:$important}
        .markdown-alert-warning{border-color:$warning} .markdown-alert-warning .markdown-alert-title{color:$warning}
        .markdown-alert-caution{border-color:$caution} .markdown-alert-caution .markdown-alert-title{color:$caution}
        </style></head><body>$body</body></html>
    """
}

private fun AppCompatActivity.startUpdate(update: UpdateChecker.Update) {
    val bar = LinearProgressIndicator(this).apply {
        isIndeterminate = true
        max = 100
    }
    val pad = (24 * resources.displayMetrics.density).toInt()
    val box = FrameLayout(this).apply {
        setPadding(pad, pad / 2, pad, 0)
        addView(bar)
    }
    var job: Job? = null
    val dialog = MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_KsuWebUI_ButtonDialog)
        .setTitle(R.string.update_downloading)
        .setView(box)
        .setCancelable(false)
        .setNegativeButton(android.R.string.cancel) { _, _ -> job?.cancel() }
        .create()
    dialog.show()

    job = lifecycleScope.launch {
        try {
            val error = withContext(Dispatchers.IO) {
                try {
                    val apk = UpdateChecker.download(update) { p ->
                        runOnUiThread {
                            bar.isIndeterminate = false
                            bar.setProgressCompat(p, true)
                        }
                    }
                    // the release APK must be newer than what is installed, otherwise the button would stay
                    val apkCode = UpdateChecker.apkVersionCode(apk)
                    if (apkCode != null && apkCode <= BuildConfig.VERSION_CODE) {
                        apk.delete()
                        return@withContext getString(R.string.update_not_newer, apkCode, BuildConfig.VERSION_CODE)
                    }
                    runOnUiThread {
                        dialog.setTitle(R.string.update_installing)
                        bar.isIndeterminate = true
                        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).isEnabled = false
                    }
                    val err = UpdateChecker.install(apk)
                    apk.delete()
                    err
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.message ?: e.javaClass.simpleName
                }
            }
            if (error != null) {
                MaterialAlertDialogBuilder(this@startUpdate, R.style.ThemeOverlay_KsuWebUI_ButtonDialog)
                    .setMessage(getString(R.string.update_failed, error))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        } finally {
            if (dialog.isShowing) dialog.dismiss()
        }
    }
}
