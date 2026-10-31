package ani.saikou.glass

import android.app.Dialog
import android.os.Build
import android.view.View
import android.view.WindowManager
import ani.saikou.R
import java.util.function.Consumer

/**
 * Turns a dialog or bottom sheet into a glass layer: the screen behind it is blurred by the system
 * compositor (Android 12+). When blur is unavailable or switched off by the system (battery saver,
 * low-end devices, accessibility), it falls back to a deeper dim so the sheet still stands out.
 */
object GlassDialog {

    private const val BLUR_DIM = 0.18f
    private const val FALLBACK_DIM = 0.42f

    fun apply(dialog: Dialog) {
        val window = dialog.window ?: return
        val decor = window.decorView
        if (decor.getTag(R.id.glass_dialog_applied) == true) return
        decor.setTag(R.id.glass_dialog_applied, true)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            window.setDimAmount(FALLBACK_DIM)
            return
        }
        val radius = dialog.context.resources.getDimensionPixelSize(R.dimen.glass_window_blur_radius)
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        val onBlurChanged = Consumer<Boolean> { enabled ->
            window.attributes = window.attributes.apply { blurBehindRadius = if (enabled) radius else 0 }
            window.setDimAmount(if (enabled) BLUR_DIM else FALLBACK_DIM)
        }
        // Called right away with the current state, then on every change
        window.windowManager.addCrossWindowBlurEnabledListener(onBlurChanged)
        decor.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                window.windowManager.removeCrossWindowBlurEnabledListener(onBlurChanged)
                v.removeOnAttachStateChangeListener(this)
            }
        })
    }
}
