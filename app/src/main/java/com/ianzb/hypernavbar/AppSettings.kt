package com.ianzb.hypernavbar

import android.content.Context
import androidx.core.content.edit
import com.ianzb.hypernavbar.rules.RulesManager
import org.json.JSONArray
import org.json.JSONObject

data class AppSettings(
    val themeMode: String = "System",
    val isFloatingNavbar: Boolean = false,
    val isLiquidGlass: Boolean = false,
    val applyIntervalMinutes: Int = 0,
    val rulesConfigsJson: String = "",
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("themeMode", themeMode)
        json.put("isFloatingNavbar", isFloatingNavbar)
        json.put("isLiquidGlass", isLiquidGlass)
        json.put("applyIntervalMinutes", applyIntervalMinutes)
        json.put("rulesConfigs", JSONArray(rulesConfigsJson.ifEmpty { "[]" }))
        return json.toString(2)
    }

    companion object {
        fun fromJson(json: String): AppSettings {
            return try {
                val obj = JSONObject(json)
                AppSettings(
                    themeMode = obj.optString("themeMode", "System"),
                    isFloatingNavbar = obj.optBoolean("isFloatingNavbar", false),
                    isLiquidGlass = obj.optBoolean("isLiquidGlass", false),
                    applyIntervalMinutes = obj.optInt("applyIntervalMinutes", 0),
                    rulesConfigsJson = obj.optJSONArray("rulesConfigs")?.toString() ?: "",
                )
            } catch (_: Exception) {
                AppSettings()
            }
        }

        private const val PREFS_NAME = "app_settings"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_FLOATING_NAVBAR = "floating_navbar"
        private const val KEY_LIQUID_GLASS = "liquid_glass"
        private const val KEY_APPLY_INTERVAL = "apply_interval"

        fun load(context: Context): AppSettings {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return AppSettings(
                themeMode = prefs.getString(KEY_THEME_MODE, "System") ?: "System",
                isFloatingNavbar = prefs.getBoolean(KEY_FLOATING_NAVBAR, false),
                isLiquidGlass = prefs.getBoolean(KEY_LIQUID_GLASS, false),
                applyIntervalMinutes = prefs.getInt(KEY_APPLY_INTERVAL, 0),
            )
        }

        fun save(context: Context, settings: AppSettings) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
                putString(KEY_THEME_MODE, settings.themeMode)
                putBoolean(KEY_FLOATING_NAVBAR, settings.isFloatingNavbar)
                putBoolean(KEY_LIQUID_GLASS, settings.isLiquidGlass)
                putInt(KEY_APPLY_INTERVAL, settings.applyIntervalMinutes)
            }
        }

        fun importFromJson(context: Context, json: String): AppSettings {
            val settings = fromJson(json)
            save(context, settings)

            if (settings.rulesConfigsJson.isNotEmpty()) {
                try {
                    val arr = JSONArray(settings.rulesConfigsJson)
                    RulesManager.importFromJson(context, arr)
                } catch (_: Exception) {}
            }
            return settings
        }
    }
}
