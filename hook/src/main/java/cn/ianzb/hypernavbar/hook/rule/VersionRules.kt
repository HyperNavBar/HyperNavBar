package cn.ianzb.hypernavbar.hook.rule

import android.os.Build

/** 版本来源。 */
enum class VersionSource {
    /** Android SDK / Release。 */
    ANDROID,

    /** HyperOS（`ro.mi.os.version.name` / `ro.mi.os.version.incremental`）。 */
    HYPER_OS,

    /** MIUI（`ro.miui.ui.version.name`）。 */
    MIUI,

    /** 指定应用的版本名。 */
    APP,
}

/** 比较运算符。 */
enum class CompareOp { GT, GE, LT, LE, EQ, NE, RANGE }

/** 多重规则之间的组合方式。 */
enum class MatchMode {
    /** 全部满足（AND）。 */
    ALL,

    /** 任一满足（OR）。 */
    ANY,
}

/**
 * 单条版本约束。
 *
 * @param source 版本来源
 * @param op 比较运算符
 * @param value 目标值（[CompareOp.RANGE] 时为下界）
 * @param value2 [CompareOp.RANGE] 时的上界
 * @param packageName 仅 [VersionSource.APP] 有意义：限定应用包名（为空则不限定）
 */
data class VersionRule(
    val source: VersionSource,
    val op: CompareOp,
    val value: String,
    val value2: String? = null,
    val packageName: String? = null,
) {
    internal fun matches(context: VersionContext): Boolean {
        if (source == VersionSource.APP && packageName != null && context.packageName != packageName) {
            return false
        }
        val actual = Version.of(context.valueOf(source))
        val lower = Version.of(value)
        val upper = Version.of(value2 ?: value)
        return when (op) {
            CompareOp.GT -> actual > lower
            CompareOp.GE -> actual >= lower
            CompareOp.LT -> actual < lower
            CompareOp.LE -> actual <= lower
            CompareOp.EQ -> actual == lower
            CompareOp.NE -> actual != lower
            CompareOp.RANGE -> actual >= lower && actual <= upper
        }
    }

    override fun toString(): String = buildString {
        append(source.name.lowercase()).append(' ').append(op.name.lowercase()).append(' ').append(value)
        if (op == CompareOp.RANGE) append("..").append(value2)
        if (packageName != null) append(" @").append(packageName)
    }
}

/**
 * 当前运行环境的版本快照。Hook 进程内可直接读取系统属性与应用版本。
 */
