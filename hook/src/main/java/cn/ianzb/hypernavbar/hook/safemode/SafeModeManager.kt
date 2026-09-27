package cn.ianzb.hypernavbar.hook.safemode

import android.content.Context
import android.content.SharedPreferences
import cn.ianzb.hypernavbar.hook.xposed.HookHelper
import io.github.libxposed.api.XposedInterface
import org.json.JSONObject
import java.io.File

/**
 * Hook 兜底 / 安全模式。
 *
 * 注意：libxposed 的远程偏好/远程文件在被注入的应用进程里都是**只读**的，
 * 因此本进程**不能**写入远程偏好（否则会抛异常并可能拖垮应用）。
 *
 * - 「手动安全模式」由 App 侧写入远程偏好，本进程只读；
 * - 「崩溃自动兜底」把计数状态写入**目标应用自身的 cache 目录**（同 uid，可写）。
 */
object SafeModeManager {

    /** 远程偏好分组名，需与 App 侧 `SafeModeReader.GROUP` 保持一致。 */
    const val GROUP = "hypernavbar_safe_mode"

    private const val CRASH_WINDOW_MS = 60_000L
    private const val SURVIVE_MS = 15_000L
    private const val DEFAULT_THRESHOLD = 3
    private const val CRITICAL_THRESHOLD = 2
    private const val STATE_FILE = "nbi_safe_mode.json"

    private val CRITICAL_PACKAGES = setOf(
        "android",
        "system",
        "com.android.systemui",
        "com.android.settings",
        "com.miui.home",
        "com.miui.securitycenter",
    )

    @Volatile
    private var remote: SharedPreferences? = null

    fun init(module: XposedInterface) {
        remote = runCatching { module.getRemotePreferences(GROUP) }.getOrNull()
    }

    /** App 侧手动开启的安全模式（远程偏好，只读）。 */
    private fun isManualSafe(packageName: String): Boolean =
        runCatching { remote?.getBoolean("safe_mode_$packageName", false) == true }.getOrDefault(false)

    /**
     * 进程启动时调用。
     *
     * @return true 表示该包应跳过全部 hook（手动安全模式或自动兜底）。
     */
    fun handleStart(context: Context?, packageName: String): Boolean {
        if (isManualSafe(packageName)) {
            HookHelper.log("SafeMode: $packageName manual safe mode, skip hooks")
            return true
        }
        if (context == null) return false
        return runCatching { autoHandle(context, packageName) }
            .onFailure { HookHelper.log("SafeMode handleStart failed: $packageName", it) }
            .getOrDefault(false)
    }

    private fun autoHandle(context: Context, packageName: String): Boolean {
        val file = File(context.cacheDir, STATE_FILE)
        val state = readState(file)
        if (state.optBoolean("safe", false)) {
            HookHelper.log("SafeMode: $packageName is in safe mode, skip hooks")
            return true
        }

        val now = System.currentTimeMillis()
        val last = state.optLong("loadingSince", 0L)
        var count = state.optInt("count", 0)
        val crashedLastRun = last != 0L && now - last in 0..CRASH_WINDOW_MS

        if (crashedLastRun) {
            count += 1
            if (count >= thresholdOf(packageName)) {
                state.put("safe", true)
                state.put("count", count)
                state.put("loadingSince", 0L)
                writeState(file, state)
                HookHelper.log("SafeMode: $packageName disabled after $count crashes")
                return true
            }
            HookHelper.log("SafeMode: $packageName crash count = $count")
        } else {
            count = 0
        }

        state.put("loadingSince", now)
        state.put("count", count)
        writeState(file, state)
        scheduleSurviveReset(file, now)
        return false
    }

    private fun scheduleSurviveReset(file: File, marker: Long) {
        Thread {
            try {
                Thread.sleep(SURVIVE_MS)
            } catch (_: InterruptedException) {
                return@Thread
            }
            runCatching {
                val state = readState(file)
                if (state.optLong("loadingSince", 0L) == marker) {
                    state.put("loadingSince", 0L)
                    state.put("count", 0)
                    writeState(file, state)
                }
            }
        }.apply {
            isDaemon = true
            name = "NbiSafeMode"
        }.start()
    }

    private fun readState(file: File): JSONObject =
        runCatching { if (file.exists()) JSONObject(file.readText()) else JSONObject() }
            .getOrDefault(JSONObject())

    private fun writeState(file: File, state: JSONObject) {
        runCatching { file.writeText(state.toString()) }
    }

    private fun thresholdOf(packageName: String): Int =
        if (packageName in CRITICAL_PACKAGES) CRITICAL_THRESHOLD else DEFAULT_THRESHOLD
}
