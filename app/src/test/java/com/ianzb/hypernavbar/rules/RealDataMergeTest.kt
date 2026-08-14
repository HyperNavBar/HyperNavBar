package com.ianzb.hypernavbar.rules

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 用真实的官方/社区规则数据验证播种后 (社区 priority 0 在上, 官方 priority 1 在下) 的合并结果。
 * 关注: 1) 同名 activity 规则谁生效; 2) 合并结果 root 的 name/dataVersion 取自哪个源。
 */
class RealDataMergeTest {

    private fun loadResource(name: String): String =
        javaClass.classLoader!!.getResource(name)!!.readText()

    private fun communityConfig() = RuleConfigSource(
        id = "community", type = RuleType.LOCAL,
        jsonContent = loadResource("community.json"), priority = 0,
    )

    private fun officialConfig() = RuleConfigSource(
        id = "official", type = RuleType.LOCAL,
        jsonContent = loadResource("official.json"), priority = 1,
    )

    private fun resultsFor(vararg configs: RuleConfigSource) = configs.associate {
        it.id to RuleFetcher.parseJson(it.jsonContent).getOrThrow()
    }

    @Test
    fun seededMerge_communityOnTopOverridesOfficial() {
        val community = communityConfig()
        val official = officialConfig()
        val results = resultsFor(community, official)
        val merged = RuleCombiner.combine(listOf(community, official), results)

        val mergedRules = merged.getJSONObject("NBIRules")
        val communityRules = JSONObject(community.jsonContent).getJSONObject("NBIRules")
        val officialRules = JSONObject(official.jsonContent).getJSONObject("NBIRules")

        // 统计: 社区与官方同名 activity 但规则不同的数量, 以及合并后谁生效
        var conflictCount = 0
        var communityWins = 0
        var officialWins = 0
        var notInMerged = 0

        for (pkg in communityRules.keys()) {
            val commApp = communityRules.optJSONObject(pkg) ?: continue
            val offApp = officialRules.optJSONObject(pkg) ?: continue
            val commActs = commApp.optJSONObject("activityRules") ?: JSONObject()
            val offActs = offApp.optJSONObject("activityRules") ?: JSONObject()
            val mergedApp = mergedRules.optJSONObject(pkg) ?: continue
            val mergedActs = mergedApp.optJSONObject("activityRules") ?: JSONObject()

            for (act in commActs.keys()) {
                if (!offActs.has(act)) continue
                val commRule = commActs.getJSONObject(act)
                val offRule = offActs.getJSONObject(act)
                if (commRule.toString() != offRule.toString()) {
                    conflictCount++
                    val mergedRule = mergedActs.optJSONObject(act)
                    if (mergedRule == null) {
                        notInMerged++
                    } else if (mergedRule.toString() == commRule.toString()) {
                        communityWins++
                    } else if (mergedRule.toString() == offRule.toString()) {
                        officialWins++
                    }
                }
            }
        }

        println("同名但不同规则的 activity 数: $conflictCount")
        println("合并后社区(上)生效: $communityWins")
        println("合并后官方(下)生效: $officialWins")
        println("合并后丢失: $notInMerged")

        // 期望: 社区(priority 0, 上) 覆盖官方 → communityWins == conflictCount
        assertEquals("社区(上)应覆盖官方(下)", conflictCount, communityWins)
        assertEquals(0, officialWins)
    }

    @Test
    fun seededMerge_rootNameComesFromWinningTopConfig() {
        val community = communityConfig()
        val official = officialConfig()
        val results = resultsFor(community, official)
        val merged = RuleCombiner.combine(listOf(community, official), results)

        val rootName = merged.optString("name", "")
        val communityName = JSONObject(community.jsonContent).optString("name", "")
        val officialName = JSONObject(official.jsonContent).optString("name", "")

        println("合并结果 root name = '$rootName'")
        println("社区源 name = '$communityName'")
        println("官方源 name = '$officialName'")
        // 修复后: root name 应取自合并获胜方（列表最上方 = priority 最小 = 社区）
        assertEquals("root name 应为社区源 name（上方获胜方）", communityName, rootName)
    }
}
