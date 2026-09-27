package cn.ianzb.hypernavbar.xposed

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.libxposed.service.XposedService

/**
 * App 侧读取 / 重置 hook 兜底（安全模式）状态。
 *
 * 数据由 hook 进程写入 [SafeModeReader.GROUP] 远程偏好分组，与
 * `:hook` 的 `SafeModeManager.GROUP` 必须一致。
 */
object SafeModeReader {

    /** 远程偏好分组名，需与 hook 侧 `SafeModeManager.GROUP` 一致。 */
    const val GROUP = "hypernavbar_safe_mode"

    private const val SAFE_PREFIX = "safe_mode_"
    private const val COUNT_PREFIX = "crash_count_"
    private const val LOADING_PREFIX = "loading_since_"

    @Volatile
    private var service: XposedService? = null

    /** 当前处于安全模式（hook 被自动禁用）的包名集合。 */
    var safeModePackages by mutableStateOf<Set<String>>(emptySet())
        private set

    fun attach(service: XposedService?) {
        this.service = service
        refresh()
    }

    /** 重新读取远程偏好（hook 进程可能已更新）。 */
    fun refresh() {
        val prefs = remotePrefs()
        if (prefs == null) {
            safeModePackages = emptySet()
            return
        }
        val result = mutableSetOf<String>()
        runCatching {
            prefs.all.forEach { (key, value) ->
                if (key.startsWith(SAFE_PREFIX) && value == true) {
                    result += key.removePrefix(SAFE_PREFIX)
                }
            }
        }
        safeModePackages = result
    }

    fun isInSafeMode(packageName: String): Boolean = packageName in safeModePackages

    /** 最近一次记录的崩溃次数。 */
    fun crashCount(packageName: String): Int =
        remotePrefs()?.getInt("$COUNT_PREFIX$packageName", 0) ?: 0

    /** 退出安全模式并清空该包的崩溃计数。 */
    fun reset(packageName: String) = setSafeMode(packageName, false)

    /**
     * 手动启用 / 关闭某应用的安全模式。
     *
     * 启用后 hook 进程会在下次启动时跳过该包全部 hook；关闭时同时清空崩溃计数与加载时间戳。
     */
    fun setSafeMode(packageName: String, enabled: Boolean) {
        val prefs = remotePrefs() ?: return
        val edit = prefs.edit()
        if (enabled) {
            edit.putBoolean("$SAFE_PREFIX$packageName", true)
        } else {
            edit.remove("$SAFE_PREFIX$packageName")
                .remove("$COUNT_PREFIX$packageName")
                .remove("$LOADING_PREFIX$packageName")
        }
        edit.commit()
        refresh()
    }

    /** 退出全部安全模式。 */
    fun resetAll() {
        val prefs = remotePrefs() ?: return
        val edit = prefs.edit()
        prefs.all.keys
            .filter {
                it.startsWith(SAFE_PREFIX) ||
                    it.startsWith(COUNT_PREFIX) ||
                    it.startsWith(LOADING_PREFIX)
            }
            .forEach { edit.remove(it) }
        edit.commit()
        refresh()
    }

    private fun remotePrefs(): SharedPreferences? =
        runCatching { service?.getRemotePreferences(GROUP) }.getOrNull()
}
