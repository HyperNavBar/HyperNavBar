package com.ianzb.hypernavbar.rules

import org.json.JSONObject

object RuleCombiner {

    private const val DEFAULT_DATA_VERSION = "999999"
    private const val DEFAULT_NAME = "沉浸规则"
    private const val MODULES = "navigation_bar_immersive_application_config_new"
    private const val MODIFY_APPS = "modifyApps"

    fun combine(configs: List<RuleConfigSource>, fetchResults: Map<String, RuleFetcher.FetchResult>): JSONObject {
        // priority 降序: 下方订阅(priority 大)先处理作基底, 上方订阅(priority 小)后处理覆盖 → 上方获胜
        val sortedConfigs = configs.sortedByDescending { it.priority }

        val mergedNBIRules = JSONObject()

        for (config in sortedConfigs) {
            val nbiRules = fetchResults[config.id]?.nbiRules ?: continue
            val keys = nbiRules.keys()
            while (keys.hasNext()) {
                val pkg = keys.next()
                val appRule = nbiRules.getJSONObject(pkg)
                if (mergedNBIRules.has(pkg)) {
                    mergeAppRule(mergedNBIRules.getJSONObject(pkg), appRule)
                } else {
                    mergedNBIRules.put(pkg, JSONObject(appRule.toString()))
                }
            }
        }

        // 元数据取自优先级最高（列表最上方、合并中获胜）的订阅，与生效规则保持一致
        val winningResult = sortedConfigs.asReversed().firstNotNullOfOrNull { config ->
            fetchResults[config.id]
        }

        return buildRoot(winningResult?.rawJson, mergedNBIRules)
    }

    private fun buildRoot(rawJson: String?, mergedNBIRules: JSONObject): JSONObject {
        val rootJson = JSONObject(rawJson ?: "{}")
        val mergedRoot = JSONObject()
        mergedRoot.put("dataVersion", rootJson.optString("dataVersion", DEFAULT_DATA_VERSION))
        mergedRoot.put("name", rootJson.optString("name", DEFAULT_NAME))
        mergedRoot.put("modules", rootJson.optString("modules", MODULES))
        mergedRoot.put("modifyApps", rootJson.optString("modifyApps", MODIFY_APPS))

        val sortedNBIRules = JSONObject()
        for (key in sortedSetOf<String>().also { set ->
            val iter = mergedNBIRules.keys()
            while (iter.hasNext()) set.add(iter.next())
        }) {
            sortedNBIRules.put(key, mergedNBIRules.get(key))
        }
        mergedRoot.put("NBIRules", sortedNBIRules)
        return mergedRoot
    }

    private fun mergeAppRule(existing: JSONObject, newRule: JSONObject) {
        val existingActivities = existing.optJSONObject("activityRules")
        val newActivities = newRule.optJSONObject("activityRules")

        if (existingActivities != null && newActivities != null) {
            val newKeys = newActivities.keys()
            while (newKeys.hasNext()) {
                val activity = newKeys.next()
                existingActivities.put(activity, newActivities.get(activity))
            }
        } else if (newActivities != null) {
            existing.put("activityRules", newActivities)
        }

        if (newRule.has("enable")) {
            existing.put("enable", newRule.getBoolean("enable"))
        }
        if (newRule.has("name") && newRule.getString("name").isNotEmpty()) {
            existing.put("name", newRule.getString("name"))
        }
    }

    fun getTotalAppCount(fetchResults: Map<String, RuleFetcher.FetchResult>): Int {
        val allPackages = mutableSetOf<String>()
        for ((_, result) in fetchResults) {
            val iter = result.nbiRules.keys()
            while (iter.hasNext()) allPackages.add(iter.next())
        }
        return allPackages.size
    }
}
