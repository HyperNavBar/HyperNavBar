package cn.ianzb.hypernavbar.hook.gesture

import cn.ianzb.hypernavbar.hook.gesture.GestureHandleBounds.Edges
import org.junit.Assert.assertEquals
import org.junit.Test

class GestureHandleBoundsTest {
    private val pill = Edges(4f, 12f, 104f, 16f)
    private val handle = Edges(0f, 0f, 108f, 24f)
    private val upperPanel = Edges(-160f, -64f, 268f, 24f)

    @Test
    fun tallerPanelExtendsOnlyTheUpwardRange() {
        val (local, extended) = GestureHandleBounds.fitUpper(pill, handle, upperPanel, 48f, 2f)
        assertEquals(GestureHandleBounds(2f, 2f, 10f, 6f), local)
        assertEquals(local.copy(up = 48f), extended)
    }

    @Test
    fun clippedPanelFallsBackToTheStockWindow() {
        val (local, extended) = GestureHandleBounds.fitUpper(
            pill, handle, Edges(5f, -64f, 108f, 15f), 48f, 2f
        )
        assertEquals(local, extended)
    }

    @Test
    fun fadeOrReturnFrameUsesNewBoundsIfTheViewportShrinks() {
        val limits = GestureHandleBounds.fitUpper(pill, handle, upperPanel.copy(top = -10f), 48f, 2f).second
        assertEquals(-20f, limits.clampY(-48f), 0f)
        assertEquals(6f, limits.clampY(48f), 0f)
    }

    @Test
    fun noExtraMotionWhenTheStockPillIsAlreadyClipped() {
        assertEquals(
            GestureHandleBounds.NONE,
            GestureHandleBounds.fit(pill, Edges(-12f, 14f, 170f, 18f), 48f, 2f),
        )
    }
}
