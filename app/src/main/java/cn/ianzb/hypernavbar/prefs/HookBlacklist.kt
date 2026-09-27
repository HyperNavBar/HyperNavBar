package cn.ianzb.hypernavbar.prefs

/**
 * Hook 排除名单。
 *
 * 某些应用会检测到 Xposed 注入并主动退出（即使不安装任何 Hook 也会崩），
 * 这类应用只能从 LSPosed 作用域中排除。本名单用于：
 * - 「立即应用 / 强制生效」自动申请作用域时跳过这些包；
 * - 作用域页提供一键排除入口。
 */
object HookBlacklist {

    private const val KEY = "hook_blacklist"

    fun all(): Set<String> = PrefsStore.getStringSet(KEY, emptySet())

    fun contains(packageName: String): Boolean = packageName in all()

    fun add(packageName: String) {
        PrefsStore.put(KEY, all() + packageName)
    }

    fun remove(packageName: String) {
        PrefsStore.put(KEY, all() - packageName)
    }

    fun filter(packages: List<String>): List<String> {
        if (packages.isEmpty()) return packages
        val blocked = all()
        return if (blocked.isEmpty()) packages else packages.filter { it !in blocked }
    }
}
