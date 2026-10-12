package cn.ianzb.hypernavbar.hook.gesture

import org.json.JSONObject

/**
 * 小白条（手势提示线）的显示模式与解析。
 *
 * 规则值（应用级 `navBarVisibility`）：
 * - `default` — 跟随「全局默认适配模式」；
 * - `alwaysShow` — 始终显示；
 * - `autoHide` — 应用切到前台 3 秒后自动隐藏（触摸 / 滑动可唤醒）；
 * - `alwaysHide` — 始终隐藏。
 *
 * 内部运行模式（策略层使用）：`show` / `auto` / `hide`，`null` 表示保持系统原样。
 *
 * 「是否优先跟随规则」（[FOLLOW_*]）：
 * - [FOLLOW_RULES]（默认）— 先考虑规则：规则中的显式值优先；规则值为 `default` 或不在规则中的应用使用全局默认模式；
 * - [FOLLOW_OFF] — 忽略规则：所有应用统一使用全局默认模式。
 */
object GestureVisibility {

    const val DEFAULT = "default"
    const val ALWAYS_SHOW = "alwaysShow"
    const val AUTO_HIDE = "autoHide"
    const val ALWAYS_HIDE = "alwaysHide"

    const val FOLLOW_RULES = "follow"
    const val FOLLOW_OFF = "off"

    const val MODE_SHOW = "show"
    const val MODE_AUTO = "auto"
    const val MODE_HIDE = "hide"

    /** 规则值 → 内部模式；`default` / 未知值返回 null。 */
    fun toMode(value: String?): String? = when (value) {
        ALWAYS_SHOW -> MODE_SHOW
        AUTO_HIDE -> MODE_AUTO
        ALWAYS_HIDE -> MODE_HIDE
        else -> null
    }

    /** 解析单个应用应使用的模式；null 表示保持系统原样。 */
    fun resolve(pkg: String?, rules: Map<String, String>, follow: String, globalMode: String): String? {
        pkg ?: return null
        // 优先跟随规则：先取规则中的显式值，未覆盖时回落到全局默认模式
        if (follow != FOLLOW_OFF) {
            val rule = rules[pkg]
            if (rule != null && rule != DEFAULT) return toMode(rule)
        }
        return toMode(globalMode)
    }

    /** 解析 hook 载荷中的可见性规则 JSON（包名 → 规则值），忽略非法值。 */
    fun parseRules(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        val result = LinkedHashMap<String, String>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val pkg = keys.next()
            val value = root.optString(pkg, "")
            if (pkg.isNotEmpty() && (value == DEFAULT || toMode(value) != null)) {
                result[pkg] = value
            }
        }
        return result
    }
}
