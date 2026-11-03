package app.kanata.glass

import android.animation.AnimatorInflater
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.FrameLayout
import androidx.annotation.DimenRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import app.kanata.R
import app.kanata.navBarHeight
import com.google.android.material.button.MaterialButton
import com.google.android.material.internal.ViewUtils
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView

/**
 * A rounded liquid glass surface that blurs what is behind it. Set `app:glassBlurTarget` to the sibling
 * view it floats over (e.g. the list or banner); without one it is frosted instead of blurred.
 */
open class GlassContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    @DimenRes defaultRadius: Int = R.dimen.glass_radius,
) : FrameLayout(context, attrs, defStyleAttr) {

    val glass = GlassRenderer(this, attrs, defaultRadius)

    init {
        // The ripple only shows when clickable; the press scale can be turned off for views that animate their own scale
        foreground = glass.ripple()
        if (glass.pressable) stateListAnimator = AnimatorInflater.loadStateListAnimator(context, R.animator.glass_press)
    }

    fun setBlurTarget(target: View?) {
        glass.blurTarget = target
    }

    override fun draw(canvas: Canvas) {
        glass.draw(canvas)
        super.draw(canvas)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        glass.attach()
    }

    override fun onDetachedFromWindow() {
        glass.detach()
        super.onDetachedFromWindow()
    }
}

/** Glass bar for the top of a screen, kept clear of the status bar */
class GlassTopBar @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    GlassContainer(context, attrs, 0, R.dimen.glass_radius_large) {
    private var initialTopMargin: Int? = null

    init {
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updateLayoutParams<MarginLayoutParams> {
                val initial = initialTopMargin ?: topMargin.also { initialTopMargin = it }
                topMargin = initial + top
            }
            insets
        }
    }
}

/** Floating pill bar for the bottom of a screen, e.g. around the main tab bar */
class GlassBottomBar @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    GlassContainer(context, attrs, 0, R.dimen.glass_radius_pill)

/**
 * Bottom navigation on glass. Same window inset handling as the app's CustomBottomNavBar,
 * so it can replace it directly.
 */
class GlassNavigationBar @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    BottomNavigationView(context, attrs) {

    val glass = GlassRenderer(this, attrs, R.dimen.glass_radius_large)

    init {
        setBackgroundColor(Color.TRANSPARENT)
        elevation = 0f
        ViewUtils.doOnApplyWindowInsets(this) { view, insets, initialPadding ->
            initialPadding.bottom = 0
            updateLayoutParams<MarginLayoutParams> { bottomMargin = navBarHeight }
            initialPadding.applyToView(view)
            insets
        }
    }

    override fun draw(canvas: Canvas) {
        glass.draw(canvas)
        super.draw(canvas)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        glass.attach()
    }

    override fun onDetachedFromWindow() {
        glass.detach()
        super.onDetachedFromWindow()
    }
}

/**
 * Glass card for content and list items. Frosted rather than live-blurred: lists hold many cards and a
 * blur per card would cost too much. Styled by `?attr/glassCardStyle`.
 */
class GlassCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.glassCardStyle,
) : MaterialCardView(context, attrs, defStyleAttr) {

    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlight = ContextCompat.getColor(context, R.color.glass_highlight)
    private val bounds = RectF()

    init {
        setWillNotDraw(false)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        sheen.shader = LinearGradient(0f, 0f, 0f, h * 0.45f, highlight, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        bounds.set(0f, 0f, w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRoundRect(bounds, radius, radius, sheen)
    }
}

/** Pill shaped glass button, styled by `?attr/glassButtonStyle` */
class GlassButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.glassButtonStyle,
) : MaterialButton(context, attrs, defStyleAttr)
