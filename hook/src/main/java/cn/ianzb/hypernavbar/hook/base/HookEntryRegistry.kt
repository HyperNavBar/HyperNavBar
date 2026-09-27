package cn.ianzb.hypernavbar.hook.base

import cn.ianzb.hypernavbar.hook.load.HyperNavBarFrameworkLoad
import cn.ianzb.hypernavbar.hook.load.HyperNavBarSystemLoad

/**
 * 目标 Load 注册表。
 *
 * - [targetPackages] 含 `*` 的 Load 视为「全局 Load」，随系统框架在任何应用进程生效；
 * - 含 `system` 的 Load 在 system_server（`onSystemServerStarting`）生效。
 */
object HookEntryRegistry {

    private const val WILDCARD = "*"
    private const val SYSTEM_SERVER = "system"

    private val loads: List<BaseLoad> = listOf(
        HyperNavBarSystemLoad(),
        HyperNavBarFrameworkLoad(),
    )

    fun loadsFor(packageName: String): List<BaseLoad> =
        loads.filter { it.targetPackages.contains(packageName) || it.targetPackages.contains(WILDCARD) }

    fun loadsForSystemServer(): List<BaseLoad> =
        loads.filter { it.targetPackages.contains(SYSTEM_SERVER) }

    fun all(): List<BaseLoad> = loads
}
