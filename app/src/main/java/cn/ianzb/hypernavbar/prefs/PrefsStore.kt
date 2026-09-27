package cn.ianzb.hypernavbar.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * App 侧统一配置存储。
 *
 * - 物理存储：普通 SharedPreferences
 * - 跨进程：同步镜像到 LSPosed 远程偏好（hook 进程只读）
 */
@Suppress("unused")
object PrefsStore {

    const val PREFS_NAME = "hypernavbar_prefs"

    /** 远程偏好分组名，需与 :hook 的 HookPrefs.GROUP 一致。 */
    const val REMOTE_GROUP = "hypernavbar_remote"

    private const val KEY_PREFIX = "prefs_key_"

    @Volatile
    private var prefs: SharedPreferences? = null

    @Volatile
    private var remote: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun attachRemote(remotePrefs: SharedPreferences?) {
        remote = remotePrefs
        syncToRemote()
    }

    /**
     * 把本地已保存的全部配置同步到远程偏好，确保 hook 进程（含开机后）能读到。
     */
    fun syncToRemote() {
        val target = remote ?: return
        val local = prefs ?: return
        local.all.forEach { (storageKey, value) -> applyTo(target, storageKey, value) }
    }

    fun isRemoteAttached(): Boolean = remote != null

    fun key(key: String): String =
        if (key.startsWith(KEY_PREFIX)) key else KEY_PREFIX + key

    fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        prefs?.getBoolean(key(key), defaultValue) ?: defaultValue

    fun getInt(key: String, defaultValue: Int): Int =
        prefs?.getInt(key(key), defaultValue) ?: defaultValue

    fun getLong(key: String, defaultValue: Long): Long =
        prefs?.getLong(key(key), defaultValue) ?: defaultValue

    fun getFloat(key: String, defaultValue: Float): Float =
        prefs?.getFloat(key(key), defaultValue) ?: defaultValue

    fun getString(key: String, defaultValue: String?): String? =
        prefs?.getString(key(key), defaultValue) ?: defaultValue

    fun getStringSet(key: String, defaultValue: Set<String>): Set<String> =
        prefs?.getStringSet(key(key), defaultValue) ?: defaultValue

    fun put(key: String, value: Any?) {
        val storageKey = key(key)
        applyTo(prefs, storageKey, value)
        applyTo(remote, storageKey, value)
    }

    fun remove(key: String) {
        val storageKey = key(key)
        prefs?.edit { remove(storageKey) }
        remote?.edit { remove(storageKey) }
    }

    fun getAll(): Map<String, Any?> =
        prefs?.all?.mapKeys { it.key.removePrefix(KEY_PREFIX) } ?: emptyMap()

    fun clearAll() {
        prefs?.edit { clear() }
        remote?.edit { clear() }
    }

    private fun applyTo(target: SharedPreferences?, storageKey: String, value: Any?) {
        if (target == null) return
        target.edit {
            when (value) {
                null -> remove(storageKey)
                is Boolean -> putBoolean(storageKey, value)
                is Int -> putInt(storageKey, value)
                is Long -> putLong(storageKey, value)
                is Float -> putFloat(storageKey, value)
                is Double -> putFloat(storageKey, value.toFloat())
                is String -> putString(storageKey, value)
                is Set<*> -> putStringSet(storageKey, value.filterIsInstance<String>().toSet())
                else -> putString(storageKey, value.toString())
            }
        }
    }
}
