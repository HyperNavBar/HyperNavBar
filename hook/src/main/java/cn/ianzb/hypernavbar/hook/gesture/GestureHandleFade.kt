package cn.ianzb.hypernavbar.hook.gesture

/**
 * 小白条显隐的透明度动画（与可见性策略、3 秒时限相互独立）。
 *
 * 默认 220ms；触摸 / 滑动唤醒后的快速显示使用 90ms。
 */
internal class GestureHandleFade(hidden: Boolean) {

    private var from: Float = if (hidden) 0f else 1f
    private var target: Float = from
    private var startedAt: Long = 0L
    private var duration: Long = DURATION_MS

    /** 推进动画并返回当前帧透明度（0f..1f）。 */
    fun alpha(hidden: Boolean, now: Long, quickReveal: Boolean = false): Float {
        val next = if (hidden) 0f else 1f
        val current = value(now)
        if (next != target) {
            // 从当前帧反向过渡，避免跳到端点
            from = current
            target = next
            startedAt = now
            duration = if (!hidden && quickReveal) QUICK_REVEAL_DURATION_MS else DURATION_MS
        }
        return current
    }

    /** 动画是否仍在推进（用于请求下一帧重绘）。 */
    fun running(now: Long): Boolean = from != target && now - startedAt < duration

    private fun value(now: Long): Float {
        val progress = ((now - startedAt) / duration.toFloat()).coerceIn(0f, 1f)
        val eased = progress * progress * (3f - 2f * progress)
        return from + (target - from) * eased
    }

    companion object {
        const val DURATION_MS = 220L
        const val QUICK_REVEAL_DURATION_MS = 90L
    }
}
