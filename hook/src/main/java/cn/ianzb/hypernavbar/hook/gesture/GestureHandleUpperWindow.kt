package cn.ianzb.hypernavbar.hook.gesture

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import cn.ianzb.hypernavbar.hook.xposed.HookHelper
import java.lang.ref.WeakReference
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Visual-only extension above the navigation bar window. All calls run on the main thread. */
internal class GestureHandleUpperWindow(private val trace: GestureHandleTrace) {
    private var panel: PillView? = null
    private var wm: WindowManager? = null
    private var owner = WeakReference<View>(null)
    private var requestedHeight = 0
    private var attempted = false
    private var finishing = false
    private val cleanup = Runnable { close() }
    private val handleLocation = IntArray(2)
    private val panelLocation = IntArray(2)
    private val visible = Rect()

    fun prepare(handle: View, cap: Float) {
        if (handle.resources.configuration.orientation != Configuration.ORIENTATION_PORTRAIT) return
        val display = handle.display ?: return
        if (!handle.isShown || !handle.isAttachedToWindow || !cap.isFinite() || cap !in 1f..512f) return
        val height = handle.rootView.height + ceil(cap + 4f * handle.resources.displayMetrics.density).toInt()
        if (owner.get() !== handle) close()
        if (attempted) {
            if (finishing) {
                finishing = false
                panel?.removeCallbacks(cleanup)
                panel?.postDelayed(cleanup, 1_600L)
            }
            return
        }
        attempted = true
        owner = WeakReference(handle)
        runCatching {
            val context = handle.context.createWindowContext(display, NAVIGATION_BAR_PANEL, null)
            val manager = context.getSystemService(WindowManager::class.java) ?: return
            val view = PillView(context, trace)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                height,
                NAVIGATION_BAR_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.LEFT
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                setFitInsetsTypes(0)
                title = "HyperNavBar gesture animation"
            }
            // Android can block touches behind an untrusted overlay even with NOT_TOUCHABLE.
            // Leave the stock window in use if this privileged operation is unavailable.
            WindowManager.LayoutParams::class.java.getMethod("setTrustedOverlay").invoke(params)
            manager.addView(view, params)
            requestedHeight = height
            wm = manager
            panel = view
            view.postDelayed(cleanup, 1_600L)
        }.onFailure { HookHelper.log("GestureHandle: upper animation window unavailable", it) }
    }

    /** Position of the visible portion of the panel in the original handle's coordinates. */
    fun viewport(handle: View): GestureHandleBounds.Edges? {
        val view = panel ?: return null
        if (owner.get() !== handle || !handle.isShown || !view.isShown ||
            handle.resources.configuration.orientation != Configuration.ORIENTATION_PORTRAIT ||
            !view.isLaidOut || view.height <= 0 || !view.getLocalVisibleRect(visible)
        ) {
            trace.sample("panel-unavailable") {
                "owner=${owner.get() === handle} hostShown=${handle.isShown} panelShown=${view.isShown}" +
                    " laidOut=${view.isLaidOut} height=${view.height} requested=$requestedHeight"
            }
            return null
        }
        handle.getLocationOnScreen(handleLocation)
        view.getLocationOnScreen(panelLocation)
        val x = (panelLocation[0] - handleLocation[0]).toFloat()
        val y = (panelLocation[1] - handleLocation[1]).toFloat()
        val bounds = GestureHandleBounds.Edges(x + visible.left, y + visible.top, x + visible.right, y + visible.bottom)
        trace.sample("panel") { "owner=(${handleLocation[0]},${handleLocation[1]}) viewport=$bounds height=${view.height}" }
        return bounds
    }

    fun show(handle: View, pill: RectF, dx: Float, dy: Float, paint: Paint, radius: Float, fade: Float): Boolean {
        val view = panel ?: return false
        val area = viewport(handle) ?: return false
        if (radius <= 0f || !radius.isFinite() ||
            pill.left + dx < area.left || pill.right + dx > area.right ||
            pill.top + dy < area.top || pill.bottom + dy > area.bottom
        ) {
            trace.sample("panel-rejected") { "pill=$pill offset=($dx,$dy) radius=$radius bounds=$area" }
            return false
        }
        var alpha = fade
        var target: View? = handle
        while (target != null) {
            alpha *= target.alpha * target.transitionAlpha
            target = target.parent as? View
        }
        view.removeCallbacks(cleanup)
        finishing = false
        view.postDelayed(cleanup, 1_600L)
        view.paint.set(paint)
        view.paint.alpha = (paint.alpha * alpha).roundToInt().coerceIn(0, 255)
        view.pill.set(pill)
        view.pill.offset(dx + handleLocation[0] - panelLocation[0], dy + handleLocation[1] - panelLocation[1])
        view.radius = radius
        trace.sample("panel-accepted") { "pill=${view.pill} radius=$radius alpha=${view.paint.alpha} dy=$dy" }
        view.invalidate()
        return true
    }

    fun clear(handle: View) {
        if (owner.get() !== handle) return
        panel?.let {
            if (!it.pill.isEmpty) {
                it.pill.setEmpty()
                it.invalidate()
            }
        }
    }

    fun finishAfterAnimation(handle: View) {
        if (owner.get() !== handle) return
        val view = panel
        if (view == null) {
            close()
        } else if (!finishing) {
            finishing = true
            view.removeCallbacks(cleanup)
            view.postDelayed(cleanup, 800L)
        }
    }

    fun close(handle: View? = null) {
        if (handle != null && owner.get() !== handle) return
        val view = panel
        val manager = wm
        panel = null
        wm = null
        owner.clear()
        attempted = false
        finishing = false
        if (view != null) {
            view.removeCallbacks(cleanup)
            view.pill.setEmpty()
            view.invalidate()
            runCatching { manager?.removeView(view) }
        }
    }

    private class PillView(context: Context, private val trace: GestureHandleTrace) : View(context) {
        val pill = RectF()
        val paint = Paint()
        var radius = 0f

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        override fun onDraw(canvas: Canvas) {
            if (!pill.isEmpty) {
                trace.sample("panel-draw") { "pill=$pill alpha=${paint.alpha}" }
                canvas.drawRoundRect(pill, radius, radius, paint)
            }
        }
    }

    companion object {
        // WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL (@hide).
        private const val NAVIGATION_BAR_PANEL = 2024
    }
}
