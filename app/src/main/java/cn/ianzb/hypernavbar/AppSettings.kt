package cn.ianzb.hypernavbar

import android.content.Context
import androidx.core.content.edit
import cn.ianzb.hypernavbar.rules.RulesManager
import cn.ianzb.hypernavbar.rules.SystemVersionDetector
import org.json.JSONArray
import org.json.JSONObject

data class AppSettings(
    val themeMode: String = "System",
    val isFloatingNavbar: Boolean = false,
    val isLiquidGlass: Boolean = false,
    val isBlurEnabled: Boolean = true,
    val applyIntervalMinutes: Int = 1,
    val autoApplyAfterEdit: Boolean = true,
    val language: String = "",
    val forcedMode: String = "auto",
    val rulesConfigsJson: String = "",
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("themeMode", themeMode)
        json.put("isFloatingNavbar", isFloatingNavbar)
        json.put("isLiquidGlass", isLiquidGlass)
        json.put("isBlurEnabled", isBlurEnabled)
        json.put("applyIntervalMinutes", applyIntervalMinutes)
        json.put("autoApplyAfterEdit", autoApplyAfterEdit)
        json.put("language", language)
        json.put("forcedMode", forcedMode)
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
                    isBlurEnabled = obj.optBoolean("isBlurEnabled", true),
                    applyIntervalMinutes = obj.optInt("applyIntervalMinutes", 1),
                    autoApplyAfterEdit = obj.optBoolean("autoApplyAfterEdit", true),
                    language = obj.optString("language", ""),
                    forcedMode = obj.optString("forcedMode", "auto"),
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
        private const val KEY_BLUR_ENABLED = "blur_enabled"
        private const val KEY_APPLY_INTERVAL = "apply_interval"
        private const val KEY_AUTO_APPLY_AFTER_EDIT = "auto_apply_after_edit"

        fun load(context: Context): AppSettings {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val language = LocaleHelper.getSavedLanguage(context).code
            val forcedMode = SystemVersionDetector.getForcedMode(context)
            return AppSettings(
                themeMode = prefs.getString(KEY_THEME_MODE, "System") ?: "System",
                isFloatingNavbar = prefs.getBoolean(KEY_FLOATING_NAVBAR, false),
                isLiquidGlass = prefs.getBoolean(KEY_LIQUID_GLASS, false),
                isBlurEnabled = prefs.getBoolean(KEY_BLUR_ENABLED, true),
                applyIntervalMinutes = prefs.getInt(KEY_APPLY_INTERVAL, 1),
                autoApplyAfterEdit = prefs.getBoolean(KEY_AUTO_APPLY_AFTER_EDIT, true),
                language = language,
                forcedMode = forcedMode,
            )
        }

        fun save(context: Context, settings: AppSettings) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
                putString(KEY_THEME_MODE, settings.themeMode)
                putBoolean(KEY_FLOATING_NAVBAR, settings.isFloatingNavbar)
                putBoolean(KEY_LIQUID_GLASS, settings.isLiquidGlass)
                putBoolean(KEY_BLUR_ENABLED, settings.isBlurEnabled)
                putInt(KEY_APPLY_INTERVAL, settings.applyIntervalMinutes)
                putBoolean(KEY_AUTO_APPLY_AFTER_EDIT, settings.autoApplyAfterEdit)
            }
            // Restore language
            val lang = LocaleHelper.Language.entries.find { it.code == settings.language } ?: LocaleHelper.Language.SYSTEM
            LocaleHelper.setLanguage(context, lang)
            // Restore forced mode
            SystemVersionDetector.setForcedMode(context, settings.forcedMode)
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
