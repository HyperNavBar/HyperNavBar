package cn.ianzb.hypernavbar.hook.gesture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** 校验滑动跟随的阻尼弹簧：拖动受限、松手回零。 */
class GestureHandleMotionTest {

    @Test
    fun dragFollowsWithResistanceAndReturnsToZero() {
        val motion = GestureHandleMotion()
        motion.start(0f, 0f, 0L)
        motion.move(200f, 0f, 24f, 24f, 24f, 24f, 16L)

        val dragged = motion.x(32L)
        assertTrue("dragged=$dragged", dragged > 0f && dragged <= 24f)

        motion.release(32L)
        assertTrue(motion.running(100L))

        val settled = motion.x(32L + 2_000L)
        assertTrue("settled=$settled", abs(settled) < 0.5f)
        assertFalse(motion.running(32L + 2_000L))
    }

    @Test
    fun moveBeyond_slopDetection() {
        val motion = GestureHandleMotion()
        motion.start(0f, 0f, 0L)
        motion.move(5f, 0f, 24f, 24f, 24f, 24f, 0L)

        assertFalse(motion.movedBeyond(4f, 0f, 16f))
        assertTrue(motion.movedBeyond(20f, 0f, 16f))
    }

    @Test
    fun configure_followScaleControlsAmplitude() {
        val half = GestureHandleMotion().apply { configure(260L, 0.5f, 1f) }
        val full = GestureHandleMotion().apply { configure(260L, 1f, 1f) }
        for (motion in listOf(half, full)) {
            motion.start(0f, 0f, 0L)
            motion.move(20f, 0f, 24f, 24f, 24f, 24f, 16L)
        }

        assertTrue(full.x(32L) > half.x(32L))
    }

    @Test
    fun configure_holdDelaysTheReturn() {
        val immediate = GestureHandleMotion().apply { configure(0L, 0.5f, 1f) }
        val delayed = GestureHandleMotion().apply { configure(500L, 0.5f, 1f) }
        for (motion in listOf(immediate, delayed)) {
            motion.start(0f, 0f, 0L)
            motion.move(200f, 0f, 24f, 24f, 24f, 24f, 16L)
            motion.release(32L)
        }

        // 松手 400ms 后：立即回弹的位移已基本归零，500ms 停留期的仍在跟随位置
        assertTrue("immediate=${immediate.x(432L)}", immediate.x(432L) < 5f)
        assertTrue("delayed=${delayed.x(432L)}", delayed.x(432L) > 15f)
    }

    @Test
    fun configure_returnScaleSpeedsUpSettle() {
        val slow = GestureHandleMotion().apply { configure(0L, 0.5f, 1f) }
        val fast = GestureHandleMotion().apply { configure(0L, 0.5f, 4f) }
        for (motion in listOf(slow, fast)) {
            motion.start(0f, 0f, 0L)
            motion.move(200f, 0f, 24f, 24f, 24f, 24f, 16L)
            motion.release(32L)
        }

        // 同样回弹 100ms，速度倍率更高的一侧位移更接近归零
        assertTrue("slow=${slow.x(132L)} fast=${fast.x(132L)}", fast.x(132L) < slow.x(132L))
    }
}
