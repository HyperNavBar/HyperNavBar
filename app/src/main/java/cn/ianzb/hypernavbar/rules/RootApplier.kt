package cn.ianzb.hypernavbar.rules

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File

/**
 * HyperOS 3.3（3.0.3XX）「替换系统规则文件」方式的挂载实现（3.1.0 起重新启用）。
 *
 * 应用规则时以设备当前的官方规则文件为基底，把模块规则逐活动覆盖合并后写回
 * `/data/system/cloudFeature_navigation_bar_immersive_rules_list.json` 并触发系统重载；
 * 首次写入前把官方文件备份为 `.bak`（配合 `/data/system/HyperNavBarRules` 标记只在首次备份），
 * 还原时优先用 `.bak` 回滚。
 *
 * 官方基底优先取云控文件（可能比内置文件更新），不存在时回退到
 * `/system_ext/etc/nbi/navigation_bar_immersive_rules_list.json`。
 */
object RootApplier {

    private const val MARKER_PATH = "/data/system/HyperNavBarRules"
    private const val CLOUD_PATH = "/data/system/cloudFeature_navigation_bar_immersive_rules_list.json"
    private const val LOCAL_PATH = "/system_ext/etc/nbi/navigation_bar_immersive_rules_list.json"
    private const val BACKUP_PATH = "$CLOUD_PATH.bak"
    private const val OFFICIAL_MODULES = "navigation_bar_immersive_application_config_new"

    /** 读取当前官方规则文件内容（云控文件优先）；读取失败返回 null。 */
    suspend fun readOfficialBase(): String? = withContext(Dispatchers.IO) {
        val script = "if [ -f ${shellQuote(CLOUD_PATH)} ]; then cat ${shellQuote(CLOUD_PATH)}; " +
            "elif [ -f ${shellQuote(LOCAL_PATH)} ]; then cat ${shellQuote(LOCAL_PATH)}; fi"
        execRead(script)?.takeIf { it.isNotBlank() }
    }

    /** 以 Root 写入合并后的规则文件并触发系统重载。 */
    suspend fun applyRules(content: String, tempDir: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val tempFile = File(tempDir, "hypernavbar_rules.tmp")
            tempFile.writeText(content, Charsets.UTF_8)
            exec(
                listOf(
                    "if [ ! -f ${shellQuote(MARKER_PATH)} ]; then cp -f ${shellQuote(CLOUD_PATH)} ${shellQuote(BACKUP_PATH)} 2>/dev/null; touch ${shellQuote(MARKER_PATH)}; fi",
                    "cp -f ${shellQuote(tempFile.absolutePath)} ${shellQuote(CLOUD_PATH)}",
                    "chmod 600 ${shellQuote(CLOUD_PATH)}",
                    "chown system:system ${shellQuote(CLOUD_PATH)}",
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
    suspend fun restoreOfficial(): Boolean = withContext(Dispatchers.IO) {
        exec(
            listOf(
                "if [ -f ${shellQuote(MARKER_PATH)} ] || [ -f ${shellQuote(BACKUP_PATH)} ]; then",
                "  if [ -f ${shellQuote(BACKUP_PATH)} ]; then cp -f ${shellQuote(BACKUP_PATH)} ${shellQuote(CLOUD_PATH)} && rm -f ${shellQuote(BACKUP_PATH)}; else rm -f ${shellQuote(CLOUD_PATH)}; fi",
                "  rm -f ${shellQuote(MARKER_PATH)}",
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
     * - `dataVersion` 取「当天日期」与基底版本中的较大值，确保系统把写入的文件识别为最新配置。
     */
    internal fun buildFileContent(baseJson: String?, payloads: Map<String, String>): String {
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
}
