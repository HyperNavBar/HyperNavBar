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

    /** 小白条（手势提示线）总开关。 */
    const val GESTURE_HANDLE = "gesture_handle"

    /** 小白条滑动跟随动画：拖动时提示线跟随手指、松手回弹。 */
    const val GESTURE_MOTION = "gesture_handle_motion"

    /** 滑动跟随：松手后回弹延迟（毫秒）。 */
    const val GESTURE_MOTION_HOLD = "gesture_handle_motion_hold"

    /** 滑动跟随：跟随幅度（百分比 0–100）。 */
    const val GESTURE_MOTION_SCALE = "gesture_handle_motion_scale"

    /** 滑动跟随：单方向最大位移（dp）。 */
    const val GESTURE_MOTION_RANGE = "gesture_handle_motion_range"

    /** 滑动跟随：回弹速度倍率（百分比 50–200）。 */
    const val GESTURE_MOTION_RETURN = "gesture_handle_motion_return"

    /** 小白条触摸唤醒：触摸底部手势区域时临时显示。 */
    const val GESTURE_TOUCH_REVEAL = "gesture_handle_touch_reveal"

    /** 小白条全局默认适配模式（alwaysShow / autoHide / alwaysHide）。 */
    const val GESTURE_GLOBAL_MODE = "gesture_handle_global_mode"

    /** 是否优先跟随规则（follow / off，默认 follow）。 */
    const val GESTURE_FOLLOW_RULES = "gesture_handle_follow_rules"

    /** 应用级小白条状态规则（JSON：包名 → navBarVisibility 值）。 */
    const val GESTURE_VISIBILITY_RULES = "gesture_visibility_rules"

    fun payloadKey(pkg: String): String = RULE_PAYLOAD_PREFIX + pkg
}
