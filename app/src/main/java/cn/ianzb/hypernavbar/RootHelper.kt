package cn.ianzb.hypernavbar

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

object RootHelper {

    var isRootAvailable: Boolean = false
        private set

    suspend fun checkRoot(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec("su")
                val stream = DataOutputStream(process.outputStream)
                stream.writeBytes("echo rootok\n")
                stream.flush()
                stream.writeBytes("exit\n")
                stream.flush()
                process.waitFor()
                val exitCode = process.exitValue()
                val result = exitCode == 0
                isRootAvailable = result
                process.destroy()
                result
            } catch (_: Exception) {
                isRootAvailable = false
                false
            }
        }
    }

    fun grantOverlayPermission(context: Context): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow"))
            process.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    fun enableAccessibilityService(context: Context): Boolean {
        return try {
            val componentName = "${context.packageName}/.AppIdentifyAccessibilityService"
            // 先读取已有的无障碍服务列表，避免覆盖其他应用的权限
            val readProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "settings get secure enabled_accessibility_services"))
            val existing = BufferedReader(InputStreamReader(readProcess.inputStream)).use { it.readText().trim() }
            readProcess.waitFor()

            val currentList = if (existing.isNotEmpty() && existing != "null") existing else ""
            val merged = if (currentList.split(":").contains(componentName)) {
                currentList  // 已包含，无需重复添加
            } else if (currentList.isEmpty()) {
                componentName
            } else {
                "$currentList:$componentName"
            }
            val writeProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "settings put secure enabled_accessibility_services $merged"))
            writeProcess.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    fun isOverlayPermissionGranted(context: Context): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "appops get ${context.packageName} SYSTEM_ALERT_WINDOW"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.readText().trim()
            reader.close()
            process.waitFor()
            result.contains("allow")
        } catch (_: Exception) {
            false
        }
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "settings get secure enabled_accessibility_services"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.readText().trim()
            reader.close()
            process.waitFor()
            result.contains("${context.packageName}/.AppIdentifyAccessibilityService")
        } catch (_: Exception) {
            false
        }
    }
}
