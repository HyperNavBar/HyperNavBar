package cn.ianzb.hypernavbar.ui.util

import android.app.Activity
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable

/**
 * 按应用内主题模式设置启动窗口背景，避免冷启动瞬间闪现浅色/深色底色。
 *
 * 颜色需与页面实际背景（Miuix 主题的 surface：浅色 `0xFFF7F7F7`、深色 `0xFF000000`）保持一致，
 * 因此手动强制深浅色时也能与 Compose 内容无缝衔接。
 */
fun Activity.applyWindowBackground(themeModeName: String) {
    val dark = when (themeModeName) {
        "Dark", "MonetDark" -> true
        "Light", "MonetLight" -> false
        else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }
    val color = if (dark) 0xFF000000.toInt() else 0xFFF7F7F7.toInt()
    window.setBackgroundDrawable(ColorDrawable(color))
}
