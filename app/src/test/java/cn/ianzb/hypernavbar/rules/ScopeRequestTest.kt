package cn.ianzb.hypernavbar.rules

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** 校验应用规则时的作用域申请集合：受「强制生效」总开关与手动排除（黑名单）约束。 */
class ScopeRequestTest {

    private val merged = JSONObject(
        """
        {"NBIRules":{
          "com.inject":{"hookExcluded":false,"activityRules":{}},
          "com.excluded":{"hookExcluded":true,"activityRules":{}},
          "com.missing":{"activityRules":{}}
        }}
        """.trimIndent()
    )

    @Test
    fun masterOff_requestsNothing() {
        assertEquals(
            emptyList<String>(),
            RuleConverter.scopeRequestPackages(merged, masterEnabled = false),
        )
    }

    @Test
    fun masterOn_requestsOnlyExplicitHookExcludedFalse() {
        assertEquals(
            listOf("com.inject"),
            RuleConverter.scopeRequestPackages(merged, masterEnabled = true),
        )
    }

    @Test
    fun blacklistedPackagesAreNeverRequested() {
        assertEquals(
            emptyList<String>(),
            RuleConverter.scopeRequestPackages(
                merged,
                masterEnabled = true,
                blacklist = setOf("com.inject"),
            ),
        )
    }
}
