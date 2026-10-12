package cn.ianzb.hypernavbar.ui.component.pref

import androidx.compose.runtime.Composable
import cn.ianzb.hypernavbar.prefs.ConfigState
import cn.ianzb.hypernavbar.prefs.OptionRegistry
import cn.ianzb.hypernavbar.prefs.OptionSpec

/** 解析依赖项：依赖项（含其自身的依赖链，如 总开关 → 功能开关 → 滑条）满足条件时组件启用。 */
@Composable
fun rememberDependencyEnabled(spec: OptionSpec): Boolean = dependencyEnabled(spec, depth = 0)

@Composable
private fun dependencyEnabled(spec: OptionSpec, depth: Int): Boolean {
    if (depth >= 8) return true
    val dependencyKey = spec.dependsOn ?: return true
    val dependencySpec = OptionRegistry.find(dependencyKey)
    val dependencyDefault = dependencySpec?.defaultBoolean ?: false
    val dependencyValue = ConfigState.bool(dependencyKey, dependencyDefault)
    if (spec.dependsOnValue != dependencyValue) return false
    return dependencySpec?.dependsOn == null || dependencyEnabled(dependencySpec, depth + 1)
}
