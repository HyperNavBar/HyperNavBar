package com.ianzb.hypernavbar.rules

import org.json.JSONObject

object RuleCombiner {

    private const val DEFAULT_DATA_VERSION = "999999"
    private const val DEFAULT_NAME = "沉浸规则"
    private const val NEW_FORMAT_MODULES = "HyperNavBar_config"
    private const val MODIFY_APPS = "modifyApps"

    fun combine(configs: List<RuleConfigSource>, fetchResults: Map<String, RuleFetcher.FetchResult>): JSONObject {
        // priority 降序: 下方订阅(priority 大)先处理作基底, 上方订阅(priority 小)后处理覆盖 → 上方获胜
        val sortedConfigs = configs.sortedByDescending { it.priority }

        val mergedNBIRules = JSONObject()
        // 记录每个订阅源（可能归一化后）的完整 root，供 buildRoot 提取元数据
        val normalizedRawJson = mutableMapOf<String, String>()

        for (config in sortedConfigs) {
            val result = fetchResults[config.id] ?: continue
            // 旧格式订阅源（modules != HyperNavBar_config）在合并前归一化为内部新格式，
            // 其 NBIRules 内 activity 已是 style 形态；新格式源保持原样
            val root = JSONObject(result.rawJson)
            val normalizedRoot = if (RuleConverter.isNewFormat(root)) root else RuleConverter.normalizeFromOfficial(root)
            normalizedRawJson[config.id] = normalizedRoot.toString()

            val nbiRules = normalizedRoot.optJSONObject("NBIRules") ?: continue
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
        val winningRawJson = sortedConfigs.asReversed().firstNotNullOfOrNull { config ->
            normalizedRawJson[config.id]
        }

        return buildRoot(winningRawJson, mergedNBIRules)
    }

    private fun buildRoot(rawJson: String?, mergedNBIRules: JSONObject): JSONObject {
        val rootJson = JSONObject(rawJson ?: "{}")
        val mergedRoot = JSONObject()
        mergedRoot.put("dataVersion", rootJson.optString("dataVersion", DEFAULT_DATA_VERSION))
        mergedRoot.put("name", rootJson.optString("name", DEFAULT_NAME))
        // 内部统一新格式：根级 modules 固定写 HyperNavBar_config，与 RuleConverter.isNewFormat 判定一致
        mergedRoot.put("modules", NEW_FORMAT_MODULES)
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
