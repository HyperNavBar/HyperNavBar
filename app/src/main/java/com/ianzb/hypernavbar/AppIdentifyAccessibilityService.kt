package com.ianzb.hypernavbar

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent

class AppIdentifyAccessibilityService : AccessibilityService() {

    companion object {
        const val ACTION_FOREGROUND_CHANGED = "com.ianzb.hypernavbar.FOREGROUND_APP_CHANGED"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_ACTIVITY_NAME = "activity_name"
        const val EXTRA_APP_NAME = "app_name"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString()
        val className = event.className?.toString()

        if (packageName.isNullOrEmpty() || className.isNullOrEmpty()) return
        if (packageName == this.packageName) return

        val appName = resolveAppName(packageName)

        val intent = Intent(ACTION_FOREGROUND_CHANGED).apply {
            putExtra(EXTRA_PACKAGE_NAME, packageName)
            putExtra(EXTRA_ACTIVITY_NAME, className)
            putExtra(EXTRA_APP_NAME, appName)
        }
        sendBroadcast(intent)
    }

    override fun onInterrupt() {
        // No action needed on interrupt
    }

    private fun resolveAppName(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }
}
