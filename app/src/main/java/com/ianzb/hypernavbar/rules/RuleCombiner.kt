package com.ianzb.hypernavbar.rules

import org.json.JSONObject

object RuleCombiner {

    fun combine(configs: List<RuleConfigSource>, fetchResults: Map<String, RuleFetcher.FetchResult>): JSONObject {
        val sortedConfigs = configs.sortedByDescending { it.priority }

        val mergedNBIRules = JSONObject()
        val mergedRoot = JSONObject()

        for (config in sortedConfigs) {
            val result = fetchResults[config.id] ?: continue
            val nbiRules = result.nbiRules

            val keys = nbiRules.keys()
            while (keys.hasNext()) {
                val pkg = keys.next()
                val appRule = nbiRules.getJSONObject(pkg)

                if (mergedNBIRules.has(pkg)) {
                    val existing = mergedNBIRules.getJSONObject(pkg)
                    mergeAppRule(existing, appRule)
                } else {
                    mergedNBIRules.put(pkg, JSONObject(appRule.toString()))
                }
            }
        }

        // 元数据 (name/dataVersion/modules/modifyApps) 取自优先级最高（列表最上方、合并中获胜）的订阅。
        // sortedConfigs 为 priority 降序，从后往前找第一个存在于 fetchResults 的即 priority 最小（列表顶部、合并获胜方）。
        val winningResult = sortedConfigs.asReversed().firstNotNullOfOrNull { config ->
            fetchResults[config.id]
        }
        val rootJson = JSONObject(winningResult?.rawJson ?: "{}")
        mergedRoot.put("dataVersion", rootJson.optString("dataVersion", "999999"))
        mergedRoot.put("name", rootJson.optString("name", "沉浸规则"))
        mergedRoot.put("modules", rootJson.optString("modules", "navigation_bar_immersive_application_config_new"))
        mergedRoot.put("modifyApps", rootJson.optString("modifyApps", "modifyApps"))

        val sortedNBIRules = JSONObject()
        val keys = sortedSetOf<String>().also { set ->
            val iter = mergedNBIRules.keys()
            while (iter.hasNext()) set.add(iter.next())
        }
        for (key in keys) {
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
            existing.put("activityRules", JSONObject(newActivities.toString()))
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
            val keys = result.nbiRules.keys()
            while (keys.hasNext()) {
                allPackages.add(keys.next())
            }
        }
        return allPackages.size
    }
}
