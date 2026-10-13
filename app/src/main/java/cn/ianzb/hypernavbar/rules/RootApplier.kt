package cn.ianzb.hypernavbar.rules

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File

/**
 * 「替换系统规则文件」方式的挂载实现（HyperOS 4.0 以下：2.2 / 3.0 / 3.3）。
 *
 * 应用规则时以设备当前的官方规则文件为基底，把模块规则逐活动覆盖合并后写回
 * `/data/system/cloudFeature_navigation_bar_immersive_rules_list.(json|xml)` 并触发系统重载；
 * 首次写入前把官方文件备份为 `.bak`（配合 `/data/system/HyperNavBarRules` 标记只在首次备份），
 * 还原时优先用 `.bak` 回滚。
 *
 * 官方基底优先取云控文件（可能比内置文件更新），不存在时回退到 `/system_ext/etc/nbi/` 下的内置文件。
 *
 * 格式随版本：
 * - 2.2 → XML（`<package name="包名" enable="…" activityRule="活动:mode[:color],…" />`）；
 * - 3.0 / 3.3 → JSON（3.0 的解析器只读取它认识的 `mode` / `color` / `viewRules`，多余字段会被忽略，
 *   因此两者共用同一条写入路径与完整字段）。
 */
object RootApplier {

    private const val MARKER_PATH = "/data/system/HyperNavBarRules"
    private const val JSON_CLOUD = "/data/system/cloudFeature_navigation_bar_immersive_rules_list.json"
    private const val JSON_LOCAL = "/system_ext/etc/nbi/navigation_bar_immersive_rules_list.json"
    private const val XML_CLOUD = "/data/system/cloudFeature_navigation_bar_immersive_rules_list.xml"
    private const val XML_LOCAL = "/system_ext/etc/nbi/navigation_bar_immersive_rules_list.xml"
    private const val OFFICIAL_MODULES = "navigation_bar_immersive_application_config_new"

    /** 读取当前官方规则文件内容（云控文件优先，回退内置文件）；读取失败返回 null。 */
    suspend fun readOfficialBase(mode: RuleConverter.OsMode = RuleConverter.OsMode.OS33): String? =
        withContext(Dispatchers.IO) {
        val (cloud, local) = basePaths(mode)
        val script = "if [ -f ${shellQuote(cloud)} ]; then cat ${shellQuote(cloud)}; " +
            "elif [ -f ${shellQuote(local)} ]; then cat ${shellQuote(local)}; fi"
        execRead(script)?.takeIf { it.isNotBlank() }
    }

