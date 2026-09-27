package cn.ianzb.hypernavbar.hook.base

import cn.ianzb.hypernavbar.hook.device.DeviceContext
import cn.ianzb.hypernavbar.hook.device.DeviceType
import cn.ianzb.hypernavbar.hook.dexkit.DexKitCacheManager
import cn.ianzb.hypernavbar.hook.dexkit.IDexKit
import cn.ianzb.hypernavbar.hook.dexkit.IDexKitList
import cn.ianzb.hypernavbar.hook.rule.HookSkippedException
import cn.ianzb.hypernavbar.hook.rule.HookVariant
import cn.ianzb.hypernavbar.hook.rule.HookVersionGate
import cn.ianzb.hypernavbar.hook.rule.VersionContext
import cn.ianzb.hypernavbar.hook.xposed.HookHelper

/**
 * 单条 hook 规则基类。
 *
 * 子类实现 [init]，在其中使用 [HookHelper] 完成挂载。
 * 若需要 DexKit，覆写 [useDexKit] 并在 [initDexKit] 中解析成员。
 *
 * 版本筛选：
 * - [versionGate]：不满足时整条规则跳过（不安装、不记录失败）；
 * - [variants]：按顺序匹配第一个满足的版本分支，实现「按 HyperOS / Android / 应用版本应用不同 hook 代码」。
 */
abstract class BaseHook {

    /** 日志 TAG。 */
    open val tag: String get() = javaClass.simpleName

    /** 绑定的配置键；用于 hook 状态上报与 UI 展示。 */
    open val key: String get() = javaClass.simpleName

    /** 是否需要 DexKit 会话。 */
    open fun useDexKit(): Boolean = false

    /**
     * DexKit 初始化阶段：只在这里解析成员，不要在 hook 回调里首次触发 DexKit。
     * 返回 false 表示跳过该 hook。
     */
    open fun initDexKit(): Boolean = true

    /** 版本门禁；为空表示不限制。 */
    open val versionGate: HookVersionGate? get() = null

    /** 设备形态白名单（手机 / 平板 / 折叠屏）；为空表示各设备通用。 */
    open val deviceScope: Set<DeviceType>? get() = null

    /** 版本分支；为空表示直接执行 [init]。 */
    open val variants: List<HookVariant> get() = emptyList()

    /** 当前目标包信息，由 [BaseLoad] 在安装前注入，可在 [init] 中读取（如 `target.classLoader`）。 */
    protected lateinit var target: PackageTarget

    /** 执行挂载（未声明 [variants] 时使用）。 */
    open fun init() {}

    internal var dexKitInitInProgress = false

    /**
     * 执行设备 / 版本筛选并安装，返回命中的分支名（默认分支返回 null）。
     * 由 [BaseLoad] 调用，不应在子类中手动调用。
     */
    internal fun apply(target: PackageTarget): String? {
        this.target = target
        val device = DeviceContext.current
        deviceScope?.takeIf { it.isNotEmpty() }?.let {
            if (device.type !in it) {
                throw HookSkippedException("device scope not matched: $it | device=${device.type}")
            }
        }
        if (versionGate == null && variants.isEmpty()) {
            init()
            return null
        }
        val context = VersionContext.of(target.packageName, target.appVersionName, target.appVersionCode)
        versionGate?.let {
            if (!it.matches(context)) {
                throw HookSkippedException("version gate not matched: $it | $context")
            }
        }
        if (variants.isNotEmpty()) {
            val variant = variants.firstOrNull { it.matches(context, device.type) }
                ?: throw HookSkippedException("no variant matched: $context | device=${device.type}")
            variant.body()
            return variant.name
        }
        init()
        return null
    }

    // ---------------- DexKit 辅助 ----------------

    @Suppress("unused")
    protected fun <T> requiredMember(memberKey: String, finder: IDexKit): T {
        val value = optionalMemberOrNull<T>(memberKey, finder)
            ?: throw IllegalStateException("$tag: required DexKit member not found: $memberKey")
        return value
    }

    @Suppress("unused")
    protected fun <T> requiredMemberList(memberKey: String, finder: IDexKitList): List<T> {
        val value = optionalMemberListOrNull<T>(memberKey, finder)
        if (value.isNullOrEmpty()) {
            throw IllegalStateException("$tag: required DexKit member list not found: $memberKey")
        }
        return value
    }

    @Suppress("unused")
    protected fun <T> optionalMember(memberKey: String, finder: IDexKit): T? =
        runCatching { optionalMemberOrNull<T>(memberKey, finder) }
            .onFailure { HookHelper.log("$tag: optional DexKit member failed: $memberKey", it) }
            .getOrNull()

    @Suppress("unused")
    protected fun <T> optionalMemberList(memberKey: String, finder: IDexKitList): List<T> =
        runCatching { optionalMemberListOrNull<T>(memberKey, finder) }
            .onFailure { HookHelper.log("$tag: optional DexKit member list failed: $memberKey", it) }
            .getOrNull()
            .orEmpty()

    @Suppress("UNCHECKED_CAST")
    private fun <T> optionalMemberOrNull(memberKey: String, finder: IDexKit): T? =
        DexKitCacheManager.findMember<T>(namespaced(memberKey), finder)

    @Suppress("UNCHECKED_CAST")
    private fun <T> optionalMemberListOrNull(memberKey: String, finder: IDexKitList): List<T>? =
        DexKitCacheManager.findMemberList<T>(namespaced(memberKey), finder)

    private fun namespaced(memberKey: String): String = "$tag#$memberKey"
}
