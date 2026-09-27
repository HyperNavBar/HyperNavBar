package cn.ianzb.hypernavbar.hook.nbi

import android.content.Context
import cn.ianzb.hypernavbar.hook.base.BaseHook
import cn.ianzb.hypernavbar.hook.prefs.HookKeys
import cn.ianzb.hypernavbar.hook.xposed.HookHelper
import cn.ianzb.hypernavbar.hook.xposed.Reflect

/**
 * 绕过虚拟显示限制：让 `DecorViewImmersiveImpl.isVirtualDisplay` 恒为 false。
 *
 * 官方对投屏 / 车机 / 镜像等虚拟显示直接跳过沉浸。
 */
class ForceBypassVirtualDisplayHook : BaseHook() {

    override val tag = "ForceBypassVirtualDisplay"

    override val key: String get() = HookKeys.BYPASS_VIRTUAL_DISPLAY

    override val versionGate = NbiHookSupport.NBI_GATE

    override fun init() {
        val immersive = Reflect.findClass("com.android.internal.policy.DecorViewImmersiveImpl", target.classLoader)
        val method = Reflect.findMethod(immersive, "isVirtualDisplay", Context::class.java)
        HookHelper.hookReplace(method) { false }
    }
}
