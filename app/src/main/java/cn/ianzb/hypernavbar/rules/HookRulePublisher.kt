package cn.ianzb.hypernavbar.rules

import cn.ianzb.hypernavbar.hook.prefs.HookKeys
import cn.ianzb.hypernavbar.prefs.PrefsStore
import org.json.JSONObject

/**
 * 把合并后的模块规则发布到跨进程配置（RemotePreferences），供 hook 进程读取。
 *
 * 每个包名一个载荷键（官方字段格式的 activityRules JSON），避免单个配置值过大。
 */
object HookRulePublisher {

    /** 发布模块规则，返回发布的包名列表（不含被排除的应用）。 */
    fun publish(merged: JSONObject): List<String> {
        val payloads = RuleConverter.buildHookPayloads(merged)
        val excluded = RuleConverter.excludedPackages(merged)

        // 清理已不存在于最新规则中的包（含旧版遗留载荷）
        val prefix = HookKeys.RULE_PAYLOAD_PREFIX
        PrefsStore.getAll().keys
            .filter { it.startsWith(prefix) }
            .forEach { storageKey ->
                val pkg = storageKey.removePrefix(prefix)
                if (!payloads.containsKey(pkg)) PrefsStore.remove(storageKey)
            }

        payloads.forEach { (pkg, json) -> PrefsStore.put(HookKeys.payloadKey(pkg), json) }
        PrefsStore.put(KEY_PAYLOAD_COUNT, payloads.size)
        PrefsStore.put(KEY_EXCLUDED, excluded)
        return payloads.keys.toList()
    }

    /** 清空全部模块规则载荷。 */
    fun clear() {
        val prefix = HookKeys.RULE_PAYLOAD_PREFIX
        PrefsStore.getAll().keys
            .filter { it.startsWith(prefix) }
            .forEach { PrefsStore.remove(it) }
        PrefsStore.put(KEY_PAYLOAD_COUNT, 0)
        PrefsStore.put(KEY_EXCLUDED, emptySet<String>())
    }

    fun count(): Int = PrefsStore.getInt(KEY_PAYLOAD_COUNT, 0)

    /** 当前被规则标记为排除 Hook 的应用集合。 */
    fun excludedPackages(): Set<String> = PrefsStore.getStringSet(KEY_EXCLUDED, emptySet())

    /** 当前已发布规则的包名列表。 */
    fun ruledPackages(): List<String> =
        PrefsStore.getAll().keys
            .filter { it.startsWith(HookKeys.RULE_PAYLOAD_PREFIX) }
            .map { it.removePrefix(HookKeys.RULE_PAYLOAD_PREFIX) }

    private const val KEY_PAYLOAD_COUNT = "rule_payload_count"
    private const val KEY_EXCLUDED = "rule_excluded_packages"
}
