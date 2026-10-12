package cn.ianzb.hypernavbar.rules

import org.junit.Assert.assertEquals
import org.junit.Test

/** 校验「新建规则默认放到最上面」的插入与优先级重排逻辑。 */
class RulesManagerOrderTest {

    private fun config(id: String, priority: Int) =
        RuleConfigSource(id = id, name = id, priority = priority)

    @Test
    fun insertAtTop_newRuleBecomesFirstWithPriorityZero() {
        val existing = listOf(config("a", 0), config("b", 1))

        val inserted = RulesManager.insertAtTop(existing, config("new", 0))

        assertEquals(listOf("new", "a", "b"), inserted.map { it.id })
        assertEquals(listOf(0, 1, 2), inserted.map { it.priority })
    }

    @Test
    fun insertAtTop_keepsExistingOrderAndNormalizesStalePriorities() {
        val existing = listOf(config("a", 7), config("b", 3))

        val inserted = RulesManager.insertAtTop(existing, config("new", 0))

        assertEquals(listOf("new", "a", "b"), inserted.map { it.id })
        assertEquals(listOf(0, 1, 2), inserted.map { it.priority })
    }

    @Test
    fun insertAtTop_intoEmptyListKeepsSingleItem() {
        val inserted = RulesManager.insertAtTop(emptyList(), config("new", 0))

        assertEquals(listOf("new"), inserted.map { it.id })
        assertEquals(listOf(0), inserted.map { it.priority })
    }
}