data class VersionContext(
    val packageName: String = "",
    val androidSdk: Int = Build.VERSION.SDK_INT,
    val androidRelease: String = Build.VERSION.RELEASE ?: "",
    val hyperOs: String = systemProp("ro.mi.os.version.name", "ro.mi.os.version.incremental"),
    val hyperOsIncremental: String = systemProp(
        "ro.mi.os.version.incremental",
        "ro.system.build.version.incremental",
    ),
    val miui: String = systemProp("ro.miui.ui.version.name"),
    val appVersionName: String = "",
    val appVersionCode: Long = 0L,
) {
    /** 统一的系统版本判定（主版本 + 第三段百位；第二段不参与）。 */
    val systemVersion: SystemVersion get() = SystemVersion.parse(hyperOs, hyperOsIncremental)

    internal fun valueOf(source: VersionSource): String = when (source) {
        VersionSource.ANDROID -> androidSdk.toString()
        // 归一化为统一判定结果（4.0 / 3.3 / 3.0 / 2.2），既有 hyperOs { } 门禁可继续按数字比较
        VersionSource.HYPER_OS -> systemVersion.label
        VersionSource.MIUI -> miui
        VersionSource.APP -> appVersionName
    }

    override fun toString(): String =
        "android=$androidSdk/$androidRelease, hyperOs=$hyperOs, miui=$miui, app=$appVersionName($appVersionCode)"

    companion object {

        /** 采集目标应用所在的版本环境。 */
        fun of(packageName: String, appVersionName: String, appVersionCode: Long): VersionContext =
            VersionContext(
                packageName = packageName,
                appVersionName = appVersionName,
                appVersionCode = appVersionCode,
            )

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

/**
 * 版本门禁：一组 [VersionRule] 按 [mode] 组合。
 *
 * 支持两种写法：
 * - 规则式（`hyperOs { ge("3.3") }` 等）：由 [VersionRule] 描述；
 * - 统一判定式（推荐）：[of] 直接对 [SystemVersion] 结果做判断，避免各处重复实现版本逻辑。
 */
class HookVersionGate(
    val rules: List<VersionRule>,
    val mode: MatchMode = MatchMode.ALL,
    private val predicate: ((SystemVersion) -> Boolean)? = null,
) {
    fun matches(context: VersionContext): Boolean {
        val predicateMatched = predicate?.invoke(context.systemVersion)
        if (predicate != null && predicateMatched == false) return false
        val ruleMatched = when {
            rules.isEmpty() -> true
            mode == MatchMode.ALL -> rules.all { it.matches(context) }
            else -> rules.any { it.matches(context) }
        }
        return ruleMatched
    }

    override fun toString(): String =
        (if (predicate != null) "systemVersion(fn)" else "") +
            (if (rules.isNotEmpty() && predicate != null) " && " else "") +
            (if (rules.isEmpty()) "" else rules.joinToString(if (mode == MatchMode.ALL) " && " else " || "))

    companion object {
        /** 用统一版本判定构建门禁（Hook 侧推荐写法）。 */
        fun of(predicate: (SystemVersion) -> Boolean): HookVersionGate =
            HookVersionGate(emptyList(), MatchMode.ALL, predicate)
    }
}

/** 单个版本来源的规则构建作用域（`gt` / `ge` / `lt` / `le` / `eq` / `ne` / `range`）。 */
class VersionRuleScope internal constructor(
    private val source: VersionSource,
    private val packageName: String?,
    private val sink: (VersionRule) -> Unit,
) {
    fun gt(value: String) = emit(CompareOp.GT, value)
    fun ge(value: String) = emit(CompareOp.GE, value)
    fun lt(value: String) = emit(CompareOp.LT, value)
    fun le(value: String) = emit(CompareOp.LE, value)
    fun eq(value: String) = emit(CompareOp.EQ, value)
    fun ne(value: String) = emit(CompareOp.NE, value)
    fun range(min: String, max: String) = sink(VersionRule(source, CompareOp.RANGE, min, max, packageName))

    private fun emit(op: CompareOp, value: String) = sink(VersionRule(source, op, value, packageName = packageName))
}

/** 版本门禁构建器。 */
class HookVersionGateBuilder internal constructor(private val mode: MatchMode) {

    private val rules = mutableListOf<VersionRule>()

    private val predicates = mutableListOf<(SystemVersion) -> Boolean>()

    fun android(block: VersionRuleScope.() -> Unit) = scope(VersionSource.ANDROID, null, block)
    fun hyperOs(block: VersionRuleScope.() -> Unit) = scope(VersionSource.HYPER_OS, null, block)
    fun miui(block: VersionRuleScope.() -> Unit) = scope(VersionSource.MIUI, null, block)
    fun app(packageName: String? = null, block: VersionRuleScope.() -> Unit) = scope(VersionSource.APP, packageName, block)

    /** 统一版本判定：直接对 [SystemVersion] 结果判断（推荐，避免各处重复实现版本逻辑）。 */
    fun systemVersion(predicate: (SystemVersion) -> Boolean) {
        predicates += predicate
    }

    fun rule(rule: VersionRule) {
        rules += rule
    }

    internal fun build(): HookVersionGate = HookVersionGate(
        rules = rules.toList(),
        mode = mode,
        predicate = when (predicates.size) {
            0 -> null
            1 -> predicates.first()
            else -> { version -> if (mode == MatchMode.ALL) predicates.all { it(version) } else predicates.any { it(version) } }
        },
    )

    private fun scope(source: VersionSource, pkg: String?, block: VersionRuleScope.() -> Unit) {
        VersionRuleScope(source, pkg) { rules += it }.block()
    }
}

/**
 * 构建版本门禁的 DSL。
 *
 * ```kotlin
 * val gate = hookVersionGate {
 *     android { ge("35") }
 *     systemVersion { it.immersiveSupported }
 *     app("com.miui.home") { gt("8.01.02.7709") }
 * }
 * ```
 */
fun hookVersionGate(
    mode: MatchMode = MatchMode.ALL,
    block: HookVersionGateBuilder.() -> Unit,
): HookVersionGate = HookVersionGateBuilder(mode).apply(block).build()
