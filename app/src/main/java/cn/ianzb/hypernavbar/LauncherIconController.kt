package cn.ianzb.hypernavbar

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * 控制桌面图标的显示与隐藏。
 *
 * 桌面图标由 manifest 中的 [LAUNCHER_ALIAS_CLASS] 别名提供，
 * MainActivity 自身保留 Xposed 模块设置入口，因此隐藏图标后仍可从模块管理器进入应用。
 */
object LauncherIconController {

    private const val LAUNCHER_ALIAS_CLASS = "cn.ianzb.hypernavbar.LauncherAlias"

    fun apply(context: Context, hide: Boolean) {
        val component = ComponentName(context.packageName, LAUNCHER_ALIAS_CLASS)
        val state = if (hide) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        runCatching {
            context.packageManager.setComponentEnabledSetting(
                component,
                state,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
