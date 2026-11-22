package cn.ianzb.hypernavbar.hook.gesture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureTouchSourceTest {
    @Test
    fun legacyCancelCannotEndMiuiDrag() {
        val source = GestureTouchSource()
        val receiver = Any()
        val motion = GestureHandleMotion()

        assertTrue(source.acceptMiui(receiver, isDown = true))
        motion.start(0f, 0f, 100L)
        // Launcher pilfers the legacy stream 13 ms after DOWN.
        if (source.acceptLegacy) motion.release(113L)
        assertTrue(source.acceptMiui(receiver, isDown = false))
        motion.move(80f, -50f, 24f, 24f, 16f, 4f, 155L)

        assertTrue(motion.x(180L) > 0f)
        assertTrue(motion.y(180L) < 0f)
    }

    @Test
    fun fallbackRemainsAvailableUntilMiuiDownAndAfterDisposal() {
        val source = GestureTouchSource()
        val receiver = Any()
        assertTrue(source.acceptLegacy)
        assertFalse(source.acceptMiui(receiver, isDown = false))
        assertTrue(source.acceptLegacy)
        assertTrue(source.acceptMiui(receiver, isDown = true))
        assertFalse(source.acceptLegacy)
        assertFalse(source.clearMiui(Any()))
        assertFalse(source.acceptLegacy)
        assertTrue(source.clearMiui(receiver))
        assertTrue(source.acceptLegacy)
    }
}
