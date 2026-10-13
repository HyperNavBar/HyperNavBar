package cn.ianzb.hypernavbar.rules

import android.content.Context
import cn.ianzb.hypernavbar.hook.rule.VersionContext
import cn.ianzb.hypernavbar.hook.rule.hookVersionGate

/**
 * 规则挂载方式选择。
 *
 * - HyperOS 4.x：运行期 Hook 注入（system_server 侧覆盖官方 Bundle，无需 Root）；
 * - 更早的系统（2.2 / 3.0 / 3.3）：沿用「替换系统规则文件」方式（Root 写入，见 [RootApplier]），
 *   按版本写入对应格式与路径：2.2 → XML，3.0 / 3.3 → JSON；4.x 的规则注入 Hook 在这些版本上不安装。
 */
object RuleMountStrategy {

    private val atLeast4 = hookVersionGate { hyperOs { ge("4.0") } }

    /** 当前设备是否应使用「替换系统规则文件」方式挂载规则。 */
    fun usesRuleFile(context: VersionContext = VersionContext.of("", "", 0L)): Boolean =
        !atLeast4.matches(context)

    /**
     * 文件挂载使用的规则格式（同时决定目标文件与基底文件路径）。
     *
     * 优先用 [SystemVersionDetector.getEffectiveOsMode]（自动检测 / 用户在首页手动指定），
     * 检测不到时回退 [RuleConverter.detectOsMode]。
     */
    fun fileOsMode(context: Context): RuleConverter.OsMode =
        SystemVersionDetector.getEffectiveOsMode(context)
            ?.let { osMode -> runCatching { RuleConverter.OsMode.valueOf(osMode.name) }.getOrNull() }
            ?: RuleConverter.detectOsMode()
}
