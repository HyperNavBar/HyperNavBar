package cn.ianzb.hypernavbar.ui.screen.rules

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 校验手动编辑器保存路径（formatNbiJson）不会丢失应用级新增参数。 */
class JsonRuleEditorFormatTest {

    private fun app(json: String): JSONObject =
        JSONObject(formatNbiJson(json)).getJSONObject("NBIRules").getJSONObject("com.x")

    @Test
    fun formatNbiJson_preservesExplicitHookExcludedFalse() {
        val out = app(
            """{"NBIRules":{"com.x":{"name":"X","enable":true,"hookExcluded":false,"activityRules":{}}}}"""
        )
        assertTrue(out.has("hookExcluded"))
        assertFalse(out.getBoolean("hookExcluded"))
    }

    @Test
    fun formatNbiJson_writesDefaultHookExcludedWhenMissing() {
        val out = app(
            """{"NBIRules":{"com.x":{"name":"X","enable":true,"activityRules":{"A":{"style":"view"}}}}}"""
        )
        assertTrue(out.has("hookExcluded"))
        assertTrue(out.getBoolean("hookExcluded"))
    }

    @Test
    fun formatNbiJson_preservesHookExcludedTrueAndDisableVersionCode() {
        val out = app(
            """
            {"NBIRules":{"com.x":{
              "name":"X","enable":true,"hookExcluded":true,"disableVersionCode":123,
              "activityRules":{"A":{"style":"view"}}
            }}}
            """.trimIndent()
        )
        assertTrue(out.getBoolean("hookExcluded"))
        assertTrue(out.has("disableVersionCode"))
    }
}
