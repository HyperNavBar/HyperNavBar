package cn.ianzb.hypernavbar.hook.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 校验小白条模式解析：规则值 / 全局默认 / 「是否优先跟随规则」。 */
class GestureVisibilityTest {

    private val rules = mapOf(
        "com.hidden" to "alwaysHide",
        "com.auto" to "autoHide",
        "com.default" to "default",
    )

    @Test
    fun toMode_mapsRuleValues() {
        assertEquals(GestureVisibility.MODE_SHOW, GestureVisibility.toMode("alwaysShow"))
        assertEquals(GestureVisibility.MODE_AUTO, GestureVisibility.toMode("autoHide"))
        assertEquals(GestureVisibility.MODE_HIDE, GestureVisibility.toMode("alwaysHide"))
        assertNull(GestureVisibility.toMode("default"))
        assertNull(GestureVisibility.toMode("bogus"))
    }

    @Test
    fun resolve_followRules_ruleValueWins() {
        assertEquals(
            GestureVisibility.MODE_HIDE,
            GestureVisibility.resolve("com.hidden", rules, GestureVisibility.FOLLOW_RULES, "alwaysShow"),
        )
        assertEquals(
            GestureVisibility.MODE_AUTO,
            GestureVisibility.resolve("com.auto", rules, GestureVisibility.FOLLOW_RULES, "alwaysShow"),
        )
    }

    @Test
    fun resolve_followRules_fallsBackToGlobalDefault() {
        // 规则值为 default / 不在规则中的应用 → 全局默认模式
        assertEquals(
            GestureVisibility.MODE_SHOW,
            GestureVisibility.resolve("com.default", rules, GestureVisibility.FOLLOW_RULES, "alwaysShow"),
        )
        assertEquals(
            GestureVisibility.MODE_SHOW,
            GestureVisibility.resolve("com.unknown", rules, GestureVisibility.FOLLOW_RULES, "alwaysShow"),
        )
        assertEquals(
            GestureVisibility.MODE_HIDE,
            GestureVisibility.resolve("com.unknown", rules, GestureVisibility.FOLLOW_RULES, "alwaysHide"),
        )
        assertNull(GestureVisibility.resolve(null, rules, GestureVisibility.FOLLOW_RULES, "alwaysShow"))
    }

    @Test
    fun resolve_off_ignoresRulesAndUsesGlobalMode() {
        assertEquals(
            GestureVisibility.MODE_SHOW,
            GestureVisibility.resolve("com.hidden", rules, GestureVisibility.FOLLOW_OFF, "alwaysShow"),
        )
        assertEquals(
            GestureVisibility.MODE_HIDE,
            GestureVisibility.resolve("com.unknown", rules, GestureVisibility.FOLLOW_OFF, "alwaysHide"),
        )
    }

    @Test
    fun parseRules_filtersInvalidValues() {
        val parsed = GestureVisibility.parseRules(
            """{"com.a":"alwaysHide","com.b":"default","com.c":"bogus","":"alwaysShow"}"""
        )

        assertEquals(mapOf("com.a" to "alwaysHide", "com.b" to "default"), parsed)
        assertEquals(emptyMap<String, String>(), GestureVisibility.parseRules(""))
        assertEquals(emptyMap<String, String>(), GestureVisibility.parseRules("not json"))
    }
}
