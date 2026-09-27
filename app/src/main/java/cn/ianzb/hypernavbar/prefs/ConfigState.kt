package cn.ianzb.hypernavbar.prefs

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf

/**
 * 响应式配置状态。Compose 组件读取这里以获得自动重组。
 */
object ConfigState {

    private val values = mutableStateMapOf<String, Any?>()

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        PrefsStore.init(context)
        if (!initialized) reload()
    }

    /** 从磁盘重新加载全部配置（导入后调用）。 */
    fun reload() {
        values.clear()
        PrefsStore.getAll().forEach { (key, value) -> values[key] = value }
        initialized = true
    }

    fun get(key: String): Any? = values[key]

    fun bool(key: String, defaultValue: Boolean): Boolean =
        values[key] as? Boolean ?: defaultValue

    fun int(key: String, defaultValue: Int): Int =
        (values[key] as? Number)?.toInt() ?: defaultValue

    fun float(key: String, defaultValue: Float): Float =
        (values[key] as? Number)?.toFloat() ?: defaultValue

    fun string(key: String, defaultValue: String): String =
        values[key] as? String ?: defaultValue

    fun set(key: String, value: Any?) {
        values[key] = value
        PrefsStore.put(key, value)
    }
}
