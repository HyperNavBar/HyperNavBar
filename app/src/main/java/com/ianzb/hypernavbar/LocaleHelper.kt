package com.ianzb.hypernavbar

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {

    private const val PREFS_NAME = "app_prefs"
    private const val KEY_LANGUAGE = "language_code"

    enum class Language(val code: String) {
        SYSTEM(""),
        ZH_CN("zh-CN"),
        EN("en")
    }

    fun getSavedLanguage(context: Context): Language {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val code = prefs.getString(KEY_LANGUAGE, "") ?: ""
        return Language.entries.find { it.code == code } ?: Language.SYSTEM
    }

    fun setLanguage(context: Context, language: Language) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.code)
            .apply()
    }

    fun wrapContext(context: Context, language: Language): Context {
        if (language == Language.SYSTEM) return context
        val locale = Locale.forLanguageTag(language.code)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        return context.createConfigurationContext(config)
    }
}
