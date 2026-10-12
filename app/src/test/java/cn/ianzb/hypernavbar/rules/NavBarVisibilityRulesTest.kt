package cn.ianzb.hypernavbar.rules

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 校验应用级小白条状态（navBarVisibility）的合并、补全与发布提取。 */
class NavBarVisibilityRulesTest {

    private fun ruleConfig(id: String, priority: Int, json: String) = RuleConfigSource(
        id = id,
        type = RuleType.LOCAL,
        jsonContent = json,
        priority = priority,
    )

    private fun resultsFor(vararg configs: RuleConfigSource) = configs.associate {
        it.id to RuleFetcher.parseJson(it.jsonContent).getOrThrow()
    }

    @Test
    fun normalizeNavBarVisibility_fallsBackToDefault() {
        assertEquals("default", RuleConverter.normalizeNavBarVisibility(null))
        assertEquals("default", RuleConverter.normalizeNavBarVisibility("bogus"))
        assertEquals("alwaysHide", RuleConverter.normalizeNavBarVisibility("alwaysHide"))
    }

    @Test
    fun buildVisibilityRules_keepsOnlyExplicitNonDefaultValues() {
        val merged = JSONObject(
            """
            {"NBIRules":{
              "com.a":{"navBarVisibility":"alwaysHide","activityRules":{}},
              "com.b":{"navBarVisibility":"default","activityRules":{}},
              "com.c":{"activityRules":{}},
              "com.d":{"navBarVisibility":"bogus","activityRules":{}}
            }}
            """.trimIndent()
        )

        val rules = RuleConverter.buildVisibilityRules(merged)

        assertEquals(mapOf("com.a" to "alwaysHide"), rules)
    }

    @Test
    fun combine_fillsDefaultVisibilityForAppsMissingIt() {
        val base = ruleConfig(
            "base", 0,
            """{"NBIRules":{"com.x":{"enable":true,"activityRules":{"A":{"style":"view"}}}}}""",
        )

        val merged = RuleCombiner.combine(listOf(base), resultsFor(base))
        val app = merged.getJSONObject("NBIRules").getJSONObject("com.x")

        assertTrue(app.has("navBarVisibility"))
        assertEquals("default", app.getString("navBarVisibility"))
    }

    @Test
    fun combine_appLevelVisibilityFromWinningSource() {
        val base = ruleConfig(
            "base", 10,
            """{"NBIRules":{"com.x":{"enable":true,"navBarVisibility":"alwaysShow","activityRules":{"A":{"style":"view"}}}}}""",
        )
        val top = ruleConfig(
            "top", 0,
            """{"NBIRules":{"com.x":{"enable":true,"navBarVisibility":"autoHide","activityRules":{"A":{"style":"view"}}}}}""",
        )

        val merged = RuleCombiner.combine(listOf(base, top), resultsFor(base, top))

        assertEquals(
            "autoHide",
            merged.getJSONObject("NBIRules").getJSONObject("com.x").getString("navBarVisibility"),
        )
        assertEquals(mapOf("com.x" to "autoHide"), RuleConverter.buildVisibilityRules(merged))
    }
}
