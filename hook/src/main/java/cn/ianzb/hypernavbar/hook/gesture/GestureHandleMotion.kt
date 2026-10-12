package cn.ianzb.hypernavbar.hook.gesture

/**
 * 小白条滑动跟随的阻尼弹簧。
 *
 * 只影响绘制偏移（Canvas 平移），不消费 / 修改输入事件，也不影响系统手势判定。
 */
internal class GestureHandleMotion {

    private var startX = 0f
    private var startY = 0f
    private var x = 0f
    private var y = 0f
    private var velocityX = 0f
    private var velocityY = 0f
    private var targetX = 0f
    private var targetY = 0f
    private var left = 0f
    private var right = 0f
    private var up = 0f
    private var down = 0f
    private var lastFrameAt = 0L
    private var holdUntil = 0L
    private var releasedAt = 0L
    private var dragging = false
    private var holdMs = DEFAULT_HOLD_MS
    private var followFactor = DEFAULT_FOLLOW_SCALE
    private var returnScale = 1f

    /** 应用用户可调参数：松手停留时长、跟随幅度、回弹速度倍率。 */
    fun configure(holdMs: Long, followFactor: Float, returnScale: Float) {
        this.holdMs = holdMs.coerceIn(0L, 2_000L)
        this.followFactor = followFactor.coerceIn(0f, 1.5f)
        this.returnScale = returnScale.coerceIn(0.25f, 4f)
    }

    fun start(rawX: Float, rawY: Float, now: Long) {
        advance(now)
        startX = rawX
        startY = rawY
        targetX = 0f
        targetY = 0f
        holdUntil = 0L
        releasedAt = 0L
        dragging = true
        lastFrameAt = now
    }

    fun move(rawX: Float, rawY: Float, maxLeft: Float, maxRight: Float, maxUp: Float, maxDown: Float, now: Long) {
        if (!dragging) return
        advance(now)
        left = maxLeft
        right = maxRight
        up = maxUp
        down = maxDown
        targetX = resisted((rawX - startX) * followFactor, maxLeft, maxRight)
        targetY = resisted((rawY - startY) * followFactor, maxUp, maxDown)
    }

    fun movedBeyond(rawX: Float, rawY: Float, slop: Float): Boolean {
        if (!dragging) return false
        val dx = rawX - startX
        val dy = rawY - startY
        return dx * dx + dy * dy >= slop * slop
    }

    fun release(now: Long) {
        if (!dragging) return
        advance(now)
        dragging = false
        releasedAt = now
        holdUntil = now + holdMs
    }

    /** 导航栏宿主 View 重建时调用，避免返回动画消耗在已消失的视图上。 */
    fun rebaseForNewHost(now: Long) {
        if (dragging) {
            lastFrameAt = now
        } else if (releasedAt != 0L && now - releasedAt < 1_000L && (targetX != 0f || targetY != 0f)) {
            lastFrameAt = now
            holdUntil = maxOf(holdUntil, now + HOST_HOLD_MS)
        }
    }

    fun reset() {
        dragging = false
        x = 0f
        y = 0f
        velocityX = 0f
        velocityY = 0f
        targetX = 0f
        targetY = 0f
        lastFrameAt = 0L
        holdUntil = 0L
        releasedAt = 0L
    }

    fun x(now: Long): Float {
        advance(now)
        return x
    }

    fun y(now: Long): Float {
        advance(now)
        return y
    }

    /** 是否仍需继续重绘。 */
    fun running(now: Long): Boolean {
        advance(now)
        val holding = dragging || now < holdUntil
        return kotlin.math.abs(x - (if (holding) targetX else 0f)) > SETTLED_DISTANCE ||
            kotlin.math.abs(y - (if (holding) targetY else 0f)) > SETTLED_DISTANCE ||
            kotlin.math.abs(velocityX) > SETTLED_SPEED ||
            kotlin.math.abs(velocityY) > SETTLED_SPEED ||
            (!dragging && now < holdUntil && (targetX != 0f || targetY != 0f))
    }

    private fun advance(now: Long) {
        if (lastFrameAt == 0L || now <= lastFrameAt) {
            lastFrameAt = now
            return
        }
        // 长时间挂起后按 16ms 步进补算，最多回补 1 秒
        var cursor = maxOf(lastFrameAt, now - 1_000L)
        while (cursor < now) {
            val stepMs = minOf(16L, now - cursor)
            cursor += stepMs
            val holding = dragging || cursor < holdUntil
            val stiffness = if (dragging) DRAG_STIFFNESS else RETURN_STIFFNESS * returnScale
            val damping = if (dragging) DRAG_DAMPING else (RETURN_DAMPING * kotlin.math.sqrt(returnScale))
            val dt = stepMs / 1_000f
            val goalX = if (holding) targetX else 0f
            val goalY = if (holding) targetY else 0f
            velocityX += ((goalX - x) * stiffness - velocityX * damping) * dt
            velocityY += ((goalY - y) * stiffness - velocityY * damping) * dt
            x = clamp(x + velocityX * dt, -left, right)
            y = clamp(y + velocityY * dt, -up, down)
        }
        lastFrameAt = now
        if (!dragging && now >= holdUntil &&
            kotlin.math.abs(x) < SETTLED_DISTANCE && kotlin.math.abs(y) < SETTLED_DISTANCE &&
            kotlin.math.abs(velocityX) < SETTLED_SPEED && kotlin.math.abs(velocityY) < SETTLED_SPEED
        ) {
            x = 0f
            y = 0f
            velocityX = 0f
            velocityY = 0f
            targetX = 0f
            targetY = 0f
        }
    }

    private fun resisted(displacement: Float, negativeLimit: Float, positiveLimit: Float): Float {
        val limit = if (displacement < 0f) negativeLimit else positiveLimit
        if (limit <= 0f) return 0f
        val amount = (limit * kotlin.math.tanh(kotlin.math.abs(displacement) / limit)).toFloat()
        return java.lang.Math.copySign(amount, displacement)
    }

    private fun clamp(value: Float, min: Float, max: Float): Float = maxOf(min, minOf(max, value))

    companion object {
        /** 默认松手停留时长（毫秒）。 */
        const val DEFAULT_HOLD_MS = 260L

        /** 默认跟随幅度（位移比例）。 */
        const val DEFAULT_FOLLOW_SCALE = 0.5f

        /** 默认单方向最大位移（dp）。 */
        const val DEFAULT_RANGE_DP = 24f

        /** 默认回弹速度倍率。 */
        const val DEFAULT_RETURN_SCALE = 1f

        private const val HOST_HOLD_MS = 200L
        private const val DRAG_STIFFNESS = 150f
        private const val DRAG_DAMPING = 25f
        private const val RETURN_STIFFNESS = 105f
        private const val RETURN_DAMPING = 16f
        private const val SETTLED_DISTANCE = 0.15f
        private const val SETTLED_SPEED = 2f
    }
}
