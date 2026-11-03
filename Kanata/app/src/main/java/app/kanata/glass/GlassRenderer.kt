package app.kanata.glass

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.PowerManager
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.view.ViewParent
import android.view.ViewTreeObserver
import androidx.annotation.DimenRes
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import app.kanata.R
import kotlin.math.ceil
import kotlin.math.min

/**
 * Draws a liquid glass surface for [host]: the blurred content of [blurTarget] behind it (Android 12+),
 * a tint, a top sheen and a hairline edge. Styling comes from the `Glass` attrs, defaulting to `glass_tokens`.
 *
 * Without a blur target, on Android 11 and older, or on software canvases it draws the same surface
 * with the more opaque [staticTint] instead, so text on it stays readable.
 *
 * The host must call [draw] before drawing its own content, and [attach]/[detach] from its window callbacks.
 */
class GlassRenderer(private val host: View, attrs: AttributeSet?, @DimenRes defaultRadius: Int = R.dimen.glass_radius) {

    private val context = host.context

    var cornerRadius: Float
    var blurRadius: Float
    var tint: Int
    var staticTint: Int
    var borderColor: Int
    val pressable: Boolean
    private var blurTargetId = View.NO_ID

    private val borderHighlight = ContextCompat.getColor(context, R.color.glass_border_highlight)
    private val highlight = ContextCompat.getColor(context, R.color.glass_highlight)
    private val backdropBase = ContextCompat.getColor(context, R.color.bg)
    private val borderWidth = context.resources.getDimension(R.dimen.glass_border_width)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private var sheenHeight = -1

    private val backdrop = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Backdrop() else null

