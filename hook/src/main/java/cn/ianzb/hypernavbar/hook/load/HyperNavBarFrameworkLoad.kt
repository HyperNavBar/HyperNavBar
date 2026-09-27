package cn.ianzb.hypernavbar.hook.load

import cn.ianzb.hypernavbar.hook.base.BaseLoad
import cn.ianzb.hypernavbar.hook.base.PackageTarget
import cn.ianzb.hypernavbar.hook.nbi.ForceBypassE2eHook
import cn.ianzb.hypernavbar.hook.nbi.ForceBypassVirtualDisplayHook
import cn.ianzb.hypernavbar.hook.nbi.ForceFullscreenHook
import cn.ianzb.hypernavbar.hook.nbi.ForceGestureNavHook
import cn.ianzb.hypernavbar.hook.nbi.ForceNbiEnableHook
import cn.ianzb.hypernavbar.hook.nbi.ForceNbiInitHook
import cn.ianzb.hypernavbar.hook.nbi.ForceShowNavHook
import cn.ianzb.hypernavbar.hook.prefs.HookKeys
import cn.ianzb.hypernavbar.hook.prefs.HookPrefs

/**
 * 应用进程侧（框架）Load：`*` 表示在所有应用进程生效。
 *
 * 每项功能拆分为独立 hook，便于在功能页按项显示成功 / 失败状态；
 * 全部受「强制生效」总开关 [HookKeys.MASTER] 控制，默认不安装以降低影响面。
 */
class HyperNavBarFrameworkLoad : BaseLoad() {

    override val targetPackages: List<String> = listOf("*")

    override fun onPackageLoaded(target: PackageTarget) {
        val master = HookPrefs.getBoolean(HookKeys.MASTER, false)
        initHook(ForceNbiEnableHook(), master)
        initHook(ForceNbiInitHook(), master && HookPrefs.getBoolean(HookKeys.IGNORE_VERSION, true))
        initHook(ForceBypassE2eHook(), master && HookPrefs.getBoolean(HookKeys.BYPASS_E2E, true))
        initHook(ForceBypassVirtualDisplayHook(), master && HookPrefs.getBoolean(HookKeys.BYPASS_VIRTUAL_DISPLAY, true))
        initHook(ForceGestureNavHook(), master && HookPrefs.getBoolean(HookKeys.FORCE_GESTURE, true))
        initHook(ForceShowNavHook(), master && HookPrefs.getBoolean(HookKeys.FORCE_SHOW_NAV, true))
        initHook(ForceFullscreenHook(), master && HookPrefs.getBoolean(HookKeys.FORCE_FULLSCREEN, true))
    }
}
