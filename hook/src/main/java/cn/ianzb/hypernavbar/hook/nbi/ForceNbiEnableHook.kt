package cn.ianzb.hypernavbar.hook.nbi

import android.content.Context
import cn.ianzb.hypernavbar.hook.base.BaseHook
import cn.ianzb.hypernavbar.hook.prefs.HookKeys
import cn.ianzb.hypernavbar.hook.rule.hookVariant
import cn.ianzb.hypernavbar.hook.xposed.HookHelper
import cn.ianzb.hypernavbar.hook.xposed.Reflect

/**
 * 强制生效（总开关）：确保目标进程内 NBI 已初始化并启用。
 *
 * 官方 `ActivityThreadImpl` 的 preInit 条件可能不满足（如系统属性 / 算力等级），
 * 导致 `MiuiNBIManagerImpl.init` 从未执行。这里在窗口附加时补一次初始化。
 */
class ForceNbiEnableHook : BaseHook() {

    override val tag = "ForceNbiEnable"

    override val key: String get() = HookKeys.MASTER

    override val versionGate = NbiHookSupport.NBI_GATE

    override val variants = listOf(
        hookVariant("os3", gate = { hyperOs { lt("4.0") } }) {
            hookAttach()
            forcePolicyGates()
        },
        hookVariant("os4", gate = { hyperOs { ge("4.0") } }) {
            hookAttach()
        },
    )

    private fun hookAttach() {
        val loader = target.classLoader
        val decorClass = Reflect.findClass("com.android.internal.policy.DecorView", loader)
        val immersive = Reflect.findClass("com.android.internal.policy.DecorViewImmersiveImpl", loader)
        val method = Reflect.findMethod(immersive, "onAttachedToWindow", decorClass, Context::class.java)
        HookHelper.hookBefore(method) { param ->
            NbiHookSupport.ensureInit(loader, param.args.getOrNull(1) as? Context)
        }
    }

    /**
     * HyperOS 3.3 的 `DecorViewImmersiveImpl.onAttachedToWindow` 会先判断
     * `Flags.navigationBarImmersivePolicy()` 与 `ComputilityLevel.getComputilityLevel() >= NORMAL`
     * 才继续，HyperOS 4 已移除这两项判断。这里在 os3 分支顺带绕过，
     * 避免低端机算力等级不足或 aconfig flag 关闭时整套沉浸被跳过；类 / 方法不存在时静默跳过。
     */
    private fun forcePolicyGates() {
        Reflect.findClassIfExists(
            "com.android.internal.hidden_from_bootclasspath.com.android.navimmersive.flags.Flags",
            target.classLoader,
        )?.let { flags ->
            Reflect.findMethodIfExists(flags, "navigationBarImmersivePolicy")?.let {
                HookHelper.hookReplace(it) { true }
            }
        }
        Reflect.findClassIfExists("miui.util.ComputilityLevel", target.classLoader)?.let { level ->
            Reflect.findMethodIfExists(level, "getComputilityLevel")?.let {
                HookHelper.hookReplace(it) { Int.MAX_VALUE }
            }
        }
    }
}
