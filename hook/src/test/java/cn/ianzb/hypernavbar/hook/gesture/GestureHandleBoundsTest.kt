package cn.ianzb.hypernavbar.hook.gesture

import cn.ianzb.hypernavbar.hook.gesture.GestureHandleBounds.Edges
import org.junit.Assert.assertEquals
import org.junit.Test

class GestureHandleBoundsTest {
    private val pill = Edges(4f, 12f, 104f, 16f)
    private val handle = Edges(0f, 0f, 108f, 24f)
    private val wideViewport = Edges(-160f, 0f, 268f, 24f)

    @Test
    fun widerViewportAllowsTheConfiguredHorizontalRange() {
        assertEquals(2f, GestureHandleBounds.fit(pill, handle, 48f, 2f).right, 0f)
        assertEquals(
            GestureHandleBounds(48f, 48f, 10f, 6f),
            GestureHandleBounds.fit(pill, wideViewport, 48f, 2f),
        )
    }

    @Test
    fun fadeOrReturnFrameUsesNewBoundsIfTheViewportShrinks() {
        val limits = GestureHandleBounds.fit(pill, Edges(-12f, 10f, 170f, 18f), 48f, 2f)
        assertEquals(-14f, limits.clampX(-48f), 0f)
        assertEquals(48f, limits.clampX(80f), 0f)
        assertEquals(0f, limits.clampY(-8f), 0f)
        assertEquals(0f, limits.clampY(8f), 0f)
    }

    @Test
    fun noExtraMotionWhenTheStockPillIsAlreadyClipped() {
        assertEquals(
            GestureHandleBounds.NONE,
            GestureHandleBounds.fit(pill, Edges(-12f, 14f, 170f, 18f), 48f, 2f),
        )
    }
}
