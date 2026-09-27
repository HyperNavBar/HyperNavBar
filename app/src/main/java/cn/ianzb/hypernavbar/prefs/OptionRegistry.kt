package cn.ianzb.hypernavbar.prefs

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf

/**
 * 全局配置项注册表。用于全局搜索、依赖解析与状态展示。
 */
object OptionRegistry {

    private val options = mutableStateMapOf<String, OptionSpec>()

    fun register(spec: OptionSpec) {
        options[spec.key] = spec
    }

    fun registerAll(specs: List<OptionSpec>) {
        specs.forEach(::register)
    }

    fun all(): List<OptionSpec> = options.values.toList()

    fun find(key: String): OptionSpec? = options[key]

    fun search(context: Context, query: String): List<OptionSpec> {
        val keyword = query.trim().lowercase()
        if (keyword.isEmpty()) return emptyList()
        return all().filter { spec ->
            val title = context.getString(spec.titleRes).lowercase()
            val summary = if (spec.summaryRes != 0) context.getString(spec.summaryRes).lowercase() else ""
            title.contains(keyword) || summary.contains(keyword)
        }
    }
}
