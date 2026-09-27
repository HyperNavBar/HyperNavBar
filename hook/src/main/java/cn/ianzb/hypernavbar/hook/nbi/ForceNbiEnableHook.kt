package cn.ianzb.hypernavbar.hook.nbi

import android.content.Context
import cn.ianzb.hypernavbar.hook.base.BaseHook
import cn.ianzb.hypernavbar.hook.prefs.HookKeys
import cn.ianzb.hypernavbar.hook.xposed.HookHelper
import cn.ianzb.hypernavbar.hook.xposed.Reflect

/**
 * 强制生效（总开关）：确保目标进程内 NBI 已初始化并启用。
 *
 * 官方 `ActivityThreadImpl` 的 preInit 条件可能不满足（如系统属性 / 算力等级），
 * 导致 `MiuiNBIManagerImpl.init` 从未执行。这里在窗口附加时补一次初始化。
 */
class ForceNbiEnableHook : BaseHook() {

    override val tag = "ForceNbiEnable"

    override val key: String get() = HookKeys.MASTER

    override fun init() {
        val loader = target.classLoader
        val decorClass = Reflect.findClass("com.android.internal.policy.DecorView", loader)
        val immersive = Reflect.findClass("com.android.internal.policy.DecorViewImmersiveImpl", loader)
        val method = Reflect.findMethod(immersive, "onAttachedToWindow", decorClass, Context::class.java)
        HookHelper.hookBefore(method) { param ->
            NbiHookSupport.ensureInit(loader, param.args.getOrNull(1) as? Context)
        }
    }
}
