package cn.ianzb.hypernavbar.hook.xposed

import android.content.Context
import cn.ianzb.hypernavbar.hook.base.HookEntryRegistry
import cn.ianzb.hypernavbar.hook.base.PackageTarget
import cn.ianzb.hypernavbar.hook.prefs.HookPrefs
import cn.ianzb.hypernavbar.hook.safemode.SafeModeManager
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

/**
 * libxposed API 102 模块入口。
 *
 * 负责：初始化封装层、按包分发 Load、system_server 分发。
 */
class XposedEntry : XposedModule() {

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        HookHelper.init(this)
        HookPrefs.init(getRemotePreferences(HookPrefs.GROUP))
        SafeModeManager.init(this)
        HookHelper.log("module loaded in ${param.processName}")
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (!param.isFirstPackage) return
        // 保证开机 / 冷启动时即使 onModuleLoaded 顺序异常也能读到最新配置。
        HookPrefs.init(getRemotePreferences(HookPrefs.GROUP))
        SafeModeManager.init(this)
        // 兜底：手动安全模式或重复崩溃时跳过该包全部 hook，避免应用反复崩溃。
        if (SafeModeManager.handleStart(applicationContext(), param.packageName)) return
        val target = PackageTarget.from(param)
        HookEntryRegistry.loadsFor(target.packageName).forEach { load ->
            runCatching { load.onPackageReady(target) }
                .onFailure { HookHelper.log("load failed for ${target.packageName}", it) }
        }
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        HookPrefs.init(getRemotePreferences(HookPrefs.GROUP))
        SafeModeManager.init(this)
        if (SafeModeManager.handleStart(null, "system")) {
            HookHelper.log("SafeMode: system_server is in safe mode, skip hooks")
            return
        }
        val target = PackageTarget.fromSystemServer(param)
        HookEntryRegistry.loadsForSystemServer().forEach { load ->
            runCatching { load.onPackageReady(target) }
                .onFailure { HookHelper.log("system_server load failed", it) }
        }
    }

    @Suppress("PrivateApi")
    private fun applicationContext(): Context? = runCatching {
        Class.forName("android.app.ActivityThread")
            .getMethod("currentApplication")
            .invoke(null) as? Context
    }.getOrNull()
}
