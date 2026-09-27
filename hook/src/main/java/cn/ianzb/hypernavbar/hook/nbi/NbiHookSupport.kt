package cn.ianzb.hypernavbar.hook.nbi

import android.content.Context
import cn.ianzb.hypernavbar.hook.xposed.Reflect

/** NBI 强制生效相关 hook 的共享工具。 */
internal object NbiHookSupport {

    /** 保证目标进程内 NBI 已初始化（官方 preInit 条件可能未满足）。 */
    fun ensureInit(loader: ClassLoader?, context: Context?) {
        if (context == null) return
        val stub = Reflect.findClassIfExists("com.miui.nbi.MiuiNBIManagerStub", loader) ?: return
        val instance = Reflect.callStaticMethod(stub, "getInstance") ?: return
        val enabled = runCatching { Reflect.callMethod(instance, "isNBIEnable", context) as? Boolean }
            .getOrNull() ?: false
        if (!enabled) {
            runCatching { Reflect.callMethod(instance, "init", context) }
        }
        runCatching { Reflect.setObjectField(instance, "mIsNBIEnable", true) }
    }

    /** 清除应用在 PhoneWindow 上自设的强制 edge-to-edge 标记。 */
    fun clearEdgeToEdge(decorView: Any?) {
        decorView ?: return
        val phoneWindow = Reflect.callMethod(decorView, "getPhoneWindow") ?: return
        runCatching { Reflect.setObjectField(phoneWindow, "mEdgeToEdgeEnforced", false) }
    }
}
