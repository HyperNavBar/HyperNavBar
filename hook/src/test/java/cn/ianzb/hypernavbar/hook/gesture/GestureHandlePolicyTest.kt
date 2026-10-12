package cn.ianzb.hypernavbar.hook.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 校验前台身份、3 秒自动隐藏与触摸 / 滑动唤醒时限。 */
class GestureHandlePolicyTest {

    private val rules = mapOf(
        "com.auto" to "autoHide",
        "com.hide" to "alwaysHide",
        "com.show" to "alwaysShow",
    )

    private fun policy(
        follow: String = GestureVisibility.FOLLOW_RULES,
        globalMode: String = "alwaysShow",
    ) = GestureHandlePolicy().apply { configure(rules, follow, globalMode, 0L) }

    @Test
    fun autoHide_hidesThreeSecondsAfterForeground() {
        val policy = policy()
        policy.foreground("com.auto", "MainActivity", 1, 0L)

        assertFalse(policy.hidden(0L, systemHidden = true))
        assertFalse(policy.hidden(2_999L, systemHidden = true))
        assertTrue(policy.hidden(3_000L, systemHidden = true))
        assertEquals(3_000L, policy.remaining(0L))
    }

    @Test
    fun alwaysHideAndShowModesAreImmediate() {
        val policy = policy()

        policy.foreground("com.hide", "A", 1, 0L)
        assertTrue(policy.hidden(0L, systemHidden = false))

        policy.foreground("com.show", "A", 2, 0L)
        assertFalse(policy.hidden(10_000L, systemHidden = true))
    }

    @Test
    fun touchReveal_overridesModeAndExpires() {
        val policy = policy()
        policy.foreground("com.hide", "A", 1, 0L)
        assertTrue(policy.hidden(0L, systemHidden = false))

        policy.touchDown(100L)
        assertFalse(policy.hidden(100L, systemHidden = false))
        assertFalse(policy.hidden(3_099L, systemHidden = false))

        policy.touchUp(200L)
        assertFalse(policy.hidden(3_199L, systemHidden = false))
        assertTrue(policy.hidden(3_200L, systemHidden = true))
    }

    @Test
    fun swipeReveal_behavesLikeTouchReveal() {
        val policy = policy()
        policy.foreground("com.hide", "A", 1, 0L)

        policy.swipeDown(0L)
        assertFalse(policy.hidden(1_000L, systemHidden = false))

        policy.swipeUp(1_000L)
        assertFalse(policy.hidden(3_999L, systemHidden = false))
        assertTrue(policy.hidden(4_000L, systemHidden = true))
    }

    @Test
    fun followRulesOff_appliesGlobalModeToEveryApp() {
        val policy = policy(follow = GestureVisibility.FOLLOW_OFF, globalMode = "alwaysHide")
        policy.foreground("com.show", "A", 1, 0L)

        assertTrue(policy.hidden(0L, systemHidden = false))
    }

    @Test
    fun followRules_uncoveredAppFallsBackToGlobalDefault() {
        val policy = policy(globalMode = "alwaysHide")
        policy.foreground("com.unknown", "A", 1, 0L)

        assertTrue(policy.hidden(0L, systemHidden = false))
    }

    @Test
    fun foregroundChange_resetsAutoHideDeadline() {
        val policy = policy()
        policy.foreground("com.auto", "A", 1, 0L)
        assertTrue(policy.hidden(3_000L, systemHidden = false))

        policy.foreground("com.auto", "B", 1, 4_000L)
        assertFalse(policy.hidden(4_000L, systemHidden = false))
        assertTrue(policy.hidden(7_000L, systemHidden = false))
    }
}
