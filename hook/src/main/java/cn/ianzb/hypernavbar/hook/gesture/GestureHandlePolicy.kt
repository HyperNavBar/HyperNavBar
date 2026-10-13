package cn.ianzb.hypernavbar.hook.gesture

/**
 * 前台应用身份与 3 秒时限逻辑，独立于 Android 与视图生命周期。
 */
internal class GestureHandlePolicy {

    private var rules: Map<String, String> = emptyMap()
    private var follow: String = GestureVisibility.FOLLOW_RULES
    private var globalMode: String = GestureVisibility.ALWAYS_SHOW
    private var packageName: String? = null
    private var activity: String? = null
    private var taskId: Int = -1
    private var shownAt: Long = 0L
    private var touchRevealUntil: Long = 0L
    private var swipeRevealUntil: Long = 0L
    private var touchHeld: Boolean = false
    private var swipeHeld: Boolean = false
    private var lastTouchAt: Long = 0L
    private var lastSwipeAt: Long = 0L

    fun configure(nextRules: Map<String, String>, nextFollow: String, nextGlobalMode: String, now: Long) {
        if (rules == nextRules && follow == nextFollow && globalMode == nextGlobalMode) return
        val previous = mode()
        rules = nextRules
        follow = nextFollow
        globalMode = nextGlobalMode
        if (previous != mode()) shownAt = now
    }

    fun foreground(nextPackage: String?, nextActivity: String?, nextTaskId: Int, now: Long) {
        val previous = mode()
        val changed = activity != nextActivity || taskId != nextTaskId
        packageName = nextPackage
        activity = nextActivity
        taskId = nextTaskId
        if (changed || previous != mode()) shownAt = now
    }

    fun reveal(now: Long) {
        shownAt = now
    }

    fun touchDown(now: Long) {
        touchHeld = true
        lastTouchAt = now
        revealOnTouch(now)
    }

    fun touchEvent(now: Long) {
        if (!touchHeld) return
        lastTouchAt = now
        revealOnTouch(now)
    }

    fun touchUp(now: Long) {
        if (!touchHeld) return
        touchHeld = false
        revealOnTouch(now)
    }

    fun swipeDown(now: Long) {
        swipeHeld = true
        lastSwipeAt = now
        revealOnSwipe(now)
    }

    fun swipeEvent(now: Long) {
        if (!swipeHeld) return
        lastSwipeAt = now
        revealOnSwipe(now)
    }

    fun swipeUp(now: Long) {
        if (!swipeHeld) return
        swipeHeld = false
        revealOnSwipe(now)
    }

    private fun revealOnTouch(now: Long) {
        touchRevealUntil = now + AUTO_HIDE_DELAY_MS
    }

    private fun revealOnSwipe(now: Long) {
        swipeRevealUntil = now + AUTO_HIDE_DELAY_MS
    }

    /**
     * 触摸唤醒 / 滑动跟随是否仍处于「按住」状态。
     *
     * 输入监听有可能丢失 UP / CANCEL（例如手势被系统接管），若只看布尔标记会让小白条
     * 永久停留在唤醒态（表现为怎么都隐藏不了），因此按住状态带 [HOLD_STALE_MS] 超时。
     */
    private fun holdActive(held: Boolean, lastAt: Long, now: Long): Boolean =
        held && now - lastAt < HOLD_STALE_MS

    fun touchRevealActive(now: Long): Boolean =
        holdActive(touchHeld, lastTouchAt, now) || now < touchRevealUntil

    fun swipeRevealActive(now: Long): Boolean =
        holdActive(swipeHeld, lastSwipeAt, now) || now < swipeRevealUntil

    fun clearTouchReveal() {
        touchHeld = false
        touchRevealUntil = 0L
        lastTouchAt = 0L
    }

    fun clearSwipeReveal() {
        swipeHeld = false
        swipeRevealUntil = 0L
        lastSwipeAt = 0L
    }

    /** 当前前台应用应使用的内部模式；null 表示保持系统原样。 */
    fun mode(): String? = GestureVisibility.resolve(packageName, rules, follow, globalMode)

    /** 当前是否需要隐藏小白条。 */
    fun hidden(now: Long, systemHidden: Boolean): Boolean {
        if (touchRevealActive(now) || swipeRevealActive(now)) return false
        return when (mode()) {
            GestureVisibility.MODE_HIDE -> true
            GestureVisibility.MODE_SHOW -> false
            GestureVisibility.MODE_AUTO -> now - shownAt >= AUTO_HIDE_DELAY_MS
            else -> systemHidden
        }
    }

    /** 距离下一次需要刷新（自动隐藏 / 唤醒结束）的毫秒数；0 表示无需刷新。 */
    fun remaining(now: Long): Long {
        val autoHide = if (mode() == GestureVisibility.MODE_AUTO) {
            (AUTO_HIDE_DELAY_MS - (now - shownAt)).coerceAtLeast(0L)
        } else 0L
        // 按住状态也要安排一次刷新：到超时点需要重新评估（避免丢失 UP 后卡在唤醒态）
        val touchHold = if (touchHeld) (HOLD_STALE_MS - (now - lastTouchAt)).coerceAtLeast(0L) else 0L
        val swipeHold = if (swipeHeld) (HOLD_STALE_MS - (now - lastSwipeAt)).coerceAtLeast(0L) else 0L
        val touch = if (touchHeld) 0L else (touchRevealUntil - now).coerceAtLeast(0L)
        val swipe = if (swipeHeld) 0L else (swipeRevealUntil - now).coerceAtLeast(0L)
        return maxOf(autoHide, touchHold, swipeHold, touch, swipe)
    }

    companion object {
        /** 自动隐藏模式的显示时长。 */
        const val AUTO_HIDE_DELAY_MS = 3_000L

        /** 按住状态的最长无事件时长；超过即视为已松手（防止丢失 UP / CANCEL 卡在唤醒态）。 */
        const val HOLD_STALE_MS = 10_000L
    }
}
