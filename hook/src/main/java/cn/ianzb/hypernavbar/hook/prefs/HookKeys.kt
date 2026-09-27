package cn.ianzb.hypernavbar.hook.prefs

/**
 * Hook 侧配置键（需与 App 侧 `OptionRegistry` 中的 key 完全一致）。
 */
object HookKeys {

    /** 强制生效总开关。 */
    const val MASTER = "force_master"

    /** 模块规则覆盖合并官方 cloudFeature（默认开启）。 */
    const val INJECT_RULES = "force_inject_rules"

    /** 忽略官方 disableVersionCode 版本阈值。 */
    const val IGNORE_VERSION = "force_ignore_version"

    /** 绕过应用自设 edge-to-edge。 */
    const val BYPASS_E2E = "force_bypass_e2e"

    /** 绕过虚拟显示（投屏 / 车机）黑名单。 */
    const val BYPASS_VIRTUAL_DISPLAY = "force_bypass_virtual_display"

    /** 强制视为手势导航。 */
    const val FORCE_GESTURE = "force_gesture_nav"

    /** 强制全屏语义（绕过 windowingMode != 1）。 */
    const val FORCE_FULLSCREEN = "force_fullscreen"

    /** 忽略导航栏隐藏状态。 */
    const val FORCE_SHOW_NAV = "force_show_nav"

    /** 自动清理并还原旧版写盘残留（默认开启）。 */
    const val CLEANUP_LEGACY = "cleanup_legacy"

    /** 模块规则的每应用载荷键前缀（值为官方字段格式的 activityRules JSON）。 */
    const val RULE_PAYLOAD_PREFIX = "rule_payload_"

    fun payloadKey(pkg: String): String = RULE_PAYLOAD_PREFIX + pkg
}
