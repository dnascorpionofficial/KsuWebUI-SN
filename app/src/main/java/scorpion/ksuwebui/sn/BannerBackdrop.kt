package scorpion.ksuwebui.sn

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable

class BannerBackdrop(private val bmp: Bitmap, scrim: Int) : Drawable() {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val scrimPaint = Paint().apply { color = scrim }
    private val matrix = Matrix()

    override fun onBoundsChange(b: Rect) {
        val s = maxOf(b.width() / bmp.width.toFloat(), b.height() / bmp.height.toFloat())
        matrix.setScale(s, s)
        matrix.postTranslate(b.left + (b.width() - bmp.width * s) / 2f, b.top + (b.height() - bmp.height * s) / 2f)
    }

    override fun draw(c: Canvas) {
        c.drawBitmap(bmp, matrix, paint)
        c.drawRect(bounds, scrimPaint)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.OPAQUE
}
