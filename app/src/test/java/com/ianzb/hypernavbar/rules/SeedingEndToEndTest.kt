package com.ianzb.hypernavbar.rules

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 端到端模拟: ensureDefaultConfigs 播种 (连续两次 add) + reloadConfigs 显示排序 + combine 合并。
 * 验证: 社区(priority 0) 显示在上, 且合并时覆盖官方(priority 1)。
 */
class SeedingEndToEndTest {

    // 内存存储, 模拟 SharedPreferences
    private var storedJson: String? = null

    // ===== 模拟 RulesManager.add =====
    private fun add(id: String): Pair<String, Int> {
        val configs = loadAll().toMutableList()
        val priority = (configs.maxOfOrNull { it.second } ?: -1) + 1
        configs.add(Pair(id, priority))
        saveAll(configs)
        return Pair(id, priority)
    }

    // ===== 模拟 RulesManager.loadAll (JSON 序列化往返) =====
    private fun loadAll(): List<Pair<String, Int>> {
        val json = storedJson ?: return emptyList()
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            Pair(obj.getString("id"), obj.getInt("priority"))
        }
    }

    private fun saveAll(configs: List<Pair<String, Int>>) {
        val arr = JSONArray()
        configs.forEach { (id, p) ->
            arr.put(JSONObject().put("id", id).put("priority", p))
        }
        storedJson = arr.toString()
    }

    // ===== 模拟 RulesPage.reloadConfigs: sortedBy { priority } 升序 =====
    private fun displayOrder(): List<String> = loadAll().sortedBy { it.second }.map { it.first }

    // ===== 模拟 RuleCombiner.combine: sortedByDescending { priority } =====
    private fun mergeOrder(): List<String> = loadAll().sortedByDescending { it.second }.map { it.first }

    @Test
    fun seeding_producesCommunityOnTopAndCommunityWins() {
        // ensureDefaultConfigs: 先 add 社区, 再 add 官方
        val community = add("community")  // priority 0
        val official = add("official")    // priority 1

        assertEquals(0, community.second)
        assertEquals(1, official.second)

        // 显示顺序 (sortedBy 升序): 社区在上, 官方在下
        val display = displayOrder()
        assertEquals(listOf("community", "official"), display)

        // 合并顺序 (sortedByDescending): 官方先处理(基底), 社区后处理(覆盖)
        val merge = mergeOrder()
        assertEquals(listOf("official", "community"), merge)

        // 结论: 合并时 community (priority 0, 显示在上) 最后处理并覆盖 official
        // → 上面(社区)覆盖下面(官方) ✓
        println("播种后显示顺序: $display")
        println("播种后合并顺序: $merge")
        println("社区在上且在合并中获胜: true")
    }
}