    /** 以 Root 写入合并后的规则文件并触发系统重载。 */
    suspend fun applyRules(
        content: String,
        targetPath: String,
        tempDir: File,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val tempFile = File(tempDir, "hypernavbar_rules.tmp")
            tempFile.writeText(content, Charsets.UTF_8)
            val target = shellQuote(targetPath)
            val backup = shellQuote("$targetPath.bak")
            val marker = shellQuote(MARKER_PATH)
            exec(
                listOf(
                    // 首次写入且官方文件存在时备份，之后不再覆盖已有备份
                    "if [ ! -f $marker ] && [ -f $target ] && [ ! -f $backup ]; then cp -f $target $backup; fi",
                    "touch $marker",
                    "cp -f ${shellQuote(tempFile.absolutePath)} $target",
                    "chmod 600 $target",
                    "chown system:system $target",
                    "rm -f ${shellQuote(tempFile.absolutePath)}",
                    "cmd miui_navigation_bar_immersive update",
                ),
            )
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 还原官方规则：有 `.bak` 时回滚，否则删除模块写入的文件；同时清理标记文件。
     *
     * 仅当模块写入过文件（存在标记或 `.bak`）时才执行，避免误删设备上的官方云控规则文件。
     */
    suspend fun restoreOfficial(
        targetPath: String = RuleConverter.getTargetPath(RuleConverter.OsMode.OS33),
    ): Boolean = withContext(Dispatchers.IO) {
        val target = shellQuote(targetPath)
        val backup = shellQuote("$targetPath.bak")
        val marker = shellQuote(MARKER_PATH)
        exec(
            listOf(
                "if [ -f $marker ] || [ -f $backup ]; then",
                "  if [ -f $backup ]; then cp -f $backup $target && rm -f $backup; else rm -f $target; fi",
                "  rm -f $marker",
                "  cmd miui_navigation_bar_immersive update",
                "fi",
            ),
        )
    }

    /**
     * 生成写入系统的规则文件内容：官方基底 + 模块规则覆盖合并。
     *
     * 与 4.x 运行期注入语义保持一致：
     * - 仅在模块规则涉及的应用上强制 `enable=true` 并移除 `disableVersionCode`（对应注入 hook 的处理）；
     * - 模块规则的活动覆盖同名官方活动，未涉及的应用 / 活动原样保留；
     * - JSON 格式的 `dataVersion` 取「当天日期」与基底版本中的较大值，确保系统把写入的文件识别为最新配置。
     */
    internal fun buildFileContent(
        base: String?,
        payloads: Map<String, String>,
        mode: RuleConverter.OsMode = RuleConverter.OsMode.OS33,
    ): String = if (mode == RuleConverter.OsMode.OS22) {
        buildXmlContent(base, payloads)
    } else {
        buildJsonContent(base, payloads)
    }

    /** 3.0 / 3.3：基底与输出都是官方字段 JSON，逐活动覆盖合并。 */
    private fun buildJsonContent(baseJson: String?, payloads: Map<String, String>): String {
        val root = baseJson?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        if (!root.has("modules")) root.put("modules", OFFICIAL_MODULES)
        if (!root.has("name")) root.put("name", "沉浸规则")
        root.put("dataVersion", fileDataVersion(root.optString("dataVersion", "")))

        val nbi = root.optJSONObject("NBIRules") ?: JSONObject().also { root.put("NBIRules", it) }
        for ((pkg, payloadJson) in payloads) {
            val activities = runCatching { JSONObject(payloadJson) }.getOrNull() ?: continue
            val app = nbi.optJSONObject(pkg) ?: JSONObject().also { nbi.put(pkg, it) }
            app.put("enable", true)
            app.remove("disableVersionCode")
            val merged = app.optJSONObject("activityRules") ?: JSONObject().also { app.put("activityRules", it) }
            val keys = activities.keys()
            while (keys.hasNext()) {
                val activity = keys.next()
                val rule = activities.optJSONObject(activity) ?: continue
                merged.put(activity, withoutNulls(rule))
            }
        }
        return root.toString(2)
    }

    /**
     * 2.2：基底与输出都是 XML。只重写模块规则涉及的 `<package>` 元素，其余内容（XML 声明、
     * `<NBIRules>` 根元素及其它应用）原样保留；基底里没有的应用追加到 `</NBIRules>` 之前。
     */
    private fun buildXmlContent(baseXml: String?, payloads: Map<String, String>): String {
        val base = baseXml.orEmpty()
        if (base.isBlank()) return base

        val overrides = payloads.mapValues { (_, json) ->
            runCatching { JSONObject(json) }.getOrNull() ?: JSONObject()
        }
        val handled = mutableSetOf<String>()

        var result = PACKAGE_TAG.replace(base) { match ->
            val tag = match.value
            val pkg = attribute(tag, "name") ?: return@replace tag
            val payload = overrides[pkg] ?: return@replace tag
            handled += pkg
            rewritePackage(tag, mergeActivityRule(attribute(tag, "activityRule"), payload))
        }

        val missing = overrides.keys - handled
        if (missing.isNotEmpty()) {
            val extra = buildString {
                missing.forEach { pkg ->
                    append("   <package name=\"").append(escapeXml(pkg))
                        .append("\" enable=\"true\" activityRule=\"")
                        .append(escapeXml(mergeActivityRule(null, overrides.getValue(pkg))))
                        .append("\" />\n")
                }
            }
            val close = result.lastIndexOf("</NBIRules>")
            result = if (close >= 0) {
                result.substring(0, close) + extra + result.substring(close)
            } else {
                result + extra
            }
        }
        return result
    }

    /** 合并某个应用的活动列表：基底活动原样保留，模块活动按同名覆盖（结果仍按基底顺序在前）。 */
    private fun mergeActivityRule(baseRule: String?, payload: JSONObject): String {
        val merged = LinkedHashMap<String, String>()
        baseRule?.split(',')?.forEach { part ->
            val trimmed = part.trim()
            if (trimmed.isEmpty()) return@forEach
            val pieces = trimmed.split(':')
            val name = pieces[0]
            if (name.isEmpty()) return@forEach
            val mode = pieces.getOrNull(1)?.trim().orEmpty()
            val color = pieces.getOrNull(2)?.trim()
            merged[name] = if (color.isNullOrEmpty()) mode else "$mode:$color"
        }

        val keys = payload.keys()
        while (keys.hasNext()) {
            val activity = keys.next()
            val rule = payload.optJSONObject(activity) ?: continue
            val mode = rule.optInt("mode", 0)
            val color = if (rule.has("color") && !rule.isNull("color")) rule.optInt("color", 1) else null
            // 与 2.2 语义一致：color=1 表示视图采样，不写具体颜色
            merged[activity] = if (color != null && color != 1) "$mode:$color" else mode.toString()
        }

        return merged.entries.joinToString(",") { (name, value) -> "$name:$value" }
    }

    /** 重写 `<package>` 属性：`enable` 强制 true，`activityRule` 换成合并结果，其它属性原样保留。 */
    private fun rewritePackage(tag: String, activityRule: String): String =
        setAttribute(setAttribute(tag, "enable", "true"), "activityRule", escapeXml(activityRule))

    private fun setAttribute(tag: String, name: String, value: String): String {
        val existing = Regex("""\b$name\s*=\s*"[^"]*"""").find(tag)
        if (existing != null) {
            return tag.substring(0, existing.range.first) + "$name=\"$value\"" +
                tag.substring(existing.range.last + 1)
        }
        val tail = Regex("""\s*/?>$""").find(tag) ?: return tag
        val closing = if (tail.value.trimStart().startsWith("/")) " />" else ">"
        return tag.substring(0, tail.range.first) + " $name=\"$value\"" + closing
    }

    private fun attribute(tag: String, name: String): String? =
        Regex("""\b$name\s*=\s*"([^"]*)"""")
            .find(tag)?.groupValues?.getOrNull(1)?.takeIf { it.isNotEmpty() }

    private fun escapeXml(value: String): String = buildString {
        value.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(char)
            }
        }
    }

    /** 规则文件路径（云控 / 内置）：2.2 是 XML，3.0 / 3.3 是 JSON。 */
    private fun basePaths(mode: RuleConverter.OsMode): Pair<String, String> =
        if (mode == RuleConverter.OsMode.OS22) XML_CLOUD to XML_LOCAL else JSON_CLOUD to JSON_LOCAL

    /** dataVersion 取当天日期（yyMMdd）与基底版本中的较大值，避免被系统判定为过期配置。 */
    private fun fileDataVersion(baseVersion: String): String {
        val today = RuleConverter.todayDataVersion()
        val todayValue = today.toLongOrNull() ?: 0L
        val baseValue = baseVersion.filter { it.isDigit() }.toLongOrNull() ?: 0L
        return if (baseValue > todayValue) baseVersion else today
    }

    /** 复制规则对象并移除 null 值（模块载荷里无自定义颜色时 color 为 null）。 */
    private fun withoutNulls(rule: JSONObject): JSONObject {
        val result = JSONObject()
        val keys = rule.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = rule.opt(key)
            if (value != null && value != JSONObject.NULL) result.put(key, value)
        }
        return result
    }

    private fun exec(lines: List<String>): Boolean = try {
        val process = Runtime.getRuntime().exec("su")
        val stream = DataOutputStream(process.outputStream)
        lines.forEach { stream.writeBytes("$it\n") }
        stream.writeBytes("exit\n")
        stream.flush()
        process.waitFor()
        val ok = process.exitValue() == 0
        process.destroy()
        ok
    } catch (_: Exception) {
        false
    }

    private fun execRead(script: String): String? = try {
        val process = Runtime.getRuntime().exec("su")
        val stream = DataOutputStream(process.outputStream)
        stream.writeBytes("$script\n")
        stream.writeBytes("exit\n")
        stream.flush()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        val ok = process.exitValue() == 0
        process.destroy()
        if (ok) output else null
    } catch (_: Exception) {
        null
    }

    private fun shellQuote(path: String): String = "'${path.replace("'", "'\\''")}'"

    private val PACKAGE_TAG = Regex("""<package\b[^>]*?/?>""")
}
