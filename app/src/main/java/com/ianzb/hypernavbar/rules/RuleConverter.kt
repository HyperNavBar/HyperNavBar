package com.ianzb.hypernavbar.rules

import org.json.JSONObject
import java.io.File

object RuleConverter {

    enum class OsMode {
        OS33,  // HyperOS 3.3+ (PATCH >= 300, full JSON)
        OS30,  // HyperOS 3.0 (reduced JSON)
        OS22,  // HyperOS 2.2 (XML)
        UNKNOWN
    }

    fun detectOsMode(): OsMode {
        val incremental = try {
            Runtime.getRuntime().exec(arrayOf("getprop", "ro.build.version.incremental"))
                .inputStream.bufferedReader().readLine() ?: ""
        } catch (_: Exception) { "" }

        val jsonFile = File("/system_ext/etc/nbi/navigation_bar_immersive_rules_list.json")
        val xmlFile = File("/system_ext/etc/nbi/navigation_bar_immersive_rules_list.xml")

        if (jsonFile.exists()) {
            try {
                val patch = incremental.split(".").getOrElse(2) { "0" }.toIntOrNull() ?: 0
                return if (patch >= 300) OsMode.OS33 else OsMode.OS30
            } catch (_: Exception) {
                return OsMode.OS30
            }
        } else if (xmlFile.exists()) {
            return OsMode.OS22
        }
        return OsMode.OS30
    }

    fun getTargetPath(mode: OsMode): String = when (mode) {
        OsMode.OS33, OsMode.OS30 -> "/data/system/cloudFeature_navigation_bar_immersive_rules_list.json"
        OsMode.OS22 -> "/data/system/cloudFeature_navigation_bar_immersive_rules_list.xml"
        OsMode.UNKNOWN -> "/data/system/cloudFeature_navigation_bar_immersive_rules_list.json"
    }

    /** 根级 modules == "HyperNavBar_config" 视为内部统一新格式，否则为官方格式。 */
    fun isNewFormat(rootJson: JSONObject): Boolean =
        rootJson.optString("modules", "") == "HyperNavBar_config"

    fun convert(mergedJson: JSONObject, mode: OsMode): String = when (mode) {
        OsMode.OS33 -> convertToOS33(mergedJson)
        OsMode.OS30 -> convertToOS30(mergedJson)
        OsMode.OS22 -> convertToOS22(mergedJson)
        OsMode.UNKNOWN -> convertToOS33(mergedJson)
    }

    /** 官方格式 → 内部统一新格式（根级 modules 写 "HyperNavBar_config"）。 */
    fun normalizeFromOfficial(json: JSONObject): JSONObject {
        val result = JSONObject()
        val rootKeys = json.keys()
        while (rootKeys.hasNext()) {
            val key = rootKeys.next()
            if (key != "modules" && key != "NBIRules") {
                result.put(key, json.get(key))
            }
        }
        result.put("modules", "HyperNavBar_config")

        val converted = JSONObject()
        val nbiRules = json.optJSONObject("NBIRules") ?: JSONObject()
        val keys = nbiRules.keys()
        while (keys.hasNext()) {
            val pkg = keys.next()
            val appRule = nbiRules.getJSONObject(pkg)
            val app = JSONObject()
            // 应用级字段（name/enable/enable31/disableVersionCode 等）原样保留
            val appKeys = appRule.keys()
            while (appKeys.hasNext()) {
                val field = appKeys.next()
                if (field != "activityRules") {
                    app.put(field, appRule.get(field))
                }
            }

            val convertedActivities = JSONObject()
            val activityRules = appRule.optJSONObject("activityRules") ?: JSONObject()
            val actKeys = activityRules.keys()
            while (actKeys.hasNext()) {
                val actName = actKeys.next()
                val rule = activityRules.getJSONObject(actName)
                convertedActivities.put(actName, normalizeActivity(rule))
            }
            app.put("activityRules", convertedActivities)
            converted.put(pkg, app)
        }
        result.put("NBIRules", converted)
        return result
    }

