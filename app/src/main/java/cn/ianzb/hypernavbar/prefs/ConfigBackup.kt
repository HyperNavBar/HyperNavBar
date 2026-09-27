package cn.ianzb.hypernavbar.prefs

import org.json.JSONArray
import org.json.JSONObject

/**
 * 统一配置导出 / 导入（JSON）。
 */
object ConfigBackup {

    private const val SKIP_PREFIX = "runtime_"

    fun exportJson(): String {
        val json = JSONObject()
        PrefsStore.getAll().forEach { (key, value) ->
            if (key.startsWith(SKIP_PREFIX)) return@forEach
            when (value) {
                is Set<*> -> json.put(key, JSONArray(value))
                else -> json.put(key, value)
            }
        }
        return json.toString(2)
    }

    fun importJson(json: String): Boolean {
        return try {
            val obj = JSONObject(json)
            obj.keys().forEach { key ->
                val raw = obj.get(key)
                val value: Any? = when (raw) {
                    is JSONArray -> (0 until raw.length()).map { raw.getString(it) }.toSet()
                    else -> raw
                }
                PrefsStore.put(key, value)
            }
            ConfigState.reload()
            true
        } catch (_: Throwable) {
            false
        }
    }
}
