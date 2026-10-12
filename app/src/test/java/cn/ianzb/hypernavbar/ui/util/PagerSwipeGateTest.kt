package cn.ianzb.hypernavbar.ui.util

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 校验横向手势认领开关的基本语义（滑条按下认领 / 抬手释放 / 新手势复位）。 */
class PagerSwipeGateTest {

    @Before
    fun setUp() {
        PagerSwipeGate.release()
    }

    @After
    fun tearDown() {
        PagerSwipeGate.release()
    }

    @Test
    fun claimAndRelease() {
        assertFalse(PagerSwipeGate.childOwnsGesture)

        PagerSwipeGate.claim()
        assertTrue(PagerSwipeGate.childOwnsGesture)

        PagerSwipeGate.release()
        assertFalse(PagerSwipeGate.childOwnsGesture)
    }

    @Test
    fun resetForNewGesture_clearsStaleClaim() {
        PagerSwipeGate.claim()

        PagerSwipeGate.resetForNewGesture()

        assertFalse(PagerSwipeGate.childOwnsGesture)
    }
}