    /** 单个 activity 规则按优先级归一化为新格式 style。 */
    private fun normalizeActivity(rule: JSONObject): JSONObject {
        val act = JSONObject()
        val sf = rule.optInt("sf_sampling_mode", 0)
        when {
            sf == 1 -> act.put("style", "sf")
            sf == -1 || sf == 255 -> {
                // 保留 sf_sampling_mode 原值为高级字段，style 按 mode/color 判定
                act.put("sf_sampling_mode", sf)
                applyModeColorStyle(act, rule)
            }
            else -> applyModeColorStyle(act, rule)
        }
        // 高级字段原样保留；等于官方默认值的可省略
        if (rule.has("dialogMode") && rule.optInt("dialogMode", 1) != 1) {
            act.put("dialogMode", rule.get("dialogMode"))
        }
        if (rule.has("popupMode") && rule.optInt("popupMode", 1) != 1) {
            act.put("popupMode", rule.get("popupMode"))
        }
        if (rule.has("appNavColorDisabled") && rule.optInt("appNavColorDisabled", 0) != 0) {
            act.put("appNavColorDisabled", rule.get("appNavColorDisabled"))
        }
        if (rule.has("viewRules")) {
            act.put("viewRules", rule.get("viewRules"))
        }
        return act
    }

    /** 依据 mode/color 判定 style（sf_sampling_mode 非 1/-1/255 时使用）。 */
    private fun applyModeColorStyle(act: JSONObject, rule: JSONObject) {
        val mode = rule.optInt("mode", -1)
        val raw = rule.opt("color")
        val color = when {
            raw is Int -> raw
            raw is Long -> raw.toInt()
            else -> null
        }
        when {
            mode == 1 && color != null && color != 1 -> {
                act.put("style", "color")
                act.put("color", argbToHex(color))
            }
            mode == 1 -> act.put("style", "view")
            mode == 0 -> act.put("style", "disabled")
            mode == 2 -> act.put("style", "floating")
            else -> act.put("style", "default")
        }
    }

    private fun convertToOS33(json: JSONObject): String {
        val result = newRoot(json)
        val converted = JSONObject()
        val nbiRules = json.optJSONObject("NBIRules") ?: JSONObject()
        val keys = nbiRules.keys()
        while (keys.hasNext()) {
            val pkg = keys.next()
            val appRule = nbiRules.getJSONObject(pkg)
            val app = JSONObject()
            app.put("name", appRule.optString("name", ""))
            app.put("enable", appRule.optBoolean("enable", true))
            if (appRule.has("disableVersionCode")) {
                app.put("disableVersionCode", appRule.get("disableVersionCode"))
            }

            val convertedActivities = JSONObject()
            val activityRules = appRule.optJSONObject("activityRules") ?: JSONObject()
            val actKeys = activityRules.keys()
            while (actKeys.hasNext()) {
                val actName = actKeys.next()
                val rule = activityRules.getJSONObject(actName)
                val act = JSONObject()
                val expansion = expandStyle(rule)
                act.put("mode", expansion.mode)
                if (expansion.color != null) {
                    act.put("color", expansion.color)
                }
                act.put("sf_sampling_mode", expansion.sfSamplingMode)
                act.put("dialogMode", rule.optInt("dialogMode", 1))
                act.put("popupMode", rule.optInt("popupMode", 1))
                act.put("appNavColorDisabled", rule.optInt("appNavColorDisabled", 0))
                // sf_sampling_mode 高级字段仅在值为 -1 或 255 时存在并覆盖展开输出
                if (rule.has("sf_sampling_mode")) {
                    val advanced = rule.optInt("sf_sampling_mode", 0)
                    if (advanced == -1 || advanced == 255) {
                        act.put("sf_sampling_mode", advanced)
                    }
                }
                convertedActivities.put(actName, act)
            }
            app.put("activityRules", convertedActivities)
            converted.put(pkg, app)
        }
        result.put("NBIRules", converted)
        return result.toString(2)
    }

    private fun convertToOS30(json: JSONObject): String {
        val result = newRoot(json)
        val converted = JSONObject()
        val nbiRules = json.optJSONObject("NBIRules") ?: JSONObject()
        val keys = nbiRules.keys()
        while (keys.hasNext()) {
            val pkg = keys.next()
            val appRule = nbiRules.getJSONObject(pkg)
            val app = JSONObject()
            app.put("name", appRule.optString("name", ""))
            app.put("enable", appRule.optBoolean("enable", true))

            val convertedActivities = JSONObject()
            val activityRules = appRule.optJSONObject("activityRules") ?: JSONObject()
            val actKeys = activityRules.keys()
            while (actKeys.hasNext()) {
                val actName = actKeys.next()
                val rule = activityRules.getJSONObject(actName)
                val act = JSONObject()
                val expansion = expandStyle(rule)
                act.put("mode", expansion.mode)
                if (expansion.color != null) {
                    act.put("color", expansion.color)
                }
                if (rule.has("viewRules")) {
                    act.put("viewRules", rule.get("viewRules"))
                }
                convertedActivities.put(actName, act)
            }
            app.put("activityRules", convertedActivities)
            converted.put(pkg, app)
        }
        result.put("NBIRules", converted)
        return result.toString(2)
    }

