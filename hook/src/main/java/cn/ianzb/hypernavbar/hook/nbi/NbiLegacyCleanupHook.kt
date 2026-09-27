package cn.ianzb.hypernavbar.hook.nbi

import cn.ianzb.hypernavbar.hook.base.BaseHook
import cn.ianzb.hypernavbar.hook.xposed.HookHelper
import java.io.File

/**
 * 旧版本残留自动清理/还原。
 *
 * 旧版 HyperNavBar 会直接覆盖系统规则文件并留下 `.bak`。这里在 system_server 启动早期
 * 把 `.bak` 还原回目标文件、删除 `.bak` 与标记文件，使系统磁盘恢复为官方规则；
 * 运行时再由规则注入 hook 覆盖，因此卸载模块后自动回到官方规则。
 */
class NbiLegacyCleanupHook : BaseHook() {

    override val tag = "NbiLegacyCleanup"

    override val key: String get() = "cleanup_legacy"

    override fun init() {
        runCatching { cleanup() }
            .onFailure { HookHelper.log("legacy cleanup failed", it) }
    }

    private fun cleanup() {
        val dir = File("/data/system")
        restore(dir, "cloudFeature_navigation_bar_immersive_rules_list.json")
        restore(dir, "cloudFeature_navigation_bar_immersive_rules_list.xml")

        val marker = File(dir, "HyperNavBarRules")
        if (marker.exists() && marker.delete()) {
            HookHelper.log("removed legacy marker ${marker.path}")
        }
    }

    private fun restore(dir: File, name: String) {
        val target = File(dir, name)
        val backup = File(dir, "$name.bak")
        if (!backup.exists()) return
        runCatching {
            target.writeBytes(backup.readBytes())
            backup.delete()
            HookHelper.log("restored $name from backup")
        }.onFailure { HookHelper.log("restore $name failed", it) }
    }
}
