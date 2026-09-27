@file:Suppress("unused")

package cn.ianzb.hypernavbar.hook.nativehook

import cn.ianzb.hypernavbar.hook.xposed.HookHelper

/**
 * 原生 hook 库描述。
 *
 * 目标进程内由框架依据模块的 `META-INF/xposed/native_init.list` 把这里声明的 `.so`
 * 载入，并在库加载后调用其导出的 `native_init`（见 `docs/NATIVE_HOOK.md`）。
 *
 * @param libraryName 逻辑库名，`native_hook` 或 `libnative_hook.so` 两种写法均可（内部自动归一化）。
 * @param key 状态 / 配置键，与 `OptionSpec.key` 对应。
 * @param required 加载失败时是否判定该规则失败；为 false 时仅记录日志。
 * @param description 说明，仅用于日志。
 */
data class NativeLibrarySpec(
    val libraryName: String,
    val key: String = defaultKey(libraryName),
    val required: Boolean = true,
    val description: String = "",
) {
    /** `System.loadLibrary` 使用的逻辑名（去掉 `lib` 前缀与 `.so` 后缀）。 */
    val logicalName: String get() = normalize(libraryName)

    companion object {
        fun normalize(name: String): String = name.removePrefix("lib").removeSuffix(".so")

        fun defaultKey(name: String): String = "native_${normalize(name)}"
    }
}

/**
 * libxposed 原生 hook 的二次封装门面（与 [HookHelper] 对称）。
 *
 * 职责：
 * - 在受控时机用 `System.loadLibrary` 把原生库载入目标进程；
 * - 库名归一化、重复加载去重、异常兜底与日志；
 *
 * 用法（在 `BaseLoad.onPackageLoaded` 中一行即可，配置键与 UI 联动）：
 * ```
 * initNativeHook(MyNativeHook(), HookPrefs.getBoolean(MyNativeHook.KEY, false))
 * ```
 *
 * 说明：真正的 hook 由原生库导出的 `native_init` 完成，Kotlin 侧只负责“一键把库带进目标进程”。
 */
object NativeHookHelper {

    private val specs = LinkedHashMap<String, NativeLibrarySpec>()
    private val loaded = LinkedHashSet<String>()

    /** 加载原生库；已加载则直接返回成功（幂等）。 */
    @Synchronized
    fun load(spec: NativeLibrarySpec): Boolean {
        val name = spec.logicalName
        if (loaded.contains(name)) return true

        val ok = try {
            System.loadLibrary(name)
            true
        } catch (t: Throwable) {
            HookHelper.log("${spec.key}: native library load failed: ${spec.libraryName}", t)
            false
        }

        if (ok) {
            loaded.add(name)
            specs[name] = spec
        }
        if (ok) HookHelper.log("${spec.key}: native library loaded: ${spec.libraryName}")
        return ok
    }

    /** 便捷重载；`required` 仅影响日志与状态记录语义。 */
    @Synchronized
    fun load(
        libraryName: String,
        key: String = NativeLibrarySpec.defaultKey(libraryName),
        required: Boolean = true,
    ): Boolean = load(NativeLibrarySpec(libraryName, key, required))

    /** 已成功载入的原生库。 */
    @Synchronized
    fun loaded(): List<NativeLibrarySpec> = specs.values.toList()

    @Synchronized
    fun isLoaded(libraryName: String): Boolean = loaded.contains(NativeLibrarySpec.normalize(libraryName))

    @Synchronized
    fun size(): Int = loaded.size
}
