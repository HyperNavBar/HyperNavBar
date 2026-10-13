package cn.ianzb.hypernavbar.ui.component.pref

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import cn.ianzb.hypernavbar.hook.rule.SystemVersion
import cn.ianzb.hypernavbar.prefs.ConfigState
import cn.ianzb.hypernavbar.prefs.OptionRegistry
import cn.ianzb.hypernavbar.prefs.OptionSpec

/**
 * 解析可用性：系统版本要求与依赖项都满足时组件才启用。
 *
 * - 系统版本要求见 [OptionSpec.osRequirement]（与 Hook 侧门禁同源，未满足时禁用灰显、不隐藏）；
 * - 依赖链（如 总开关 → 功能开关 → 滑条）中任一层未满足即禁用，前置项自身被版本禁用时同样禁用。
 */
@Composable
fun rememberDependencyEnabled(spec: OptionSpec): Boolean {
    val osVersion = remember { SystemVersion.current() }
    if (!spec.osRequirement.satisfiedBy(osVersion)) return false
    return dependencyEnabled(spec, depth = 0, osVersion = osVersion)
}

@Composable
private fun dependencyEnabled(spec: OptionSpec, depth: Int, osVersion: SystemVersion): Boolean {
    if (depth >= 8) return true
    val dependencyKey = spec.dependsOn ?: return true
    val dependencySpec = OptionRegistry.find(dependencyKey)
    // 前置项本身被系统版本禁用时，跟随禁用
    if (dependencySpec != null && !dependencySpec.osRequirement.satisfiedBy(osVersion)) return false
    val dependencyDefault = dependencySpec?.defaultBoolean ?: false
    val dependencyValue = ConfigState.bool(dependencyKey, dependencyDefault)
    if (spec.dependsOnValue != dependencyValue) return false
    return dependencySpec?.dependsOn == null || dependencyEnabled(dependencySpec, depth + 1, osVersion)
}
