package cn.ianzb.hypernavbar.hook.nativehook

import cn.ianzb.hypernavbar.hook.device.DeviceContext
import cn.ianzb.hypernavbar.hook.device.DeviceType
import cn.ianzb.hypernavbar.hook.rule.HookVersionGate
import cn.ianzb.hypernavbar.hook.rule.VersionContext

/**
 * 单条原生 hook 规则基类，与 [cn.ianzb.hypernavbar.hook.base.BaseHook] 对称。
 *
 * 子类只需声明 [libraryName]；实际 hook 逻辑在原生库导出的 `native_init` 中完成，
 * 本类负责把它接入模板的“开关配置 + 状态上报 + 版本筛选”链路。
 *
 * ```kotlin
 * class HomeTweaksNative : BaseNativeHook() {
 *     override val libraryName = "hometweaks"
 *     override val key = "native_hometweaks"
 *     override val versionGate = hookVersionGate { app("com.miui.home") { ge("8.01.02.7709") } }
 * }
 * ```
 */
abstract class BaseNativeHook {

    /** 逻辑库名（`native_hook` 或 `libnative_hook.so` 均可）。 */
    abstract val libraryName: String

    /** 状态 / 配置键；默认 `native_<库名>`。 */
    open val key: String get() = NativeLibrarySpec.defaultKey(libraryName)

    /** 加载失败是否判定为规则失败；为 false 时仅记录日志。 */
    open val required: Boolean get() = true

    /** 说明，仅用于日志。 */
    open val description: String get() = ""

    /** 版本门禁；为空表示不限制（不满足时不会把库载入目标进程）。 */
    open val versionGate: HookVersionGate? get() = null

    /** 设备形态白名单（手机 / 平板 / 折叠屏）；为空表示各设备通用。 */
    open val deviceScope: Set<DeviceType>? get() = null

    /** 当前环境是否满足版本与设备筛选。 */
    fun appliesTo(
        context: VersionContext,
        device: DeviceType = DeviceContext.current.type,
    ): Boolean {
        val scope = deviceScope
        if (!scope.isNullOrEmpty() && device !in scope) return false
        return versionGate?.matches(context) ?: true
    }

    fun spec(): NativeLibrarySpec = NativeLibrarySpec(libraryName, key, required, description)

    /** 加载原生库；由 [cn.ianzb.hypernavbar.hook.base.BaseLoad] 调用。 */
    fun install(): Boolean = NativeHookHelper.load(spec())
}
