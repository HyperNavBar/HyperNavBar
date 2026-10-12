package cn.ianzb.hypernavbar.hook.nbi

import cn.ianzb.hypernavbar.hook.base.BaseHook
import cn.ianzb.hypernavbar.hook.xposed.HookHelper
import java.io.File

/**
 * 旧版本残留自动清理/还原（仅 HyperOS 4.x）。
 *
 * 旧版 HyperNavBar 会直接覆盖系统规则文件并留下 `.bak`。这里在 system_server 启动早期
 * 把 `.bak` 还原回目标文件、删除 `.bak` 与标记文件，使系统磁盘恢复为官方规则；
 * 4.x 上规则由运行期注入挂载，因此卸载模块后自动回到官方规则。
 *
 * HyperOS 3.3（3.0.3XX）自 3.1.0 起改回替换系统规则文件的方式挂载规则，
 * 写入的文件即为生效数据，因此 3.3 上不做还原（否则会与模块写入的规则互相覆盖），
 * 门禁见 [NbiHookSupport.OS4_GATE]。
 */
class NbiLegacyCleanupHook : BaseHook() {

    override val tag = "NbiLegacyCleanup"

    override val key: String get() = "cleanup_legacy"

    override val versionGate = NbiHookSupport.OS4_GATE

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