    private fun newRoot(json: JSONObject): JSONObject = JSONObject().apply {
        put("dataVersion", json.optString("dataVersion", "999999"))
        put("name", json.optString("name", "沉浸规则"))
        // 系统端只识别官方模块名，强制写回
        put("modules", "navigation_bar_immersive_application_config_new")
        put("modifyApps", json.optString("modifyApps", "modifyApps"))
    }

    private fun convertToOS22(json: JSONObject): String {
        val nbiRules = json.optJSONObject("NBIRules") ?: JSONObject()
        val sb = StringBuilder()
        sb.append("<?xml version='1.0' encoding='utf-8' standalone='yes'?>\n")
        sb.append("<NBIRules>\n")

        val sortedKeys = nbiRules.keys().asSequence().sorted()
        for (pkg in sortedKeys) {
            val appRule = nbiRules.optJSONObject(pkg) ?: continue
            val enable = appRule.optBoolean("enable", true)
            val activityRules = appRule.optJSONObject("activityRules") ?: JSONObject()

            // Build activityRule string: "Activity1:mode:color,Activity2:mode"
            val activityParts = mutableListOf<String>()
            val actKeys = activityRules.keys().asSequence().sorted()
            for (actName in actKeys) {
                val rule = activityRules.optJSONObject(actName) ?: continue
                val expansion = expandStyle(rule)
                val mode = expansion.mode
                val color = if (expansion.color != null && expansion.color != 1) {
                    ":${expansion.color}"
                } else ""
                // 对活动名单独转义，避免后续整体转义造成双重转义
                activityParts.add("${escapeXml(actName)}:$mode$color")
            }
            val activityRuleStr = activityParts.joinToString(",")

            val name = appRule.optString("name", "")
            val nameAttr = if (name.isNotEmpty()) " name=\"${escapeXml(name)}\"" else ""
            sb.append("   <package name=\"${escapeXml(pkg)}\"$nameAttr enable=\"$enable\" activityRule=\"$activityRuleStr\" />\n")
        }

        sb.append("</NBIRules>")
        return sb.toString()
    }

    private data class StyleExpansion(val mode: Int, val color: Any?, val sfSamplingMode: Int)

    /** 新格式 style → 官方 mode/color/sf_sampling_mode 展开；未知/缺失 style 回退 disabled。 */
    private fun expandStyle(rule: JSONObject): StyleExpansion {
        val style = rule.optString("style", "disabled")
        return when (style) {
            "default" -> StyleExpansion(-1, null, 0)
            "view" -> StyleExpansion(1, 1, 0)
            "sf" -> StyleExpansion(1, null, 1)
            "color" -> StyleExpansion(1, hexToArgb(rule.optString("color", "#00000000")), 0)
            "floating" -> StyleExpansion(2, null, 0)
            else -> StyleExpansion(0, null, 0)
        }
    }

    /** hex 颜色字符串（RGBA 序 #RRGGBBAA）→ ARGB int；支持 #RRGGBBAA/#RRGGBB/无#号，6 位补 AA=FF。 */
    private fun hexToArgb(hex: String): Int {
        val cleaned = hex.removePrefix("#")
        val padded = if (cleaned.length == 6) cleaned + "FF" else cleaned
        val r = padded.substring(0, 2).toIntOrNull(16) ?: 0
        val g = padded.substring(2, 4).toIntOrNull(16) ?: 0
        val b = padded.substring(4, 6).toIntOrNull(16) ?: 0
        val a = if (padded.length >= 8) padded.substring(6, 8).toIntOrNull(16) ?: 0 else 0xFF
        // Kotlin Int 有符号，按位或直接得到正确的有符号 ARGB int
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** ARGB int → hex 颜色字符串（RGBA 序 "#RRGGBBAA"，对齐 utils.py 的 argb_int_to_rgba）。 */
    private fun argbToHex(argb: Int): String {
        val a = (argb ushr 24) and 0xFF
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return String.format("#%02X%02X%02X%02X", r, g, b, a)
    }

    private fun escapeXml(input: String): String = buildString {
        for (c in input) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(c)
            }
        }
    }
}