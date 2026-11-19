package scorpion.ksuwebui.sn

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.LinearLayout
import androidx.core.animation.doOnEnd
import androidx.core.view.isVisible
import scorpion.ksuwebui.sn.databinding.ItemSheetGroupBinding

private const val DURATION = 240L
private val EASING = PathInterpolator(0.4f, 0f, 0.2f, 1f)

class SheetGroups(private val container: ViewGroup) {
    private class Section(val body: View, val chevron: View, var open: Boolean, var anim: ValueAnimator? = null)

    private val sections = mutableListOf<Section>()

    fun add(icon: Int, title: Int, open: Boolean): LinearLayout {
        val b = ItemSheetGroupBinding.inflate(LayoutInflater.from(container.context), container, false)
        b.icon.setImageResource(icon)
        b.title.setText(title)
        b.body.isVisible = open
        b.chevron.rotation = if (open) 180f else 0f
        val section = Section(b.body, b.chevron, open)
        b.header.setOnClickListener { select(section) }
        sections += section
        container.addView(b.root)
        return b.body
    }

    private fun select(target: Section) {
        for (s in sections) {
            val open = s === target && !s.open
            if (s.open != open) animate(s, open)
        }
    }

    private fun animate(s: Section, open: Boolean) {
        s.open = open
        s.anim?.cancel()
        s.chevron.animate().rotation(if (open) 180f else 0f).setDuration(DURATION).start()
        val body = s.body
        val from = if (body.isVisible) body.height else 0
        body.measure(
            View.MeasureSpec.makeMeasureSpec((body.parent as View).width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.UNSPECIFIED
        )
        val full = body.measuredHeight
        body.isVisible = true
        s.anim = ValueAnimator.ofInt(from, if (open) full else 0).apply {
            duration = DURATION
            interpolator = EASING
            addUpdateListener {
                val h = it.animatedValue as Int
                body.layoutParams.height = h
                body.alpha = if (full > 0) h.toFloat() / full else 0f
                body.requestLayout()
            }
            doOnEnd {
                if (s.open == open) {
                    body.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    body.alpha = 1f
                    body.isVisible = open
                }
            }
            start()
        }
    }
}
