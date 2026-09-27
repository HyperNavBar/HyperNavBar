package cn.ianzb.hypernavbar.hook.nbi

import android.content.Context
import cn.ianzb.hypernavbar.hook.base.BaseHook
import cn.ianzb.hypernavbar.hook.prefs.HookKeys
import cn.ianzb.hypernavbar.hook.prefs.HookPrefs
import cn.ianzb.hypernavbar.hook.xposed.HookHelper
import cn.ianzb.hypernavbar.hook.xposed.Reflect

/**
 * 应用进程侧：强制启用 NBI。
 *
 * 官方 `MiuiNBIManagerImpl.init` 会因 `disableVersionCode` / `allowSystemOverride` 把
 * `mIsNBIEnable` 置为 false，导致整套沉浸逻辑不执行。这里在 init 之后强制置 true，
 * 使模块注入的规则能够生效。
 */
class ForceNbiInitHook : BaseHook() {

    override val tag = "ForceNbiInit"

    override val key: String get() = HookKeys.IGNORE_VERSION

    override val versionGate = NbiHookSupport.NBI_GATE

    override fun init() {
        val clazz = Reflect.findClass("com.android.internal.policy.MiuiNBIManagerImpl", target.classLoader)
        val method = Reflect.findMethod(clazz, "init", Context::class.java)
        HookHelper.hookAfter(method) { param ->
            if (!HookPrefs.getBoolean(HookKeys.MASTER, false)) return@hookAfter
            if (!HookPrefs.getBoolean(HookKeys.IGNORE_VERSION, true)) return@hookAfter
            val self = param.thisObject ?: return@hookAfter
            runCatching { Reflect.setObjectField(self, "mIsNBIEnable", true) }
        }
    }
}
