package cn.ianzb.hypernavbar.prefs

import cn.ianzb.hypernavbar.xposed.HookStatus

/**
 * 单个配置项的声明。所有组件都基于它接入统一配置系统。
 *
 * @param key 配置键（持久化时会自动加 `prefs_key_` 前缀）
 * @param type 组件类型
 * @param titleRes 标题字符串资源
 * @param summaryRes 副标题字符串资源（0 表示无）
 * @param defaultBoolean 布尔默认值
 * @param defaultInt 整数默认值
 * @param defaultFloat 浮点默认值
 * @param defaultString 文本/选项默认值
 * @param entryResIds 下拉/单选/选择器的选项文本资源
 * @param entryValues 与 [entryResIds] 对应的取值
 * @param targetPackages 依赖的目标包（用于作用域申请与状态判断）
 * @param dependsOn 依赖的配置键；为空表示无依赖
 * @param dependsOnValue 依赖键需要等于该布尔值时才启用
 * @param masterKey 滑块的主开关键（开关控制滑块是否生效/显示）
 * @param sliderMin 滑块最小值
 * @param sliderMax 滑块最大值
 * @param sliderStep 滑块步长
 * @param sliderDecimals 小数位数（0 表示整数）
 * @param sliderUnitRes 单位文本资源（0 表示无）
 * @param sliderValueLabelRes 数值类型说明资源（显示在滑动条上方数值左侧，0 表示无）
 * @param hookId 状态上报使用的 hook 标识（默认取 [key]）
 * @param demoStatus 仅用于示例 / 预览：强制指定状态（驱动标题染色），非空时覆盖真实状态
 */
data class OptionSpec(
    val key: String,
    val type: OptionType,
    val titleRes: Int,
    val summaryRes: Int = 0,
    val defaultBoolean: Boolean = false,
    val defaultInt: Int = 0,
    val defaultFloat: Float = 0f,
    val defaultString: String = "",
    val entryResIds: List<Int> = emptyList(),
    val entryValues: List<String> = emptyList(),
    val targetPackages: List<String> = emptyList(),
    val dependsOn: String? = null,
    val dependsOnValue: Boolean = true,
    val masterKey: String? = null,
    val sliderMin: Float = 0f,
    val sliderMax: Float = 100f,
    val sliderStep: Float = 1f,
    val sliderDecimals: Int = 0,
    val sliderUnitRes: Int = 0,
    val sliderValueLabelRes: Int = 0,
    val hookId: String? = null,
    val demoStatus: HookStatus? = null,
) {
    val statusId: String get() = hookId ?: key
}
