package cn.ianzb.hypernavbar.hook.prefs

import android.content.SharedPreferences

/**
 * Hook 进程侧的配置读取（只读）。
 *
 * 数据由 App 侧写入 LSPosed 远程偏好，hook 进程通过
 * [io.github.libxposed.api.XposedInterface.getRemotePreferences] 读取。
 *
 * 键名需与 App 侧 `PrefsStore` 保持一致（统一加 `prefs_key_` 前缀）。
 */
@Suppress("unused")
object HookPrefs {

    /** 远程偏好分组名，需与 App 侧保持一致。 */
    const val GROUP = "hypernavbar_remote"

    private const val KEY_PREFIX = "prefs_key_"

    @Volatile
    private var prefs: SharedPreferences? = null

    fun init(remote: SharedPreferences) {
        prefs = remote
    }

    private fun key(key: String): String =
        if (key.startsWith(KEY_PREFIX)) key else KEY_PREFIX + key

    private inline fun <T> safe(defaultValue: T, read: (SharedPreferences) -> T): T {
        val target = prefs ?: return defaultValue
        return try {
            read(target)
        } catch (_: ClassCastException) {
            defaultValue
        }
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean =
        safe(defaultValue) { it.getBoolean(key(key), defaultValue) }

    fun getString(key: String, defaultValue: String? = null): String? =
        safe(defaultValue) { it.getString(key(key), defaultValue) }

    fun getInt(key: String, defaultValue: Int = 0): Int =
        safe(defaultValue) { it.getInt(key(key), defaultValue) }

    fun getLong(key: String, defaultValue: Long = 0L): Long =
        safe(defaultValue) { it.getLong(key(key), defaultValue) }

    fun getFloat(key: String, defaultValue: Float = 0f): Float =
        safe(defaultValue) { it.getFloat(key(key), defaultValue) }

    fun getStringSet(key: String, defaultValue: Set<String> = emptySet()): Set<String> =
        safe(defaultValue) { it.getStringSet(key(key), defaultValue) ?: defaultValue }

    fun getAll(): Map<String, *> = prefs?.all ?: emptyMap<String, Any>()
}
