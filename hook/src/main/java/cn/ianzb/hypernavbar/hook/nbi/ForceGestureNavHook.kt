package cn.ianzb.hypernavbar.hook.nbi

import cn.ianzb.hypernavbar.hook.base.BaseHook
import cn.ianzb.hypernavbar.hook.prefs.HookKeys
import cn.ianzb.hypernavbar.hook.xposed.HookHelper
import cn.ianzb.hypernavbar.hook.xposed.Reflect

/**
 * 强制手势导航：让手势导航 / 手势线缓存判定恒为「手势导航开 + 手势线未隐藏」。
 *
 * 官方在非手势导航或隐藏手势线时会跳过沉浸。
 */
class ForceGestureNavHook : BaseHook() {

    override val tag = "ForceGestureNav"

    override val key: String get() = HookKeys.FORCE_GESTURE

    override val versionGate = NbiHookSupport.OS4_GATE

    override fun init() {
        val clazz = Reflect.findClass("com.android.internal.policy.MiuiNBIManagerImpl", target.classLoader)
        val fullScreenGesture = Reflect.findMethod(clazz, "isFullScreenGestureNavCached")
        val hideGestureLine = Reflect.findMethod(clazz, "isHideGestureLineCached")
        HookHelper.hookReplace(fullScreenGesture) { true }
        HookHelper.hookReplace(hideGestureLine) { false }
    }
}
