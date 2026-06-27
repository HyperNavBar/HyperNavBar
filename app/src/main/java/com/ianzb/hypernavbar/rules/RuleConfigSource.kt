package com.ianzb.hypernavbar.rules

import org.json.JSONObject

enum class RuleType { CLOUD, LOCAL }

data class RuleConfigSource(
    val id: String,
    val type: RuleType = RuleType.CLOUD,
    val url: String = "",
    val jsonContent: String = "",
    val cachedContent: String = "",
    val name: String = "",
    val note: String = "",
    val priority: Int = 0,
    val lastRefreshTime: Long = 0L,
    val refreshIntervalMs: Long = 0L,
    val appCount: Int = 0,
    val enabled: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type.name)
        put("url", url)
        put("jsonContent", jsonContent)
        put("cachedContent", cachedContent)
        put("name", name)
        put("note", note)
        put("priority", priority)
        put("lastRefreshTime", lastRefreshTime)
        put("refreshIntervalMs", refreshIntervalMs)
        put("appCount", appCount)
        put("enabled", enabled)
    }

    companion object {
        fun fromJson(obj: JSONObject): RuleConfigSource = RuleConfigSource(
            id = obj.optString("id", ""),
            type = try { RuleType.valueOf(obj.optString("type", "CLOUD")) } catch (_: Exception) { RuleType.CLOUD },
            url = obj.optString("url", ""),
            jsonContent = obj.optString("jsonContent", ""),
            cachedContent = obj.optString("cachedContent", ""),
            name = obj.optString("name", ""),
            note = obj.optString("note", ""),
            priority = obj.optInt("priority", 0),
            lastRefreshTime = obj.optLong("lastRefreshTime", 0L),
            refreshIntervalMs = obj.optLong("refreshIntervalMs", 0L),
            appCount = obj.optInt("appCount", 0),
            enabled = obj.optBoolean("enabled", true),
        )
    }
}
