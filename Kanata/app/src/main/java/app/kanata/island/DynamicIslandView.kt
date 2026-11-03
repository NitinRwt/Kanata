package app.kanata.island

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.OvershootInterpolator
import app.kanata.R
import app.kanata.glass.GlassContainer
import kotlin.math.abs

/**
 * The island's glass surface. A tap toggles it open; a drag from anywhere on it, its buttons included, moves it.
 * On release it snaps to the nearest of start, centre and end at the height it was dropped, and
 * [onMoved] gets the new anchor (0 start, 1 centre, 2 end) and top, to lay it out there and save it.
 */
class DynamicIslandView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    GlassContainer(context, attrs) {

    var onTap: (() -> Unit)? = null
    var onMoved: ((anchor: Int, top: Int) -> Unit)? = null

    /** Highest the island may be dropped, so it stays clear of the status bar */
    var minTop = 0

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val margin = resources.getDimensionPixelSize(R.dimen.island_margin)
    private var downX = 0f
    private var downY = 0f
    private var startX = 0f
    private var startY = 0f
    private var dragging = false

    private fun down(ev: MotionEvent) {
        animate().cancel()
        downX = ev.rawX
        downY = ev.rawY
        startX = translationX
        startY = translationY
        dragging = false
    }

    private fun movedPastSlop(ev: MotionEvent) = abs(ev.rawX - downX) > slop || abs(ev.rawY - downY) > slop

    private fun startDrag() {
        dragging = true
        isPressed = false
        parent?.requestDisallowInterceptTouchEvent(true)
        animate().scaleX(1.04f).scaleY(1.04f).setDuration(120).start()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> down(ev)
            MotionEvent.ACTION_MOVE -> if (movedPastSlop(ev)) {
                startDrag()
                return true
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                down(ev)
                isPressed = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && movedPastSlop(ev)) startDrag()
                if (dragging) {
                    translationX = startX + ev.rawX - downX
                    translationY = startY + ev.rawY - downY
                }
            }
            MotionEvent.ACTION_UP -> {
                isPressed = false
                if (dragging) settle() else performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                isPressed = false
                if (dragging) settle()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        onTap?.invoke()
        return true
    }

    private fun settle() {
        dragging = false
        val parent = parent as? View ?: return
        // Where the island is drawn right now, in its parent's coordinates
        val x = left + translationX
        val y = top + translationY
        val centre = x + width / 2f
        val anchor = when {
            centre < parent.width / 3f -> 0
            centre > parent.width * 2 / 3f -> 2
            else -> 1
        }
        val newTop = y.toInt().coerceIn(minTop, (parent.height - height - margin).coerceAtLeast(minTop))
        val newLeft = when (anchor) {
            0 -> margin
            2 -> parent.width - width - margin
            else -> (parent.width - width) / 2
        }
        onMoved?.invoke(anchor, newTop)
        // Lay it out in its new slot now rather than next frame, then glide there from where it was dropped
        layout(newLeft, newTop, newLeft + width, newTop + height)
        translationX = x - newLeft
        translationY = y - newTop
        animate().translationX(0f).translationY(0f).scaleX(1f).scaleY(1f)
            .setInterpolator(OvershootInterpolator(1.1f)).setDuration(320).start()
    }
}
