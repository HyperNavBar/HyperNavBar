package cn.ianzb.hypernavbar.rules

import android.content.Context
import cn.ianzb.hypernavbar.hook.rule.SystemVersion
import cn.ianzb.hypernavbar.hook.rule.VersionContext

/**
 * 规则挂载方式选择。
 *
 * 版本判定统一走 [SystemVersion]（主版本 + 第三段百位）：
 * - [SystemVersion.OS40]：运行期 Hook 注入（system_server 侧覆盖官方 Bundle，无需 Root）；
 * - 其余版本（2.2 / 3.0 / 3.3）：沿用「替换系统规则文件」方式（Root 写入，见 [RootApplier]），
 *   按版本写入对应格式与路径：2.2 → XML，3.0 / 3.3 → JSON；4.x 的规则注入 Hook 在这些版本上不安装。
 */
object RuleMountStrategy {

    /** 当前设备是否应使用「替换系统规则文件」方式挂载规则。 */
    fun usesRuleFile(context: VersionContext = VersionContext.of("", "", 0L)): Boolean =
        context.systemVersion.usesRuleFile

    /**
     * 文件挂载使用的规则格式（同时决定目标文件与基底文件路径）。
     *
     * 取 [SystemVersionDetector.getEffectiveOsMode]（自动检测 / 用户在首页手动指定）。
     */
    fun fileVersion(context: Context): SystemVersion = SystemVersionDetector.getEffectiveOsMode(context)
}
