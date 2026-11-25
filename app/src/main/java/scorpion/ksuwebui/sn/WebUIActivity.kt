package scorpion.ksuwebui.sn

import android.app.ActivityManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WebUIActivity : ComponentActivity(), FileSystemService.Listener {
    
    private val webUIState = WebUIState()
    private var exitDialog: AlertDialog? = null
    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            val web = webUIState.webView
            if (web == null) {
                confirmExit()
                return
            }
            closeErudaIfOpen(web) { closed ->
                if (closed) return@closeErudaIfOpen
                if (web.canGoBack()) web.goBack() else confirmExit()
            }
        }
    }
    internal lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>
    private lateinit var saveFileLauncher: ActivityResultLauncher<Intent>
    private var pendingSaveData: ByteArray? = null
    private var pageDark: Boolean? = null
    private val bgPrefs by lazy { getSharedPreferences("page_bg", MODE_PRIVATE) }
    private val bgKey get() = "bg_${intent.getStringExtra("id")}"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, backCallback)
        AppList.invalidate()

        if (bgPrefs.contains(bgKey)) applyPageColor(bgPrefs.getInt(bgKey, 0))

        MonetColorsProvider.updateCss(this)

        val progressLayout = FrameLayout(this).apply {
            addView(CircularProgressIndicator(this@WebUIActivity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            })
        }
        setContentView(progressLayout)

        fileChooserLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            webUIState.filePathCallback?.onReceiveValue(uris)
            webUIState.filePathCallback = null
        }

        saveFileLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val uri = if (result.resultCode == RESULT_OK) result.data?.data else null
            val data = pendingSaveData
            if (uri != null && data != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        contentResolver.openOutputStream(uri)?.use { it.write(data) }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        pendingSaveData = null
                    }
                }
            } else {
                pendingSaveData = null
            }
        }

        webUIState.onSaveFileRequest = { data, fileName, mimeType ->
            pendingSaveData = data
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = mimeType
                putExtra(Intent.EXTRA_TITLE, fileName)
            }
            try {
                saveFileLauncher.launch(intent)
            } catch (e: Exception) {
                pendingSaveData = null
                e.printStackTrace()
            }
        }

        if (prepareWebView(webUIState)) {
            setupWebUIScreen(webUIState, FrameLayout(this))
            updateTaskDescription()
            FileSystemService.start(this)
        }
    }

    private fun updateTaskDescription() {
        val name = webUIState.moduleName
        if (name.isNotEmpty()) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                setTaskDescription(ActivityManager.TaskDescription(name))
            } else {
                val taskDescription = ActivityManager.TaskDescription.Builder().setLabel(name).build()
                setTaskDescription(taskDescription)
            }
        }
    }

    override fun onServiceAvailable(fs: FileSystemManager) {
        initWebView(fs, webUIState)
    }

    override fun onLaunchFailed() {
        showToast(this, R.string.please_grant_root)
        finish()
    }

    fun enableEdgeToEdge(enable: Boolean = true) {
        runOnUiThread {
            if (enable) {
                (this as ComponentActivity).enableEdgeToEdge()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            applyBarIcons()
            webUIState.isEdgeToEdgeEnabled = enable
            val container = webUIState.webView?.parent as? android.view.View
            container?.let { ViewCompat.requestApplyInsets(it) }

            if (enable) {
                webUIState.webView?.evaluateJavascript(webUIState.insets.js, null)
            }
        }
    }

    fun onPageColor(rgb: Int) = runOnUiThread {
        val color = if (rgb < 0) null else rgb or Color.BLACK
        applyPageColor(color)
        bgPrefs.edit { if (color == null) remove(bgKey) else putInt(bgKey, color) }
    }

    private fun applyPageColor(color: Int?) {
        pageDark = color?.let { ColorUtils.calculateLuminance(it) < 0.5 }
        color?.let { window.setBackgroundDrawable(ColorDrawable(it)) }
        applyBarIcons()
    }

    private fun applyBarIcons() {
        val dark = pageDark
            ?: ((resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyBarIcons()
        webUIState.webView?.settings?.textZoom = (newConfig.fontScale * 100).toInt()
        MonetColorsProvider.updateCss(this)
    }

    private fun confirmExit() {
        if (exitDialog?.isShowing == true) return
        exitDialog = showConfirmDialog(getString(R.string.confirm_exit_webui)) {
            backCallback.isEnabled = false
            onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exitDialog?.dismiss()
        FileSystemService.removeListener(this)
        webUIState.webviewInterface?.destroy()
        webUIState.webView?.destroy()
    }
}
