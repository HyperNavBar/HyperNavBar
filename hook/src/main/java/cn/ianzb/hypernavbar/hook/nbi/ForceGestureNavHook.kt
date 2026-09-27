package cn.ianzb.hypernavbar.hook.nbi

import android.content.Context
import cn.ianzb.hypernavbar.hook.base.BaseHook
import cn.ianzb.hypernavbar.hook.prefs.HookKeys
import cn.ianzb.hypernavbar.hook.rule.hookVariant
import cn.ianzb.hypernavbar.hook.xposed.HookHelper
import cn.ianzb.hypernavbar.hook.xposed.Reflect

/**
 * 强制手势导航：让手势导航 / 手势线判定恒为「手势导航开 + 手势线未隐藏」。
 *
 * 官方在非手势导航或隐藏手势线时会跳过沉浸。判定位置随版本不同：
 * - HyperOS 4：集中在 [MiuiNBIManagerImpl] 的静态缓存方法 `isFullScreenGestureNavCached` /
 *   `isHideGestureLineCached`（`NavigationBarImmersiveController` 读取它们）；
 * - HyperOS 3.3（3.0.3XX）：位于 [NavigationBarImmersiveController] 的实例方法
 *   `isFullScreenGestureNav(Context)` / `isHideGestureLine(Context)`，构造与设置变化时调用，
 *   结果写入 `mIsFullScreenGestureNav` / `mIsHideGestureLine` 字段。
 */
class ForceGestureNavHook : BaseHook() {

    override val tag = "ForceGestureNav"

    override val key: String get() = HookKeys.FORCE_GESTURE

    override val versionGate = NbiHookSupport.NBI_GATE

    override val variants = listOf(
        hookVariant("os3", gate = { hyperOs { lt("4.0") } }) { hookController() },
        hookVariant("os4", gate = { hyperOs { ge("4.0") } }) { hookManager() },
    )

    private fun hookController() {
        val clazz = Reflect.findClass("com.android.internal.policy.NavigationBarImmersiveController", target.classLoader)
        HookHelper.hookReplace(Reflect.findMethod(clazz, "isFullScreenGestureNav", Context::class.java)) { true }
        HookHelper.hookReplace(Reflect.findMethod(clazz, "isHideGestureLine", Context::class.java)) { false }
    }

    private fun hookManager() {
        val clazz = Reflect.findClass("com.android.internal.policy.MiuiNBIManagerImpl", target.classLoader)
        HookHelper.hookReplace(Reflect.findMethod(clazz, "isFullScreenGestureNavCached")) { true }
        HookHelper.hookReplace(Reflect.findMethod(clazz, "isHideGestureLineCached")) { false }
    }
}
