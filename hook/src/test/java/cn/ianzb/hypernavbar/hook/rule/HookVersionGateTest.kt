package cn.ianzb.hypernavbar.hook.rule

import cn.ianzb.hypernavbar.hook.nbi.NbiHookSupport
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 校验 NBI Hook 的版本门禁（`ro.mi.os.version.name` 形如 `OS4.0`，需归一化去掉 `OS` 前缀再比较）：
 * - [NbiHookSupport.NBI_GATE]（`hyperOs >= 3.0.3`）：「强制生效」系列在 3.3 / 4.x 上生效；
 * - [NbiHookSupport.OS4_GATE]（`hyperOs >= 4.0`）：规则注入与旧版残留清理仅 4.x 生效
 *   （3.3 改回替换系统规则文件的方式挂载规则）。
 */
class HookVersionGateTest {

    private fun ctx(hyperOs: String) = VersionContext(
        packageName = "com.example",
        androidSdk = 35,
        androidRelease = "15",
        hyperOs = hyperOs,
        miui = "",
        appVersionName = "1.0",
        appVersionCode = 1L,
    )

    @Test
    fun nbiGate_matches33AndAbove() {
        assertTrue(NbiHookSupport.NBI_GATE.matches(ctx("OS3.3")))
        assertTrue(NbiHookSupport.NBI_GATE.matches(ctx("OS3.0.3XX")))
        assertTrue(NbiHookSupport.NBI_GATE.matches(ctx("OS4.0")))
        assertTrue(NbiHookSupport.NBI_GATE.matches(ctx("4.1")))
    }

    @Test
    fun nbiGate_skipsEarlierSystems() {
        assertFalse(NbiHookSupport.NBI_GATE.matches(ctx("OS3.0")))
        assertFalse(NbiHookSupport.NBI_GATE.matches(ctx("OS2.2")))
        assertFalse(NbiHookSupport.NBI_GATE.matches(ctx("")))
    }

    @Test
    fun os4Gate_matchesOnlyHyperOs4() {
        assertTrue(NbiHookSupport.OS4_GATE.matches(ctx("OS4.0")))
        assertTrue(NbiHookSupport.OS4_GATE.matches(ctx("OS4.0.0.33.XPMCNXM")))
        assertFalse(NbiHookSupport.OS4_GATE.matches(ctx("OS3.3")))
        assertFalse(NbiHookSupport.OS4_GATE.matches(ctx("OS3.0.3XX")))
        assertFalse(NbiHookSupport.OS4_GATE.matches(ctx("")))
    }
}
