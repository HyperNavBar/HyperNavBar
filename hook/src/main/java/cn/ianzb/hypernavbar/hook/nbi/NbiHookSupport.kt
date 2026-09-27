package cn.ianzb.hypernavbar.hook.nbi

import android.content.Context
import cn.ianzb.hypernavbar.hook.rule.hookVersionGate
import cn.ianzb.hypernavbar.hook.xposed.Reflect

/** NBI 强制生效相关 hook 的共享工具。 */
internal object NbiHookSupport {

    /**
     * 所有 NBI hook 的共同版本门禁：仅在 HyperOS 4.0 及以上安装。
     *
     * NBI 相关类（`com.android.nbi.*`、`com.android.internal.policy.NavigationBarImmersiveController` /
     * `MiuiNBIManagerImpl` / `DecorViewImmersiveImpl`）为 HyperOS 4 专属，在 OS3 / OS2 / MIUI 上不存在。
     * 命中门禁时 [cn.ianzb.hypernavbar.hook.base.BaseHook.apply] 会抛出
     * [cn.ianzb.hypernavbar.hook.rule.HookSkippedException]，按「主动跳过」处理：
     * 不安装、不记录失败，避免在旧系统上产生无意义的 hook 失败日志。
     */
    val OS4_GATE = hookVersionGate { hyperOs { ge("4.0") } }

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
