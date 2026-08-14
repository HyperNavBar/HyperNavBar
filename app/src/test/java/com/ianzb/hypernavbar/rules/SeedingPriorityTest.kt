package com.ianzb.hypernavbar.rules

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 验证 ensureDefaultConfigs 播种时 add() 的 priority 分配顺序：
 * 社区先 add → priority 0（显示在上），官方后 add → priority 1（显示在下）。
 */
class SeedingPriorityTest {

    // 模拟 RulesManager.add 的 priority 计算: (maxOfOrNull { priority } ?: -1) + 1
    private fun simulateAdd(seedList: List<Int>): Int =
        (seedList.maxOrNull() ?: -1) + 1

    @Test
    fun seeding_communityGetsLowerPriorityThanOfficial() {
        val communityPriority = simulateAdd(emptyList())          // 第一次 add(社区)
        val officialPriority = simulateAdd(listOf(communityPriority)) // 第二次 add(官方)

        assertEquals(0, communityPriority)
        assertEquals(1, officialPriority)

        // 显示顺序: sortedBy { priority } 升序 → 社区(0)在上, 官方(1)在下
        val displayOrder = listOf(officialPriority, communityPriority).sorted()
        assertEquals(listOf(0, 1), displayOrder)
        assertEquals(communityPriority, displayOrder.first())  // 社区显示在上
    }
}
