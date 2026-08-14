package com.ianzb.hypernavbar.rules

import android.content.Context
import org.json.JSONArray
import java.util.UUID

object RulesManager {

    private const val PREFS_NAME = "rules_configs"
    private const val STATE_PREFS_NAME = "rules_state"
    private const val KEY_CONFIGS = "rule_configs"
    private const val KEY_LAST_APPLY = "last_apply_time"
    private const val KEY_MERGED_COUNT = "applied_count"
    private const val KEY_IS_CUSTOM = "is_custom_applied"
    private const val KEY_DEFAULTS_SEEDED = "defaults_seeded"

    /**
     * 首次启动时播种默认订阅：
     * - 社区规则源在上（低优先级，合并时优先覆盖官方）
     * - 官方规则源在下（高优先级，作为基底）
     * 仅在订阅列表为空时执行一次，之后以标记位防止重复播种。
     */
    fun ensureDefaultConfigs(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DEFAULTS_SEEDED, false)) return

        if (loadAll(context).isEmpty()) {
            // 社区规则源（priority 0，显示在上方，合并时优先）
            add(context, RuleType.CLOUD, RuleConfigSource.PRESET_COMMUNITY_URL,
                name = context.getString(com.ianzb.hypernavbar.R.string.preset_community_name))
            // 官方规则源（priority 1，显示在下方，作为基底）
            add(context, RuleType.CLOUD, RuleConfigSource.PRESET_OFFICIAL_URL,
                name = context.getString(com.ianzb.hypernavbar.R.string.preset_official_name))
        }
        prefs.edit().putBoolean(KEY_DEFAULTS_SEEDED, true).apply()
    }

    fun saveApplyState(context: Context, time: Long, count: Int, isCustom: Boolean) {
        context.getSharedPreferences(STATE_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_APPLY, time)
            .putInt(KEY_MERGED_COUNT, count)
            .putBoolean(KEY_IS_CUSTOM, isCustom)
            .commit()
    }

    fun loadLastApplyTime(context: Context): Long =
        context.getSharedPreferences(STATE_PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_APPLY, 0L)

    fun loadAppliedCount(context: Context): Int =
        context.getSharedPreferences(STATE_PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_MERGED_COUNT, 0)

    fun loadIsCustomApplied(context: Context): Boolean =
        context.getSharedPreferences(STATE_PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_IS_CUSTOM, false)

    fun loadAll(context: Context): List<RuleConfigSource> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CONFIGS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i -> RuleConfigSource.fromJson(arr.getJSONObject(i)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveAll(context: Context, configs: List<RuleConfigSource>) {
        val arr = JSONArray()
        configs.forEach { arr.put(it.toJson()) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_CONFIGS, arr.toString()).commit()
    }

    fun add(context: Context, type: RuleType, url: String, jsonContent: String = "", name: String = "", appCount: Int = 0): RuleConfigSource {
        val configs = loadAll(context).toMutableList()
        val config = RuleConfigSource(
            id = UUID.randomUUID().toString(),
            type = type,
            url = url,
            jsonContent = jsonContent,
            name = name.ifEmpty { url },
            priority = (configs.maxOfOrNull { it.priority } ?: -1) + 1,
            appCount = appCount,
        )
        configs.add(config)
        saveAll(context, configs)
        return config
    }

    fun update(context: Context, config: RuleConfigSource) {
        updateById(context, config.id) { config }
    }

    fun remove(context: Context, id: String) {
        val configs = loadAll(context).toMutableList()
        configs.removeAll { it.id == id }
        saveAll(context, configs)
    }

    fun moveUp(context: Context, id: String) {
        val configs = loadAll(context).toMutableList()
        val idx = configs.indexOfFirst { it.id == id }
        if (idx > 0) {
            swapPriority(configs, idx, idx - 1)
            saveAll(context, configs)
        }
    }

    fun moveDown(context: Context, id: String) {
        val configs = loadAll(context).toMutableList()
        val idx = configs.indexOfFirst { it.id == id }
        if (idx in 0 until configs.lastIndex) {
            swapPriority(configs, idx, idx + 1)
            saveAll(context, configs)
        }
    }

    fun updateRefreshTime(context: Context, id: String, time: Long, appCount: Int = 0, name: String = "", cachedContent: String = "") {
        updateById(context, id) { old ->
            old.copy(
                lastRefreshTime = time,
                appCount = if (appCount > 0) appCount else old.appCount,
                name = name.ifEmpty { old.name },
                cachedContent = cachedContent.ifEmpty { old.cachedContent },
            )
        }
    }

    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_CONFIGS).apply()
    }

    fun exportJson(configs: List<RuleConfigSource>): JSONArray {
        val arr = JSONArray()
        configs.forEach { arr.put(it.toJson()) }
        return arr
    }

    fun importFromJson(context: Context, arr: JSONArray) {
        val configs = (0 until arr.length()).map { i ->
            RuleConfigSource.fromJson(arr.getJSONObject(i))
        }
        saveAll(context, configs)
    }

    private fun updateById(context: Context, id: String, transform: (RuleConfigSource) -> RuleConfigSource) {
        val configs = loadAll(context).toMutableList()
        val idx = configs.indexOfFirst { it.id == id }
        if (idx >= 0) {
            configs[idx] = transform(configs[idx])
            saveAll(context, configs)
        }
    }

    private fun swapPriority(configs: MutableList<RuleConfigSource>, i: Int, j: Int) {
        val temp = configs[i]
        configs[i] = configs[j]
        configs[j] = temp
        configs.forEachIndexed { idx, c -> configs[idx] = c.copy(priority = idx) }
    }
}
