package cn.ianzb.hypernavbar.rules

import cn.ianzb.hypernavbar.hook.rule.VersionContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 校验按系统版本选择规则挂载方式：3.3 走替换规则文件，4.x 走 Hook 注入。 */
class RuleMountStrategyTest {

    private fun ctx(hyperOs: String) = VersionContext(
        packageName = "",
        androidSdk = 35,
        androidRelease = "15",
        hyperOs = hyperOs,
        miui = "",
        appVersionName = "",
        appVersionCode = 0L,
    )

    @Test
    fun hyperOs33_usesRuleFile() {
        assertTrue(RuleMountStrategy.usesRuleFile(ctx("OS3.3")))
        assertTrue(RuleMountStrategy.usesRuleFile(ctx("OS3.0.3XX")))
    }

    @Test
    fun hyperOs4_keepsHookInjection() {
        assertFalse(RuleMountStrategy.usesRuleFile(ctx("OS4.0")))
        assertFalse(RuleMountStrategy.usesRuleFile(ctx("OS4.0.0.33.XPMCNXM")))
    }

    @Test
    fun earlierSystems_useNeither() {
        assertFalse(RuleMountStrategy.usesRuleFile(ctx("OS3.0")))
        assertFalse(RuleMountStrategy.usesRuleFile(ctx("OS2.2")))
        assertFalse(RuleMountStrategy.usesRuleFile(ctx("")))
    }
}
