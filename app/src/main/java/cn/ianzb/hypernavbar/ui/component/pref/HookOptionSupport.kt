package cn.ianzb.hypernavbar.ui.component.pref

import androidx.compose.runtime.Composable
import cn.ianzb.hypernavbar.prefs.ConfigState
import cn.ianzb.hypernavbar.prefs.OptionRegistry
import cn.ianzb.hypernavbar.prefs.OptionSpec
import cn.ianzb.hypernavbar.xposed.XposedServiceManager

/** 解析依赖项：依赖项满足条件时组件启用。 */
@Composable
fun rememberDependencyEnabled(spec: OptionSpec): Boolean {
    val dependencyKey = spec.dependsOn ?: return true
    val dependencyDefault = OptionRegistry.find(dependencyKey)?.defaultBoolean ?: false
    val dependencyValue = ConfigState.bool(dependencyKey, dependencyDefault)
    return if (spec.dependsOnValue) dependencyValue else !dependencyValue
}

/**
 * 选项被启用时，自动为未授权的作用域目标发起申请。
 */
fun ensureScopeFor(spec: OptionSpec) {
    if (spec.targetPackages.isNotEmpty()) {
        XposedServiceManager.ensureScope(spec.targetPackages)
    }
}
