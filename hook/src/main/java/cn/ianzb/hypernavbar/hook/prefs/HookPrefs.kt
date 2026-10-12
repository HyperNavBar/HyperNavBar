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

    /** 已注册的偏好变化监听（持有强引用，避免被 GC 后不再回调）。 */
    private val changeListeners =
        java.util.Collections.synchronizedList(
            mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()
        )

    fun init(remote: SharedPreferences) {
        prefs = remote
    }

    /**
     * 注册偏好变化监听（宿主 / 框架不支持时静默忽略）。
     *
     * 供需要「改配置即时生效」的功能使用；不支持的场景下功能退化为目标进程重启后生效，
     * 因此不要把它当作唯一生效路径，仍要在使用点按当前配置读取一次。
     */
    fun registerListener(onChange: () -> Unit) {
        val target = prefs ?: return
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChange() }
        changeListeners.add(listener)
        runCatching { target.registerOnSharedPreferenceChangeListener(listener) }
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
