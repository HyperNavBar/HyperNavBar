package cn.ianzb.hypernavbar

import android.content.Context
import androidx.core.content.edit
import cn.ianzb.hypernavbar.rules.RulesManager
import cn.ianzb.hypernavbar.rules.SystemVersionDetector
import org.json.JSONArray
import org.json.JSONObject

enum class BottomBarStyle(val value: Int) {
    HyperOS(1), IOS(2);

    companion object {
        fun fromSaved(value: Any?, floating: Boolean?, liquid: Boolean): BottomBarStyle =
            when (value?.toString()?.toIntOrNull()) {
                HyperOS.value, 0 -> HyperOS
                IOS.value -> IOS
                else -> if (floating == true && liquid) IOS else HyperOS
            }
    }
}

enum class RailStyle(val value: Int) {
    Off(0), Capsule(1), Left(2);

    companion object {
        fun fromSaved(value: Any?, floating: Boolean?): RailStyle =
            entries.firstOrNull { it.name == value || it.value == value?.toString()?.toIntOrNull() }
                ?: if (floating == true) Off else Capsule
    }
}

data class AppSettings(
    val themeMode: String = "System",
    val bottomBarStyle: BottomBarStyle = BottomBarStyle.HyperOS,
    val railStyle: RailStyle = RailStyle.Capsule,
    val isBlurEnabled: Boolean = true,
    val applyIntervalMinutes: Int = 1,
    val autoApplyAfterEdit: Boolean = true,
    val checkUpdateOnLaunch: Boolean = true,
    val language: String = "",
    val forcedMode: String = "auto",
    val rulesConfigsJson: String = "",
    val hideLauncherIcon: Boolean = false,
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("themeMode", themeMode)
        json.put("bottomBarStyle", bottomBarStyle.value)
        json.put("railStyle", railStyle.value)
        json.put("isBlurEnabled", isBlurEnabled)
        json.put("applyIntervalMinutes", applyIntervalMinutes)
        json.put("autoApplyAfterEdit", autoApplyAfterEdit)
        json.put("checkUpdateOnLaunch", checkUpdateOnLaunch)
        json.put("language", language)
        json.put("forcedMode", forcedMode)
        json.put("rulesConfigs", JSONArray(rulesConfigsJson.ifEmpty { "[]" }))
        json.put("hideLauncherIcon", hideLauncherIcon)
        return json.toString(2)
    }

    companion object {
        fun fromJson(json: String): AppSettings {
            return try {
                val obj = JSONObject(json)
                val floatingNavbar = obj.opt("isFloatingNavbar") as? Boolean
                AppSettings(
                    themeMode = obj.optString("themeMode", "System"),
                    bottomBarStyle = BottomBarStyle.fromSaved(obj.opt("bottomBarStyle"), floatingNavbar, obj.optBoolean("isLiquidGlass", false)),
                    railStyle = RailStyle.fromSaved(obj.opt("railStyle"), floatingNavbar),
                    isBlurEnabled = obj.optBoolean("isBlurEnabled", true),
                    applyIntervalMinutes = obj.optInt("applyIntervalMinutes", 1),
                    autoApplyAfterEdit = obj.optBoolean("autoApplyAfterEdit", true),
                    checkUpdateOnLaunch = obj.optBoolean("checkUpdateOnLaunch", true),
                    language = obj.optString("language", ""),
                    forcedMode = obj.optString("forcedMode", "auto"),
                    rulesConfigsJson = obj.optJSONArray("rulesConfigs")?.toString() ?: "",
                    hideLauncherIcon = obj.optBoolean("hideLauncherIcon", false),
                )
            } catch (_: Exception) {
                AppSettings()
            }
        }

        private const val PREFS_NAME = "app_settings"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_BOTTOM_BAR_STYLE = "bottom_bar_style"
        private const val KEY_FLOATING_NAVBAR = "floating_navbar"
        private const val KEY_RAIL_STYLE = "rail_style"
        private const val KEY_LIQUID_GLASS = "liquid_glass"
        private const val KEY_BLUR_ENABLED = "blur_enabled"
        private const val KEY_APPLY_INTERVAL = "apply_interval"
        private const val KEY_AUTO_APPLY_AFTER_EDIT = "auto_apply_after_edit"
        private const val KEY_CHECK_UPDATE_ON_LAUNCH = "check_update_on_launch"
        private const val KEY_HIDE_LAUNCHER_ICON = "hide_launcher_icon"

        fun load(context: Context): AppSettings {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val language = LocaleHelper.getSavedLanguage(context).code
            val forcedMode = SystemVersionDetector.getForcedMode(context)
            val saved = prefs.all
            val floatingNavbar = saved[KEY_FLOATING_NAVBAR] as? Boolean
            return AppSettings(
                themeMode = prefs.getString(KEY_THEME_MODE, "System") ?: "System",
                bottomBarStyle = BottomBarStyle.fromSaved(saved[KEY_BOTTOM_BAR_STYLE], floatingNavbar, saved[KEY_LIQUID_GLASS] == true),
                railStyle = RailStyle.fromSaved(saved[KEY_RAIL_STYLE], floatingNavbar),
                isBlurEnabled = prefs.getBoolean(KEY_BLUR_ENABLED, true),
                applyIntervalMinutes = prefs.getInt(KEY_APPLY_INTERVAL, 1),
                autoApplyAfterEdit = prefs.getBoolean(KEY_AUTO_APPLY_AFTER_EDIT, true),
                checkUpdateOnLaunch = prefs.getBoolean(KEY_CHECK_UPDATE_ON_LAUNCH, true),
                language = language,
                forcedMode = forcedMode,
                hideLauncherIcon = prefs.getBoolean(KEY_HIDE_LAUNCHER_ICON, false),
            )
        }

        fun save(context: Context, settings: AppSettings) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
                putString(KEY_THEME_MODE, settings.themeMode)
                putInt(KEY_BOTTOM_BAR_STYLE, settings.bottomBarStyle.value)
                putInt(KEY_RAIL_STYLE, settings.railStyle.value)
                putBoolean(KEY_BLUR_ENABLED, settings.isBlurEnabled)
                putInt(KEY_APPLY_INTERVAL, settings.applyIntervalMinutes)
                putBoolean(KEY_AUTO_APPLY_AFTER_EDIT, settings.autoApplyAfterEdit)
                putBoolean(KEY_CHECK_UPDATE_ON_LAUNCH, settings.checkUpdateOnLaunch)
                putBoolean(KEY_HIDE_LAUNCHER_ICON, settings.hideLauncherIcon)
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
