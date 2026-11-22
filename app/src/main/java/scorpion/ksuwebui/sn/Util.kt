package scorpion.ksuwebui.sn

import android.app.Activity
import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.topjohnwu.superuser.Shell
import java.util.WeakHashMap
import scorpion.ksuwebui.sn.databinding.DialogConfirmBinding

inline fun <T> withNewRootShell(block: Shell.() -> T): T = createRootShell().use(block)

fun createRootShell(): Shell =
    Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER).build()

private val blurOwner = WeakHashMap<View, Any>()

fun MaterialAlertDialogBuilder.showBlurred(owner: Activity, onDismiss: (() -> Unit)? = null) =
    create().showBlurred(owner, onDismiss = onDismiss)

fun Dialog.showBlurred(
    owner: Activity,
    targets: List<View> = listOf(owner.findViewById(android.R.id.content)),
    onDismiss: (() -> Unit)? = null
) {
    window?.setDimAmount(0f)
    val token = Any()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val radius = 16f * owner.resources.displayMetrics.density
        val blur = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
        targets.forEach { blurOwner[it] = token; it.setRenderEffect(blur) }
        setOnDismissListener {
            targets.forEach { if (blurOwner[it] === token) { it.setRenderEffect(null); blurOwner.remove(it) } }
            onDismiss?.invoke()
        }
        show()
        return
    }
    val decor = owner.window.decorView
    if (decor.width == 0 || decor.height == 0) {
        show()
        return
    }
    val scale = 1f / 8f
    val snapshot = Bitmap.createBitmap(
        (decor.width * scale).toInt().coerceAtLeast(1),
        (decor.height * scale).toInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888
    )
    val overlays = ArrayList<Pair<View, BitmapDrawable>>()
    setOnDismissListener {
        overlays.forEach { (v, d) -> v.overlay.remove(d) }
        onDismiss?.invoke()
    }
    try {
        PixelCopy.request(owner.window, snapshot, { result ->
            if (result == PixelCopy.SUCCESS) {
                boxBlur(snapshot, 3, 2)
                val loc = IntArray(2)
                targets.forEach { v ->
                    if (v.width == 0 || v.height == 0) return@forEach
                    v.getLocationInWindow(loc)
                    val l = (loc[0] * scale).toInt().coerceIn(0, snapshot.width - 1)
                    val t = (loc[1] * scale).toInt().coerceIn(0, snapshot.height - 1)
                    val cw = (v.width * scale).toInt().coerceIn(1, snapshot.width - l)
                    val ch = (v.height * scale).toInt().coerceIn(1, snapshot.height - t)
                    val d = BitmapDrawable(owner.resources, Bitmap.createBitmap(snapshot, l, t, cw, ch))
                    d.isFilterBitmap = true
                    d.setBounds(0, 0, v.width, v.height)
                    v.overlay.add(d)
                    overlays.add(v to d)
                }
            }
            show()
        }, Handler(Looper.getMainLooper()))
    } catch (e: Exception) {
        show()
    }
}

private fun boxBlur(bmp: Bitmap, radius: Int, passes: Int) {
    val w = bmp.width
    val h = bmp.height
    val a = IntArray(w * h)
    val b = IntArray(w * h)
    bmp.getPixels(a, 0, w, 0, 0, w, h)
    repeat(passes) {
        boxBlurPass(a, b, w, h, radius, true)
        boxBlurPass(b, a, w, h, radius, false)
    }
    bmp.setPixels(a, 0, w, 0, 0, w, h)
}

private fun boxBlurPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
    val lines = if (horizontal) h else w
    val len = if (horizontal) w else h
    val size = 2 * r + 1
    for (line in 0 until lines) {
        fun px(i: Int): Int {
            val c = i.coerceIn(0, len - 1)
            return if (horizontal) src[line * w + c] else src[c * w + line]
        }
        var sr = 0
        var sg = 0
        var sb = 0
        for (i in -r..r) {
            val p = px(i)
            sr += p shr 16 and 0xFF
            sg += p shr 8 and 0xFF
            sb += p and 0xFF
        }
        for (i in 0 until len) {
            val out = (0xFF shl 24) or (sr / size shl 16) or (sg / size shl 8) or (sb / size)
            if (horizontal) dst[line * w + i] = out else dst[i * w + line] = out
            val add = px(i + r + 1)
            val sub = px(i - r)
            sr += (add shr 16 and 0xFF) - (sub shr 16 and 0xFF)
            sg += (add shr 8 and 0xFF) - (sub shr 8 and 0xFF)
            sb += (add and 0xFF) - (sub and 0xFF)
        }
    }
}

fun Activity.showConfirmDialog(
    title: CharSequence,
    message: CharSequence? = null,
    icon: Int? = null,
    confirmText: Int = R.string.yes,
    cancelText: Int = R.string.no,
    onCancel: (() -> Unit)? = null,
    blurred: Boolean = true,
    onConfirm: () -> Unit
): AlertDialog {
    val view = DialogConfirmBinding.inflate(layoutInflater)
    if (icon != null) {
        view.icon.setImageResource(icon)
        view.icon.visibility = View.VISIBLE
    }
    view.title.text = title
    if (message != null) view.message.text = message else view.message.visibility = View.GONE
    view.confirm.setText(confirmText)
    view.cancel.setText(cancelText)
    val dialog = MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_KsuWebUISN_ButtonDialog)
        .setView(view.root)
        .create()
    view.cancel.setOnClickListener {
        dialog.dismiss()
        onCancel?.invoke()
    }
    view.confirm.setOnClickListener {
        dialog.dismiss()
        onConfirm()
    }
    dialog.setOnCancelListener { onCancel?.invoke() }
    if (blurred) dialog.showBlurred(this) else dialog.show()
    return dialog
}
