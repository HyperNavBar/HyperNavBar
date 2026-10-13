package cn.ianzb.hypernavbar.hook.rule

/**
 * 全局统一的系统版本判定（App 与 Hook 共用同一套规则）。
 *
 * 版本号只按「**主版本 + 第三段百位**」判定，**第二段恒为 0，不参与判定**：
 *
 * | 版本号形态 | 判定结果 |
 * |---|---|
 * | `4.0.X`（X 暂不限） | [OS40] |
 * | `3.0.3xx`（第三段 ≥ 300） | [OS33] |
 * | `3.0.1xx` / `3.0.2xx`（第三段 < 300） | [OS30] |
 * | `2.0.2xx` | [OS22] |
 * | 其它 / 读不到 | [UNKNOWN] |
 *
 * 解析优先取 `ro.mi.os.version.incremental`（形如 `OS3.0.305.0.WMLCNXM`，含第三段），
 * 读不到时回退 `ro.mi.os.version.name`（形如 `OS3.3`、`OS4.0`）；名称本身没有第三段时，
 * 只有其第二段非 0（`OS3.3` 这类标注写法）才作为最后兜底，`OS3.0` 一律按第三段判定。
 */
enum class SystemVersion(val label: String) {
    OS22("2.2"),
    OS30("3.0"),
    OS33("3.3"),
    OS40("4.0"),
    UNKNOWN("");

    /** 沉浸支持版本：3.3 与 4.0（NBI 相关 Hook 的门禁）。 */
    val immersiveSupported: Boolean get() = this == OS33 || this == OS40

    /** 是否以「替换系统规则文件」方式挂载规则：4.0 走运行期注入，其余（2.2 / 3.0 / 3.3）写文件。 */
    val usesRuleFile: Boolean get() = this != OS40

    /** 是否 HyperOS 4.x。 */
    val isOs4: Boolean get() = this == OS40

    companion object {

        /** 第三段 ≥ 该值即 3.3（3xx 系列）。 */
        private const val PATCH_SERIES_33 = 300

        private const val HYPER_OS_NAME = "ro.mi.os.version.name"
        private const val HYPER_OS_INCREMENTAL = "ro.mi.os.version.incremental"
        private const val SYSTEM_INCREMENTAL = "ro.system.build.version.incremental"

        /** 首页「沉浸模式」手动指定值 → 版本。 */
        private val OVERRIDES = mapOf(
            "2.2" to OS22,
            "3.0" to OS30,
            "3.3" to OS33,
            "4.0" to OS40,
        )

        /** 读取系统属性解析当前版本（Hook 进程与 App 进程通用）。 */
        fun current(): SystemVersion = parse(
            name = systemProp(HYPER_OS_NAME, HYPER_OS_INCREMENTAL),
            incremental = systemProp(HYPER_OS_INCREMENTAL, SYSTEM_INCREMENTAL),
        )

        /** 手动指定（auto / 4.0 / 3.3 / 3.0 / 2.2）优先，其次按系统属性解析。 */
        fun resolve(override: String?): SystemVersion =
            OVERRIDES[override?.trim()] ?: current()

        /** 解析：优先增量号（含第三段），其次版本名。 */
        fun parse(name: String, incremental: String): SystemVersion =
            fromRaw(incremental) ?: fromRaw(name) ?: UNKNOWN

        private fun fromRaw(raw: String): SystemVersion? {
            val segments = splitVersion(raw)
            val major = segments.getOrNull(0)?.toIntOrNull() ?: return null
            val patch = patchOf(segments.getOrNull(2))
            return when {
                major >= 4 -> OS40
                major == 3 -> when {
                    patch == null -> fromLabel(segments)
                    patch >= PATCH_SERIES_33 -> OS33
                    else -> OS30
                }
                major == 2 -> when {
                    patch == null -> fromLabel(segments)
                    patch in 200..299 -> OS22
                    else -> null
                }
                else -> null
            }
        }

        /**
         * 兜底：版本号里没有第三段时（如名称 `OS3.3`）按标注的第二段判定。
         *
         * 第二段为 0 的一律返回 null —— 依据用户口径「第二段恒为 0，不参与判定」。
         */
        private fun fromLabel(segments: List<String>): SystemVersion? =
            when (segments.getOrNull(1)?.toIntOrNull()) {
                2 -> OS22
                3 -> OS33
                4 -> OS40
                else -> null
            }

        /** 去掉 `OS` 前缀并按分隔符切段：`OS3.0.305.0.WMLCNXM` → `[3, 0, 305, 0, WMLCNXM]`。 */
        private fun splitVersion(raw: String): List<String> =
            stripOsPrefix(raw)
                .split('.', '-', '_', '+', '/', ':', ' ')
                .map { it.trim() }
                .filter { it.isNotEmpty() }

        private fun stripOsPrefix(raw: String): String {
            val trimmed = raw.trim()
            return if (trimmed.startsWith("OS", ignoreCase = true)) {
                trimmed.substring(2).trimStart('.', '-', '_', '+', '/', ':', ' ')
            } else {
                trimmed
            }
        }

        /**
         * 解析第三段代表的系列号：
         * - 纯数字（`305`）→ 原值；
         * - 「数字 + 非数字」形态（`3XX`，表示 3xx 系列）→ 数字部分 ×100，落在 300–399。
         */
        private fun patchOf(segment: String?): Int? {
            if (segment == null) return null
            val digits = segment.takeWhile { it.isDigit() }
            if (digits.isEmpty()) return null
            val value = digits.toIntOrNull() ?: return null
            return if (digits.length == segment.length) value else value * 100
        }

        private fun systemProp(vararg keys: String): String {
            for (key in keys) {
                val value = runCatching {
                    val clazz = Class.forName("android.os.SystemProperties")
                    val method = clazz.getDeclaredMethod("get", String::class.java)
                    method.invoke(null, key) as? String ?: ""
                }.getOrDefault("")
                if (value.isNotEmpty()) return value
            }
            return ""
        }
    }
}
