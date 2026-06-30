package com.ianzb.hypernavbar

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
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "settings put secure enabled_accessibility_services $componentName"))
            process.waitFor() == 0
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
