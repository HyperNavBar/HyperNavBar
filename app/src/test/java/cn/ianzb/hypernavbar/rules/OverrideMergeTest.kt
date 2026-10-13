package cn.ianzb.hypernavbar.rules

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 校验同活动键覆盖、载荷字段完整性，以及 style 形态条目不被官方格式归一化抹平。 */
class OverrideMergeTest {

    private fun config(id: String, priority: Int, json: String) = RuleConfigSource(
        id = id,
        type = RuleType.LOCAL,
        jsonContent = json,
        priority = priority,
    )

    /** 新格式（style 形态）规则源：根级带 modules 标记，与 App / 规则仓库写出的格式一致。 */
    private fun newFormatConfig(id: String, priority: Int, activityRules: String) =
        config(
            id,
            priority,
            """{"modules":"HyperNavBar_config","NBIRules":{"com.app":{"activityRules":$activityRules}}}""",
        )

    private fun resultsFor(vararg configs: RuleConfigSource) = configs.associate {
        it.id to RuleFetcher.parseJson(it.jsonContent).getOrThrow()
    }

    @Test
    fun sameActivity_higherPriorityFloatingWinsOverSf() {
        val low = newFormatConfig("low", 10, """{"A":{"style":"sf"}}""")
        val high = newFormatConfig("high", 0, """{"A":{"style":"floating"}}""")

        val merged = RuleCombiner.combine(listOf(low, high), resultsFor(low, high))
        val act = merged.getJSONObject("NBIRules")
            .getJSONObject("com.app")
            .getJSONObject("activityRules")
            .getJSONObject("A")

        assertEquals("floating", act.getString("style"))
    }

    @Test
    fun specificOverride_beatsLowerPriorityWildcard() {
        val low = newFormatConfig("low", 10, """{"*":{"style":"sf"}}""")
        val high = newFormatConfig("high", 0, """{"A":{"style":"color","color":"#11223344"}}""")

        val merged = RuleCombiner.combine(listOf(low, high), resultsFor(low, high))
        val activities = merged.getJSONObject("NBIRules")
            .getJSONObject("com.app")
            .getJSONObject("activityRules")

        assertEquals("color", activities.getJSONObject("A").getString("style"))
        assertTrue("其它活动仍应走低优先级的 * 规则", activities.has("*"))
    }

    @Test
    fun styleEntryWithoutNewFormatMarker_survivesNormalization() {
        // 手工编写的 style 规则若漏写根级 modules 标记，也不能被官方格式归一化抹成 default
        val low = config("low", 10, """{"NBIRules":{"com.app":{"activityRules":{"A":{"style":"sf"}}}}}""")
        val high = config("high", 0, """{"NBIRules":{"com.app":{"activityRules":{"A":{"style":"floating"}}}}}""")

        val merged = RuleCombiner.combine(listOf(low, high), resultsFor(low, high))
        val activities = merged.getJSONObject("NBIRules")
            .getJSONObject("com.app")
            .getJSONObject("activityRules")

        assertEquals("floating", activities.getJSONObject("A").getString("style"))
    }

    @Test
    fun rootApplier_sameActivityReplaceWritesExplicitNoSfFlag() {
        // 同活动键覆盖：整条规则替换，必须显式写回 sf_sampling_mode=0，
        // 否则系统侧独立的 SF 取值会残留官方「强制 SF 采样」，悬浮 / 自定义颜色看起来无效
        val base = JSONObject(
            """{"NBIRules":{"com.app":{"activityRules":{"com.app.Main":{"mode":-1,"color":null,"sf_sampling_mode":1}}}}}""",
        )
        val payloads = mapOf(
            "com.app" to """{"com.app.Main":{"mode":2,"color":null,"sf_sampling_mode":0,"dialogMode":1,"popupMode":1,"appNavColorDisabled":0}}""",
        )

        val content = JSONObject(RootApplier.buildFileContent(base.toString(), payloads))
        val rule = content.getJSONObject("NBIRules")
            .getJSONObject("com.app")
            .getJSONObject("activityRules")
            .getJSONObject("com.app.Main")

        assertEquals(2, rule.getInt("mode"))
        assertEquals(0, rule.getInt("sf_sampling_mode"))
        assertFalse("模块载荷里的 null 颜色不应写进系统规则文件", rule.has("color"))
    }

    @Test
    fun hookPayload_floatingStyleCarriesExplicitNoSfFlag() {
        val merged = JSONObject(
            """{"NBIRules":{"com.app":{"activityRules":{"com.app.Main":{"style":"floating"}}}}}""",
        )

        val payload = JSONObject(RuleConverter.buildHookPayloads(merged).getValue("com.app"))
        val rule = payload.getJSONObject("com.app.Main")

        assertEquals(2, rule.getInt("mode"))
        assertEquals("悬浮（强制沉浸）必须显式带上 sf_sampling_mode=0", 0, rule.getInt("sf_sampling_mode"))
    }
}
