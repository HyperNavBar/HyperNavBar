package cn.ianzb.hypernavbar.hook.nbi

import cn.ianzb.hypernavbar.hook.base.BaseHook
import cn.ianzb.hypernavbar.hook.prefs.HookKeys
import cn.ianzb.hypernavbar.hook.xposed.HookHelper
import cn.ianzb.hypernavbar.hook.xposed.Reflect

/**
 * 忽略导航栏隐藏：让 `NavigationBarImmersiveController.isNavBarHidden` 恒为 false。
 *
 * 官方在导航栏隐藏时直接跳过沉浸适配。
 */
class ForceShowNavHook : BaseHook() {

    override val tag = "ForceShowNav"

    override val key: String get() = HookKeys.FORCE_SHOW_NAV

    override fun init() {
        val clazz = Reflect.findClass("com.android.internal.policy.NavigationBarImmersiveController", target.classLoader)
        val method = Reflect.findMethod(clazz, "isNavBarHidden")
        HookHelper.hookReplace(method) { false }
    }
}
