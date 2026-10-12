package cn.ianzb.hypernavbar.hook.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 校验小白条透明度动画时长与反向过渡。 */
class GestureHandleFadeTest {

    @Test
    fun fadeIn_reachesFullAlphaAfterDuration() {
        val fade = GestureHandleFade(hidden = true)

        assertEquals(0f, fade.alpha(false, 0L), 0.001f)
        assertTrue(fade.running(100L))
        assertEquals(1f, fade.alpha(false, GestureHandleFade.DURATION_MS), 0.01f)
        assertFalse(fade.running(GestureHandleFade.DURATION_MS))
    }

    @Test
    fun quickReveal_usesShorterDuration() {
        val fade = GestureHandleFade(hidden = true)

        fade.alpha(false, 0L, quickReveal = true)

        assertEquals(1f, fade.alpha(false, GestureHandleFade.QUICK_REVEAL_DURATION_MS, quickReveal = true), 0.01f)
        assertFalse(fade.running(GestureHandleFade.QUICK_REVEAL_DURATION_MS))
    }

    @Test
    fun reversalStartsFromCurrentValue() {
        val fade = GestureHandleFade(hidden = false)

        assertEquals(1f, fade.alpha(true, 0L), 0.001f)
        val reversed = fade.alpha(false, GestureHandleFade.DURATION_MS / 2)

        assertTrue("reversed=$reversed", reversed in 0.3f..1f)
        assertEquals(1f, fade.alpha(false, GestureHandleFade.DURATION_MS / 2 + GestureHandleFade.DURATION_MS), 0.01f)
    }
}
