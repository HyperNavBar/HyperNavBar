package cn.ianzb.hypernavbar.hook.gesture

/** Maximum translation that keeps the entire pill inside its drawable area. */
internal data class GestureHandleBounds(
    val left: Float,
    val right: Float,
    val up: Float,
    val down: Float,
) {
    fun clampX(x: Float): Float = x.coerceIn(-left, right)
    fun clampY(y: Float): Float = y.coerceIn(-up, down)

    companion object {
        val NONE = GestureHandleBounds(0f, 0f, 0f, 0f)

        fun fit(pill: Edges, viewport: Edges, cap: Float, margin: Float): GestureHandleBounds {
            if (!cap.isFinite() || cap <= 0f || !margin.isFinite() || margin < 0f ||
                !pill.isFinite() || !viewport.isFinite() || pill.left >= pill.right || pill.top >= pill.bottom ||
                pill.left < viewport.left || pill.top < viewport.top ||
                pill.right > viewport.right || pill.bottom > viewport.bottom
            ) return NONE

            return GestureHandleBounds(
                (pill.left - viewport.left - margin).coerceIn(0f, cap),
                (viewport.right - pill.right - margin).coerceIn(0f, cap),
                (pill.top - viewport.top - margin).coerceIn(0f, cap),
                (viewport.bottom - pill.bottom - margin).coerceIn(0f, cap),
            )
        }

        fun fitUpper(pill: Edges, viewport: Edges, panel: Edges?, cap: Float, margin: Float):
            Pair<GestureHandleBounds, GestureHandleBounds> {
            val local = fit(pill, viewport, cap, margin)
            if (panel == null || !panel.isFinite() || pill.top < viewport.top || pill.bottom > viewport.bottom ||
                pill.left < viewport.left || pill.right > viewport.right ||
                panel.left > pill.left - local.left || panel.right < pill.right + local.right ||
                panel.bottom < pill.bottom || panel.top >= viewport.top
            ) return local to local
            val upper = fit(pill, panel, cap, margin)
            return local to local.copy(up = maxOf(local.up, upper.up))
        }
    }

    data class Edges(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        fun isFinite() = left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()
    }
}
