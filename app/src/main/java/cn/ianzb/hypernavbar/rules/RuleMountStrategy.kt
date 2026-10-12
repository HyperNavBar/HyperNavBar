package cn.ianzb.hypernavbar.rules

import cn.ianzb.hypernavbar.hook.rule.VersionContext
import cn.ianzb.hypernavbar.hook.rule.hookVersionGate

/**
 * 规则挂载方式选择。
 *
 * - HyperOS 3.3（3.0.3XX < 4.0）：改回「替换系统规则文件」方式（Root 写入，见 [RootApplier]），
 *   规则注入 Hook 在 3.3 上不安装；
 * - HyperOS 4.x：维持运行期 Hook 注入；
 * - 更早的系统：两种挂载均不生效（仅保留规则编辑能力）。
 */
object RuleMountStrategy {

    private val atLeast33 = hookVersionGate { hyperOs { ge("3.0.3") } }
    private val atLeast4 = hookVersionGate { hyperOs { ge("4.0") } }

    /** 当前设备是否应使用「替换系统规则文件」方式挂载规则。 */
    fun usesRuleFile(context: VersionContext = VersionContext.of("", "", 0L)): Boolean =
        atLeast33.matches(context) && !atLeast4.matches(context)
}