    init {
        val res = context.resources
        context.obtainStyledAttributes(attrs, R.styleable.Glass).apply {
            cornerRadius = getDimension(R.styleable.Glass_glassCornerRadius, res.getDimension(defaultRadius))
            blurRadius = getDimension(R.styleable.Glass_glassBlurRadius, res.getDimension(R.dimen.glass_blur_radius))
            tint = getColor(R.styleable.Glass_glassTint, ContextCompat.getColor(context, R.color.glass_tint))
            staticTint = getColor(R.styleable.Glass_glassStaticTint, ContextCompat.getColor(context, R.color.glass_tint_static))
            borderColor = getColor(R.styleable.Glass_glassBorderColor, ContextCompat.getColor(context, R.color.glass_border))
            pressable = getBoolean(R.styleable.Glass_glassPressable, true)
            blurTargetId = getResourceId(R.styleable.Glass_glassBlurTarget, View.NO_ID)
            recycle()
        }
        host.setWillNotDraw(false)
        // The outline gives the soft shadow its shape and clips the blur and children to the rounded glass
        host.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radiusFor(view.width, view.height))
            }
        }
        host.clipToOutline = true
    }

    /** Ripple sized to the glass shape, for clickable surfaces */
    fun ripple() = RippleDrawable(
        ColorStateList.valueOf(ContextCompat.getColor(context, R.color.glass_ripple)), null, ColorDrawable(Color.WHITE)
    )

    /** The view whose content is blurred behind the glass. Ignored if it contains the host, as that would blur itself */
    var blurTarget: View? = null
        set(value) {
            if (host.isAttachedToWindow) field?.let { stopWatching() }
            field = value?.takeUnless { host.isInside(it) }
            if (host.isAttachedToWindow) field?.let { startWatching() }
            host.invalidate()
        }

    // Live blur costs GPU time every frame the backdrop changes; Battery Saver gets the frosted look instead,
    // as the system does for its own window blur. Read on attach, not per frame, since it's a binder call
    private var powerSaving = false

    fun attach() {
        powerSaving = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
        if (blurTarget == null && blurTargetId != View.NO_ID) blurTarget = findTarget(blurTargetId)
        else if (blurTarget != null) startWatching()
    }

    fun detach() {
        if (blurTarget != null) stopWatching()
    }

    fun draw(canvas: Canvas) {
        val w = host.width
        val h = host.height
        if (w == 0 || h == 0) return
        val radius = radiusFor(w, h)
        rect.set(0f, 0f, w.toFloat(), h.toFloat())

        val blurred = backdrop?.draw(canvas, w, h) == true
        fill.color = if (blurred) tint else staticTint
        canvas.drawRoundRect(rect, radius, radius, fill)

        if (sheenHeight != h) {
            sheen.shader = LinearGradient(0f, 0f, 0f, h * 0.45f, highlight, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            sheenHeight = h
        }
        canvas.drawRoundRect(rect, radius, radius, sheen)

        // Dark outer hairline, then a fainter light one just inside it, reads as a lit glass edge
        stroke.strokeWidth = borderWidth
        stroke.color = borderColor
        rect.inset(borderWidth / 2, borderWidth / 2)
        canvas.drawRoundRect(rect, radius, radius, stroke)
        stroke.strokeWidth = borderWidth / 2
        stroke.color = borderHighlight
        rect.inset(borderWidth, borderWidth)
        canvas.drawRoundRect(rect, radius, radius, stroke)
    }

    private fun radiusFor(w: Int, h: Int) = min(cornerRadius, min(w, h) / 2f)

    private fun View.isInside(other: View): Boolean {
        var p: ViewParent? = parent
        while (p != null) {
            if (p === other) return true
            p = p.parent
        }
        return this === other
    }

    private fun findTarget(id: Int): View? {
        var p: ViewParent? = host.parent
        while (p is View) {
            p.findViewById<View>(id)?.let { if (!host.isInside(it)) return it }
            p = p.parent
        }
        return null
    }

    // The backdrop only changes when the target redraws or scrolls. Only react when the target itself is dirty:
    // invalidating the glass unconditionally here would schedule another frame and redraw forever
    private val onPreDraw = ViewTreeObserver.OnPreDrawListener {
        if (blurTarget?.isDirty == true) host.invalidate()
        true
    }
    private val onScroll = ViewTreeObserver.OnScrollChangedListener { host.invalidate() }
    private var observer: ViewTreeObserver? = null

    private fun startWatching() {
        if (backdrop == null || observer != null) return
        observer = host.viewTreeObserver.also {
            it.addOnPreDrawListener(onPreDraw)
            it.addOnScrollChangedListener(onScroll)
        }
    }

    private fun stopWatching() {
        observer?.takeIf { it.isAlive }?.apply {
            removeOnPreDrawListener(onPreDraw)
            removeOnScrollChangedListener(onScroll)
        }
        observer = null
    }

    /**
     * Records the target's current display list into a RenderNode with a blur RenderEffect and draws the
     * part that sits under the host. Everything stays on the GPU: no bitmaps are copied.
     */
    private companion object {
        const val DOWNSAMPLE = 4f
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private inner class Backdrop {
        private val node = RenderNode("GlassBackdrop")
        private var effectRadius = -1f
        private val hostLocation = IntArray(2)
        private val targetLocation = IntArray(2)

        fun draw(canvas: Canvas, w: Int, h: Int): Boolean {
            val target = blurTarget ?: return false
            if (powerSaving || !canvas.isHardwareAccelerated || blurRadius <= 0f || !target.isAttachedToWindow) return false
            if (target.width == 0 || target.height == 0) return false

            if (effectRadius != blurRadius) {
                node.setRenderEffect(
                    RenderEffect.createBlurEffect(blurRadius / DOWNSAMPLE, blurRadius / DOWNSAMPLE, Shader.TileMode.CLAMP)
                )
                effectRadius = blurRadius
            }
            host.getLocationInWindow(hostLocation)
            target.getLocationInWindow(targetLocation)

            // Only record (and so only blur) the part of the target under the glass, plus a margin the blur
            // samples from. Blurring the whole target would cost a full-screen blur per frame for a small bar
            val margin = blurRadius.toInt() * 2
            val left = hostLocation[0] - targetLocation[0] - margin
            val top = hostLocation[1] - targetLocation[1] - margin
            // Heavy blur removes fine detail anyway, so blur a quarter-resolution copy and scale it back up:
            // the same look for roughly 1/16 of the pixel work
            val width = ceil((w + margin * 2) / DOWNSAMPLE).toInt()
            val height = ceil((h + margin * 2) / DOWNSAMPLE).toInt()
            node.setPosition(0, 0, width, height)
            val recording = node.beginRecording(width, height)
            recording.scale(1 / DOWNSAMPLE, 1 / DOWNSAMPLE)
            recording.translate(-(left + target.scrollX).toFloat(), -(top + target.scrollY).toFloat())
            target.draw(recording)
            node.endRecording()

            // Opaque base so gaps in the target don't let sharp content show through the blur
            fill.color = backdropBase
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill)
            canvas.save()
            canvas.translate(-margin.toFloat(), -margin.toFloat())
            canvas.scale(DOWNSAMPLE, DOWNSAMPLE)
            canvas.drawRenderNode(node)
            canvas.restore()
            return true
        }
    }
}
