package cn.ianzb.hypernavbar.hook.load

import cn.ianzb.hypernavbar.hook.base.BaseLoad
import cn.ianzb.hypernavbar.hook.base.PackageTarget
import cn.ianzb.hypernavbar.hook.nbi.NbiLegacyCleanupHook
import cn.ianzb.hypernavbar.hook.nbi.NbiRuleInjectHook

/**
 * system_server 侧 Load：规则注入 + 旧版残留清理。
 *
 * 两项均默认开启且不提供界面开关（规则注入是模块核心；残留清理是安全兜底）。
 */
class HyperNavBarSystemLoad : BaseLoad() {

    override val targetPackages: List<String> = listOf("system")

    override fun onPackageLoaded(target: PackageTarget) {
        // 残留清理必须先于规则加载执行
        initHook(NbiLegacyCleanupHook(), true)
        initHook(NbiRuleInjectHook(), true)
    }
}
