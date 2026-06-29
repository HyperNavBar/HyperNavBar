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
                val parts = incremental.split(".")
                val patch = parts.getOrElse(2) { "0" }.toIntOrNull() ?: 0
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

    fun convert(mergedJson: JSONObject, mode: OsMode): String {
        return when (mode) {
            OsMode.OS33 -> convertToOS33(mergedJson)
            OsMode.OS30 -> convertToOS30(mergedJson)
            OsMode.OS22 -> convertToOS22(mergedJson)
            OsMode.UNKNOWN -> convertToOS33(mergedJson)
        }
    }

    private fun convertToOS33(json: JSONObject): String {
        val result = JSONObject()
        result.put("dataVersion", json.optString("dataVersion", "999999"))
        result.put("name", json.optString("name", "沉浸规则"))
        result.put("modules", json.optString("modules", "navigation_bar_immersive_application_config_new"))
        result.put("modifyApps", json.optString("modifyApps", "modifyApps"))

        val nbiRules = json.optJSONObject("NBIRules") ?: JSONObject()
        val converted = JSONObject()
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

            val activityRules = appRule.optJSONObject("activityRules") ?: JSONObject()
            val convertedActivities = JSONObject()
            val actKeys = activityRules.keys()
            while (actKeys.hasNext()) {
                val actName = actKeys.next()
                val rule = activityRules.getJSONObject(actName)
                val act = JSONObject()
                act.put("mode", rule.optInt("mode", -1))
                act.put("color", rule.opt("color"))
                act.put("sf_sampling_mode", rule.optInt("sf_sampling_mode", 0))
                act.put("dialogMode", rule.optInt("dialogMode", -1))
                act.put("popupMode", rule.optInt("popupMode", -1))
                act.put("appNavColorDisabled", rule.optInt("appNavColorDisabled", 0))
                convertedActivities.put(actName, act)
            }
            app.put("activityRules", convertedActivities)
            converted.put(pkg, app)
        }
        result.put("NBIRules", converted)
        return result.toString(2)
    }

    private fun convertToOS30(json: JSONObject): String {
        val result = JSONObject()
        result.put("dataVersion", json.optString("dataVersion", "999999"))
        result.put("name", json.optString("name", "沉浸规则"))
        result.put("modules", json.optString("modules", "navigation_bar_immersive_application_config_new"))
        result.put("modifyApps", json.optString("modifyApps", "modifyApps"))

        val nbiRules = json.optJSONObject("NBIRules") ?: JSONObject()
        val converted = JSONObject()
        val keys = nbiRules.keys()
        while (keys.hasNext()) {
            val pkg = keys.next()
            val appRule = nbiRules.getJSONObject(pkg)
            val app = JSONObject()
            app.put("name", appRule.optString("name", ""))
            app.put("enable", appRule.optBoolean("enable", true))

            val activityRules = appRule.optJSONObject("activityRules") ?: JSONObject()
            val convertedActivities = JSONObject()
            val actKeys = activityRules.keys()
            while (actKeys.hasNext()) {
                val actName = actKeys.next()
                val rule = activityRules.getJSONObject(actName)
                val act = JSONObject()
                act.put("mode", rule.optInt("mode", -1))
                act.put("color", rule.opt("color"))
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
                val mode = rule.optInt("mode", -1)
                val color = if (rule.has("color") && !rule.isNull("color") && rule.opt("color") != 1) {
                    ":${rule.opt("color")}"
                } else ""
                activityParts.add("$actName:$mode$color")
            }
            val activityRuleStr = activityParts.joinToString(",")

            sb.append("   <package name=\"$pkg\" enable=\"$enable\" activityRule=\"$activityRuleStr\" />\n")
        }

        sb.append("</NBIRules>")
        return sb.toString()
    }
}
