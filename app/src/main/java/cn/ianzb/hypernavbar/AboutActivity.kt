package cn.ianzb.hypernavbar

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cn.ianzb.hypernavbar.ui.screen.about.AboutPageContent
import cn.ianzb.hypernavbar.ui.theme.AppTheme
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

class AboutActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getSavedLanguage(newBase)
        super.attachBaseContext(LocaleHelper.wrapContext(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val savedSettings = AppSettings.load(this)
        val themeMode = try {
            ColorSchemeMode.valueOf(savedSettings.themeMode)
        } catch (_: Exception) {
            ColorSchemeMode.System
        }

        setContent {
            AppTheme(themeMode = themeMode) {
                AboutPageContent(
                    openLicensePage = {
                        startActivity(Intent(this, LicenseActivity::class.java))
                    },
                )
            }
        }
    }
}
