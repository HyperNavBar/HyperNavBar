package com.ianzb.hypernavbar.ui.theme


import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.ianzb.hypernavbar.ui.util.isInDarkTheme
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController


@Composable
fun AppTheme(
    themeMode: ColorSchemeMode = ColorSchemeMode.System,
    content: @Composable () -> Unit
) {
    // 可用模式: System, Light, Dark, MonetSystem, MonetLight, MonetDark
    val controller = remember(themeMode) { ThemeController(themeMode) }
    MiuixTheme(
        controller = controller,
        content = {
            // enableEdgeToEdge() 的 SystemBarStyle.auto 只跟随系统深浅模式，
            // 应用内手动指定深色/浅色主题时状态栏图标颜色不会同步。
            // 这里按实际应用主题（surface 亮度）动态设置系统栏图标颜色。
            val isDark = isInDarkTheme()
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as? Activity)?.window ?: return@SideEffect
                    val insetsController = WindowCompat.getInsetsController(window, view)
                    insetsController.isAppearanceLightStatusBars = !isDark
                    insetsController.isAppearanceLightNavigationBars = !isDark
                }
            }
            content()
        }
    )
}