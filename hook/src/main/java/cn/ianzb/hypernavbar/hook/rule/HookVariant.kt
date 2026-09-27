package cn.ianzb.hypernavbar.hook.rule

import cn.ianzb.hypernavbar.hook.device.DeviceType

/**
 * 版本 / 设备分支：不同版本区间或设备形态可以应用不同的 hook 代码。
 *
 * ```kotlin
 * override val variants = listOf(
 *     // 仅平板：大文件夹占格缩小
 *     hookVariant("pad", setOf(DeviceType.PAD), gate = { app("com.miui.home") { ge("8.01.02.7709") } }) {
 *         // ...
 *     },
 *     // 其余设备
 *     hookVariant("default", { app("com.miui.home") { ge("8.01.02.7709") } }) {
 *         // ...
 *     },
 * )
 * ```
 *
 * 分支按声明顺序匹配第一个同时满足设备与版本的分支；若 `variants` 非空但全部不匹配，
 * 则抛出 [HookSkippedException]（不安装、不记录状态）。
 *
 * @param devices 设备形态白名单；null / 空表示各设备通用
 */
class HookVariant(
    val name: String,
    val gate: HookVersionGate,
    val body: () -> Unit,
    val devices: Set<DeviceType>? = null,
) {
    internal fun matches(context: VersionContext, device: DeviceType): Boolean =
        (devices.isNullOrEmpty() || device in devices) && gate.matches(context)

    override fun toString(): String = buildString {
        append(name).append('(').append(gate)
        if (!devices.isNullOrEmpty()) append(" | ").append(devices.joinToString("/") { it.name })
        append(')')
    }
}

/** 构建一个仅按版本匹配的版本分支。 */
@Suppress("unused")
fun hookVariant(
    name: String,
    mode: MatchMode = MatchMode.ALL,
    gate: HookVersionGateBuilder.() -> Unit,
    body: () -> Unit,
): HookVariant = HookVariant(name, hookVersionGate(mode, gate), body)

/** 构建一个同时按设备形态与版本匹配的分支。 */
@Suppress("unused")
fun hookVariant(
    name: String,
    devices: Set<DeviceType>?,
    mode: MatchMode = MatchMode.ALL,
    gate: HookVersionGateBuilder.() -> Unit = {},
    body: () -> Unit,
): HookVariant = HookVariant(name, hookVersionGate(mode, gate), body, devices)

/**
 * 版本 / 设备筛选未命中时抛出，用于把「主动跳过」与「安装失败」区分开：
 * 主动跳过不会写入失败状态（UI 显示为未应用）。
 */
class HookSkippedException(message: String) : RuntimeException(message)
