package cn.ianzb.hypernavbar.hook

import cn.ianzb.hypernavbar.hook.rule.HookVersionGate
import cn.ianzb.hypernavbar.hook.rule.SystemVersion
import cn.ianzb.hypernavbar.hook.rule.VersionContext
import cn.ianzb.hypernavbar.hook.rule.hookVersionGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 统一系统版本判定：只按「主版本 + 第三段百位」，第二段恒为 0、不参与判定。
 *
 * - `2.0.200` → 2.2；`3.0.301` → 3.3；`3.0.105` → 3.0；`4.0.x` → 4.0。
 */
class SystemVersionTest {

    private fun version(incremental: String, name: String = "") =
        SystemVersion.parse(name = name, incremental = incremental)

    private fun ctx(name: String, incremental: String = "") = VersionContext(
        packageName = "",
        androidSdk = 36,
        androidRelease = "16",
        hyperOs = name,
        hyperOsIncremental = incremental,
        miui = "",
        appVersionName = "",
        appVersionCode = 0L,
    )

    @Test
    fun thirdSegmentHundredsDigitDecidesSeries() {
        assertEquals(SystemVersion.OS22, version("2.0.200.0.X"))
        assertEquals(SystemVersion.OS33, version("3.0.301.0.WMLCNXM", name = "OS3.0"))
        assertEquals(SystemVersion.OS30, version("3.0.105.0.WMLCNXM", name = "OS3.0"))
        assertEquals(SystemVersion.OS40, version("4.0.0.33.XPMCNXM", name = "OS4.0"))
        // 第二段即使非 0 也不参与判定，只看第三段
        assertEquals(SystemVersion.OS33, version("3.9.305.0.X"))
    }

    @Test
    fun incrementalTakesPriorityOverName() {
        // 名称写 3.0（3.3 机型常见），实际系列由增量号决定
        assertEquals(SystemVersion.OS33, version("3.0.305.0.WMLCNXM", name = "OS3.0"))
        // 读不到增量号时才用名称标注（第二段兜底）
        assertEquals(SystemVersion.OS33, version("", name = "OS3.3"))
        assertEquals(SystemVersion.OS22, version("", name = "OS2.2"))
        assertEquals(SystemVersion.OS40, version("", name = "OS4.0"))
    }

    @Test
    fun unknownFormsStayUnknown() {
        assertEquals(SystemVersion.UNKNOWN, version(""))
        assertEquals(SystemVersion.UNKNOWN, version("", name = "OS3.0"))
        // 第三段 < 300 是 3.0，不是未知
        assertEquals(SystemVersion.OS30, version("3.0.205.0.X", name = "OS3.0"))
        // 2.x 里只有 2xx 系列是 2.2
        assertEquals(SystemVersion.UNKNOWN, version("2.0.105.0.X"))
    }

    @Test
    fun immersiveSupportAndMountStrategy() {
        assertTrue(SystemVersion.OS33.immersiveSupported)
        assertTrue(SystemVersion.OS40.immersiveSupported)
        assertFalse(SystemVersion.OS30.immersiveSupported)
        assertFalse(SystemVersion.OS22.immersiveSupported)

        // 4.0 走运行期注入，其余版本走替换规则文件
        assertFalse(SystemVersion.OS40.usesRuleFile)
        assertTrue(SystemVersion.OS33.usesRuleFile)
        assertTrue(SystemVersion.OS30.usesRuleFile)
        assertTrue(SystemVersion.OS22.usesRuleFile)
        assertTrue(SystemVersion.UNKNOWN.usesRuleFile)
    }

    @Test
    fun manualOverrideSelectsVersion() {
        assertEquals(SystemVersion.OS40, SystemVersion.resolve("4.0"))
        assertEquals(SystemVersion.OS33, SystemVersion.resolve("3.3"))
        assertEquals(SystemVersion.OS30, SystemVersion.resolve("3.0"))
        assertEquals(SystemVersion.OS22, SystemVersion.resolve("2.2"))
    }

    @Test
    fun versionContextExposesUnifiedVersion() {
        assertEquals(SystemVersion.OS33, ctx("OS3.0", "OS3.0.305.0.WMLCNXM").systemVersion)
        assertEquals(SystemVersion.OS40, ctx("OS4.0", "OS4.0.0.33.XPMCNXM").systemVersion)
    }

    /** 与 `NbiHookSupport.NBI_GATE` / `OS4_GATE` 相同口径的门禁。 */
    private val immersiveGate = HookVersionGate.of { it.immersiveSupported }
    private val os4Gate = HookVersionGate.of { it.isOs4 }

    @Test
    fun gatesFollowUnifiedVersion() {
        assertTrue(immersiveGate.matches(ctx("OS3.0", "OS3.0.305.0.WMLCNXM")))
        assertTrue(os4Gate.matches(ctx("OS4.0", "OS4.0.0.33.XPMCNXM")))
        assertFalse(os4Gate.matches(ctx("OS3.0", "OS3.0.305.0.WMLCNXM")))
        assertFalse(immersiveGate.matches(ctx("OS3.0", "OS3.0.105.0.WMLCNXM")))
        assertFalse(immersiveGate.matches(ctx("OS2.2", "2.0.200.0.X")))
    }

    @Test
    fun legacyHyperOsRulesStillWorkThroughUnifiedVersion() {
        val legacyGate = hookVersionGate { hyperOs { ge("3.0.3") } }

        assertTrue(legacyGate.matches(ctx("OS3.0", "OS3.0.305.0.WMLCNXM")))
        assertTrue(legacyGate.matches(ctx("OS4.0", "OS4.0.0.33.XPMCNXM")))
        assertFalse(legacyGate.matches(ctx("OS3.0", "OS3.0.105.0.WMLCNXM")))
    }
}
