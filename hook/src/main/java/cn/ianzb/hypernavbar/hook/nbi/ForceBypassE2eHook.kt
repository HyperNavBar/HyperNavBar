package cn.ianzb.hypernavbar.hook.nbi

import android.content.Context
import cn.ianzb.hypernavbar.hook.base.BaseHook
import cn.ianzb.hypernavbar.hook.prefs.HookKeys
import cn.ianzb.hypernavbar.hook.xposed.HookHelper
import cn.ianzb.hypernavbar.hook.xposed.Reflect

/**
 * 绕过强制边到边：清除应用在 PhoneWindow 上自设的 `mEdgeToEdgeEnforced`。
 *
 * 官方 `DecorViewImmersiveImpl.onAttachedToWindow` 遇到该标记会直接跳过整套沉浸逻辑。
 */
class ForceBypassE2eHook : BaseHook() {

    override val tag = "ForceBypassE2e"

    override val key: String get() = HookKeys.BYPASS_E2E

    override fun init() {
        val loader = target.classLoader
        val decorClass = Reflect.findClass("com.android.internal.policy.DecorView", loader)
        val immersive = Reflect.findClass("com.android.internal.policy.DecorViewImmersiveImpl", loader)
        val method = Reflect.findMethod(immersive, "onAttachedToWindow", decorClass, Context::class.java)
        HookHelper.hookBefore(method) { param ->
            NbiHookSupport.clearEdgeToEdge(param.args.getOrNull(0))
        }
    }
}
