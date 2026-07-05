package com.ianzb.hypernavbar

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.ianzb.hypernavbar.ui.screen.rules.FloatingIdentifyService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class AppIdentifyAccessibilityService : AccessibilityService() {

    companion object {
        const val ACTION_FOREGROUND_CHANGED = "com.ianzb.hypernavbar.FOREGROUND_APP_CHANGED"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_ACTIVITY_NAME = "activity_name"
        const val EXTRA_APP_NAME = "app_name"

        private const val TAG = "AppIdentify"
        private const val POLL_INTERVAL_MS = 300L

        // 黑名单列表：系统桌面、系统界面、本应用自身
        private val BLACKLIST_PACKAGES = setOf(
            "com.miui.home",           // 小米桌面
            "com.mi.android.globallauncher",  // 小米全球桌面
            "com.android.launcher3",    // AOSP 桌面
            "com.android.launcher",     // 旧版 AOSP 桌面
            "com.ianzb.hypernavbar",   // 本应用自身
        )

        // 系统UI包名（用于匹配）
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"

        /**
         * 检查包名是否在黑名单中
         */
        fun isBlacklisted(packageName: String): Boolean {
            return BLACKLIST_PACKAGES.contains(packageName) ||
                   packageName == SYSTEM_UI_PACKAGE ||
                   packageName.startsWith("com.android.systemui")
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private var lastPackageName: String = ""
    private var lastActivityName: String = ""
    private var lastAppName: String = ""
    private var isPolling = false
    private var pollJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()

        // 配置无障碍服务（仅用于保持服务存活）
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.DEFAULT
            notificationTimeout = 1000
        }
        serviceInfo = info

        // 直接启动dumpsys轮询
        startDumpsysPolling()
    }

    private fun startDumpsysPolling() {
        if (isPolling) return
        isPolling = true

        pollJob = serviceScope.launch {
            while (isActive) {
                try {
                    val result = withContext(Dispatchers.IO) {
                        getForegroundActivityViaDumpsys()
                    }

                    if (result != null) {
                        val (packageName, activityName) = result

                        if (packageName != lastPackageName || activityName != lastActivityName) {
                            lastPackageName = packageName
                            lastActivityName = activityName

                            val appName = withContext(Dispatchers.IO) {
                                resolveAppName(packageName)
                            }
                            lastAppName = appName

                            Log.d(TAG, "Foreground changed: pkg=$packageName activity=$activityName app=$appName")

                            notifyForegroundApp(packageName, appName, activityName)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Polling error: ${e.message}")
                }

                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * 使用dumpsys命令获取当前前台Activity（最可靠的方法）
     * 需要root权限
     */
    private fun getForegroundActivityViaDumpsys(): Pair<String, String>? {
        return try {
            // 方法1: dumpsys activity activities | grep ResumedActivity (Android 10+)
            val result = executeCommand("dumpsys activity activities | grep ResumedActivity")
            if (result != null) {
                parseResumedActivity(result)
            } else {
                // 方法2: dumpsys window | grep mCurrentFocus (备选)
                val windowResult = executeCommand("dumpsys window | grep mCurrentFocus")
                if (windowResult != null) {
                    parseCurrentFocus(windowResult)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "dumpsys error: ${e.message}")
            null
        }
    }

    private fun executeCommand(command: String): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText().trim()
            process.waitFor()
            if (output.isNotEmpty()) output else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 解析dumpsys activity activities的输出
     * 格式: ResumedActivity: ActivityRecord{... u0 com.example.app/.MainActivity t123}
     */
    private fun parseResumedActivity(output: String): Pair<String, String>? {
        try {
            val lines = output.lines()
            for (line in lines) {
                if (line.contains("ResumedActivity")) {
                    val regex = Regex("""(\S+)/(\S+)\s""")
                    val match = regex.find(line)
                    if (match != null) {
                        val packageName = match.groupValues[1]
                        val activityName = match.groupValues[2].removeSuffix("}")

                        val fullActivityName = if (activityName.startsWith(".")) {
                            "$packageName$activityName"
                        } else {
                            activityName
                        }

                        return Pair(packageName, fullActivityName)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Parse ResumedActivity error: ${e.message}")
        }
        return null
    }

    /**
     * 解析dumpsys window的输出
     * 格式: mCurrentFocus=Window{abc123 u0 com.example.app/com.example.app.MainActivity}
     */
    private fun parseCurrentFocus(output: String): Pair<String, String>? {
        try {
            val regex = Regex("""mCurrentFocus=.*?\s(\S+)/(\S+)[\}\s]""")
            val match = regex.find(output)
            if (match != null) {
                val packageName = match.groupValues[1]
                val activityName = match.groupValues[2]
                return Pair(packageName, activityName)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Parse mCurrentFocus error: ${e.message}")
        }
        return null
    }

    /**
     * AccessibilityService事件回调（不使用，仅用于保持服务存活）
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // 不处理事件，完全依赖dumpsys轮询
    }

    private fun notifyForegroundApp(pkg: String, appName: String, activity: String) {
        // 检查黑名单
        if (isBlacklisted(pkg)) {
            Log.d(TAG, "Package $pkg is blacklisted, skipping notification")
            return
        }

        // 直接调用（同进程，更可靠）
        FloatingIdentifyService.notifyForegroundApp(pkg, appName, activity)

        // 同时发送广播（为了兼容性）
        val intent = Intent(ACTION_FOREGROUND_CHANGED).apply {
            putExtra(EXTRA_PACKAGE_NAME, pkg)
            putExtra(EXTRA_ACTIVITY_NAME, activity)
            putExtra(EXTRA_APP_NAME, appName)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                sendBroadcast(intent, null)
            } else {
                @Suppress("DEPRECATION")
                sendBroadcast(intent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error sending broadcast: ${e.message}")
        }
    }

    private fun resolveAppName(packageName: String): String {
        return try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PackageManager.MATCH_ALL
            } else {
                0
            }
            val appInfo = packageManager.getApplicationInfo(packageName, flags)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    override fun onInterrupt() {
        stopPolling()
    }

    override fun onDestroy() {
        stopPolling()
        super.onDestroy()
    }

    private fun stopPolling() {
        isPolling = false
        pollJob?.cancel()
        handler.removeCallbacksAndMessages(null)
    }
}
