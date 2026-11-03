package app.kanata.glass

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.kanata.R
import app.kanata.settings.UserInterfaceSettings
import kotlin.math.abs
import kotlin.math.max

/**
 * Lightweight 3D for lists and posters, built only on GPU view transforms (rotationX/Y with a camera distance):
 * no 3D engine, no per-frame allocations beyond the glare shader.
 *
 * Follows the "Layout animations" setting and the system "remove animations" switch, so motion-sensitive
 * users get the flat UI.
 */
object Depth3D {

    private const val ROW_ANGLE = 20f      // rotationY at the edges of a horizontal row
    private const val ROW_SCALE = 0.08f    // how much edge posters shrink
    private const val GRID_ANGLE = 8f      // rotationX at the top/bottom of a grid
    private const val GRID_SCALE = 0.05f
    private const val TILT_ANGLE = 12f     // max touch tilt
    private const val CAMERA = 8000f       // camera distance in dp: larger = flatter perspective

    fun isEnabled(ui: UserInterfaceSettings): Boolean {
        val systemAnimations = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()
        return ui.layoutAnimations && systemAnimations
    }

    private fun View.usePerspective() {
        cameraDistance = CAMERA * resources.displayMetrics.density
    }

    /**
     * Curves [recyclerView] in 3D as it scrolls: horizontal rows become a carousel, vertical grids a gently
     * curved wall. Only items bound by [adapter] are transformed, so headers sharing the list stay flat.
     */
    fun attachScroll(recyclerView: RecyclerView, adapter: RecyclerView.Adapter<*>) {
        if (recyclerView.getTag(R.id.depth3d_scroller) != null) return
        val scroller = DepthScroller(recyclerView, adapter)
        recyclerView.setTag(R.id.depth3d_scroller, scroller)
        recyclerView.addOnScrollListener(scroller)
        recyclerView.addOnLayoutChangeListener(scroller)
        recyclerView.post { scroller.apply() }
    }

    fun detachScroll(recyclerView: RecyclerView) {
        val scroller = recyclerView.getTag(R.id.depth3d_scroller) as? DepthScroller ?: return
        recyclerView.removeOnScrollListener(scroller)
        recyclerView.removeOnLayoutChangeListener(scroller)
        recyclerView.setTag(R.id.depth3d_scroller, null)
    }

    private class DepthScroller(private val rv: RecyclerView, private val adapter: RecyclerView.Adapter<*>) :
        RecyclerView.OnScrollListener(), View.OnLayoutChangeListener {

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) = apply()

