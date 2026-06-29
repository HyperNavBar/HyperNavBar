package com.ianzb.hypernavbar.rules

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit

object SystemVersionDetector {

    private const val PREFS_NAME = "immersion_prefs"
    private const val KEY_MODE = "forced_mode"
    private const val MODE_AUTO = "auto"

    enum class OsMode { OS22, OS30, OS33 }

    @Suppress("unused")
    fun getHyperVersion(): String {
        return getProp("ro.mi.os.version.name")
    }

    fun getSystemVersionIncremental(): String {
        return getProp("ro.mi.os.version.incremental").ifEmpty { getProp("ro.system.build.version.incremental") }
    }

    fun detectOsMode(): OsMode? {
        val jsonFile = "/system_ext/etc/nbi/navigation_bar_immersive_rules_list.json"
        val xmlFile = "/system_ext/etc/nbi/navigation_bar_immersive_rules_list.xml"

        val jsonContent = readFileAsRoot(jsonFile)
        if (jsonContent.isNotEmpty()) {
            return if (jsonContent.contains("sf_sampling_mode")) OsMode.OS33 else OsMode.OS30
        }

        val xmlContent = readFileAsRoot(xmlFile)
        if (xmlContent.isNotEmpty()) {
            return OsMode.OS22
        }

        return null
    }

    fun getForcedMode(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MODE, MODE_AUTO) ?: MODE_AUTO
    }

    fun setForcedMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_MODE, mode)
        }
    }

    fun getEffectiveOsMode(context: Context): OsMode? {
        val forced = getForcedMode(context)
        return when (forced) {
            "3.3" -> OsMode.OS33
            "3.0" -> OsMode.OS30
            "2.2" -> OsMode.OS22
            else -> detectOsMode()
        }
    }

    private fun readFileAsRoot(path: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat $path"))
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val content = reader.readText()
            val exitCode = process.waitFor()
            reader.close()
            if (exitCode != 0) "" else content
        } catch (_: Exception) {
            ""
        }
    }

    @SuppressLint("PrivateApi")
    private fun getProp(property: String): String {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getDeclaredMethod("get", String::class.java)
            method.invoke(null, property) as? String ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    data class ImmersionStatus(val version: String, val isSupported: Boolean)

    fun getImmersionStatus(context: Context): ImmersionStatus {
        val mode = getEffectiveOsMode(context)
        return if (mode != null) {
            ImmersionStatus(
                when (mode) {
                    OsMode.OS22 -> "2.2"
                    OsMode.OS30 -> "3.0"
                    OsMode.OS33 -> "3.3"
                },
                true
            )
        } else {
            ImmersionStatus("", false)
        }
    }
}
