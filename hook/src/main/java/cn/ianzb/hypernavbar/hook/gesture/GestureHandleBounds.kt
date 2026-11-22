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
    }

    data class Edges(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        fun isFinite() = left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()
    }
}