        override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) = apply()

        fun apply() {
            val horizontal = (rv.layoutManager as? LinearLayoutManager ?: return).orientation == RecyclerView.HORIZONTAL
            val w = rv.width
            val h = rv.height
            if (w == 0 || h == 0) return
            for (i in 0 until rv.childCount) {
                val child = rv.getChildAt(i)
                if (rv.getChildViewHolder(child)?.bindingAdapter !== adapter) continue
                child.usePerspective()
                if (horizontal) {
                    // -1 at the left edge, 0 in the centre, 1 at the right edge
                    val f = ((child.left + child.right) / 2f - w / 2f) / (w / 2f)
                    child.rotationY = -f.coerceIn(-1.2f, 1.2f) * ROW_ANGLE
                    val scale = 1 - ROW_SCALE * abs(f).coerceAtMost(1f)
                    child.scaleX = scale
                    child.scaleY = scale
                } else {
                    val f = (((child.top + child.bottom) / 2f - h / 2f) / (h / 2f)).coerceIn(-1.2f, 1.2f)
                    child.rotationX = -f * GRID_ANGLE
                    val scale = 1 - GRID_SCALE * f * f
                    child.scaleX = scale
                    child.scaleY = scale
                }
            }
        }
    }

    /**
     * Makes [card] tilt toward the finger with a glass glare that follows it, springing back on release.
     * [touchView] is the view that receives the clicks; touches are observed, never consumed.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun attachTilt(touchView: View, card: CardView) {
        val glare = GlareDrawable(card)
        card.foreground = glare
        val offset = FloatArray(2)
        touchView.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    if (card.width == 0 || card.height == 0) return@setOnTouchListener false
                    offsetIn(card, touchView, offset)
                    val nx = ((e.x - offset[0]) / card.width).coerceIn(0f, 1f)
                    val ny = ((e.y - offset[1]) / card.height).coerceIn(0f, 1f)
                    card.usePerspective()
                    card.animate().cancel()
                    // The pressed side sinks into the screen
                    card.rotationY = (0.5f - nx) * 2 * TILT_ANGLE
                    card.rotationX = (ny - 0.5f) * 2 * TILT_ANGLE
                    glare.show(nx, ny)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    card.animate().rotationX(0f).rotationY(0f)
                        .setInterpolator(OvershootInterpolator(2.2f)).setDuration(420).start()
                    glare.hide()
                }
            }
            false
        }
    }

    /** Only the luminous glass edge, for posters whose rotation is driven by something else (e.g. a pager) */
    fun attachEdge(card: CardView) {
        card.foreground = GlareDrawable(card)
    }

    /**
     * 3D page turn for the trending banner: the cover card swings and recedes as its page slides away, over the
     * existing background parallax. [position] is ViewPager2's page offset (0 = centred).
     */
    fun turnPage(page: View, position: Float) {
        val card = page.findViewById<View>(R.id.itemCompactCard) ?: return
        if (position <= -1f || position >= 1f) {
            card.rotationY = 0f
            card.scaleX = 1f
            card.scaleY = 1f
            return
        }
        card.usePerspective()
        card.rotationY = -position * 40f
        val scale = 1 - 0.15f * abs(position)
        card.scaleX = scale
        card.scaleY = scale
    }

    /** [view]'s top-left inside [ancestor], ignoring transforms */
    private fun offsetIn(view: View, ancestor: View, out: FloatArray) {
        var x = 0f
        var y = 0f
        var v: View? = view
        while (v != null && v !== ancestor) {
            x += v.left
            y += v.top
            v = v.parent as? View
        }
        out[0] = x
        out[1] = y
    }

    /**
     * Foreground for poster cards: a fine luminous glass edge, plus a soft glare spot that appears under the finger.
     */
    private class GlareDrawable(private val card: CardView) : Drawable() {
        private val density = card.resources.displayMetrics.density
        private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = density
            color = Color.argb(70, 255, 255, 255)
        }
        private val glarePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        private val clip = Path()
        private var gx = 0.5f
        private var gy = 0.5f
        private var level = 0f
        private var animator: ValueAnimator? = null

        fun show(x: Float, y: Float) {
            gx = x
            gy = y
            if (level < 1f && animator?.isRunning != true) animateTo(1f, 120) else invalidateSelf()
        }

        fun hide() = animateTo(0f, 300)

        private fun animateTo(target: Float, duration: Long) {
            animator?.cancel()
            animator = ValueAnimator.ofFloat(level, target).setDuration(duration).apply {
                addUpdateListener { level = it.animatedValue as Float; invalidateSelf() }
                start()
            }
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            val radius = card.radius
            rect.set(b)
            if (level > 0.01f) {
                clip.reset()
                clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
                glarePaint.shader = RadialGradient(
                    b.left + gx * b.width(), b.top + gy * b.height(), max(b.width(), b.height()) * 0.75f,
                    intArrayOf(Color.argb((135 * level).toInt(), 255, 255, 255), Color.TRANSPARENT), null, Shader.TileMode.CLAMP
                )
                canvas.save()
                canvas.clipPath(clip)
                canvas.drawRect(rect, glarePaint)
                canvas.restore()
            }
            rect.inset(edge.strokeWidth / 2, edge.strokeWidth / 2)
            canvas.drawRoundRect(rect, radius, radius, edge)
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
