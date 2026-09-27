package cn.ianzb.hypernavbar.hook.rule

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 校验 NBI Hook 共用的 `hyperOs >= 4.0` 门禁：
 * `ro.mi.os.version.name` 形如 `OS4.0`，需归一化去掉 `OS` 前缀后才能正确比较。
 */
class HookVersionGateTest {

    private val gate = hookVersionGate { hyperOs { ge("4.0") } }

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
    fun hyperOs4_matches() {
        assertTrue(gate.matches(ctx("OS4.0")))
        assertTrue(gate.matches(ctx("OS4.0.0.33.XPMCNXM")))
        assertTrue(gate.matches(ctx("4.1")))
    }

    @Test
    fun nonHyperOs4_skips() {
        assertFalse(gate.matches(ctx("OS3.3")))
        assertFalse(gate.matches(ctx("OS3.0")))
        assertFalse(gate.matches(ctx("OS2.2")))
        assertFalse(gate.matches(ctx("")))
    }
}
