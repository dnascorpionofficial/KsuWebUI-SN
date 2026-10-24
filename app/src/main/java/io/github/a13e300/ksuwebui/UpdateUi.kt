package io.github.a13e300.ksuwebui

import android.content.DialogInterface
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Changelog + Download / Later. */
fun AppCompatActivity.showUpdateDialog(update: UpdateChecker.Update) {
    MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_KsuWebUI_ButtonDialog)
        .setTitle(getString(R.string.update_title, update.version))
        .setMessage(update.changelog.ifEmpty { getString(R.string.update_no_changelog) })
        .setPositiveButton(R.string.update_download) { _, _ -> startUpdate(update) }
        .setNegativeButton(R.string.update_later, null)
        .show()
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
