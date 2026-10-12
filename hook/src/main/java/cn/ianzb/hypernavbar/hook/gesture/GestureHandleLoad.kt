package cn.ianzb.hypernavbar.hook.gesture

import cn.ianzb.hypernavbar.hook.base.BaseLoad
import cn.ianzb.hypernavbar.hook.base.PackageTarget
import cn.ianzb.hypernavbar.hook.prefs.HookKeys
import cn.ianzb.hypernavbar.hook.prefs.HookPrefs

/**
 * SystemUI 侧 Load：小白条（手势提示线）显示控制。
 *
 * 仅在「手势提示线」总开关开启时安装；开启后需要重启系统界面（或重启设备）才能生效。
 */
class GestureHandleLoad : BaseLoad() {

    override val targetPackages: List<String> = listOf("com.android.systemui")

    override fun onPackageLoaded(target: PackageTarget) {
        initHook(GestureHandleHook(), HookPrefs.getBoolean(HookKeys.GESTURE_HANDLE, false))
    }
}
