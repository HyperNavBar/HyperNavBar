package cn.ianzb.hypernavbar.rules

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.core.content.edit
import cn.ianzb.hypernavbar.hook.rule.SystemVersion

/**
 * App 侧系统版本入口：统一走 [SystemVersion]（主版本 + 第三段百位判定），
 * 只额外负责「用户在首页手动指定」的持久化与展示。
 */
object SystemVersionDetector {

    private const val PREFS_NAME = "immersion_prefs"
    private const val KEY_MODE = "forced_mode"
    private const val MODE_AUTO = "auto"

    fun getMarketName(): String {
        return getProp("ro.product.marketname").ifEmpty { Build.MODEL }
    }

    @Suppress("unused")
    fun getHyperVersion(): String {
        return getProp("ro.mi.os.version.name")
    }

    fun getSystemVersionIncremental(): String {
        return getProp("ro.mi.os.version.incremental").ifEmpty { getProp("ro.system.build.version.incremental") }
    }

    /** 设备真实版本（不含手动指定）。 */
    fun detectOsMode(): SystemVersion = SystemVersion.current()

    fun getForcedMode(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MODE, MODE_AUTO) ?: MODE_AUTO
    }

    fun setForcedMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_MODE, mode)
        }
    }

    /** 有效版本：手动指定（auto / 4.0 / 3.3 / 3.0 / 2.2）优先，否则用真实版本。 */
    fun getEffectiveOsMode(context: Context): SystemVersion =
        SystemVersion.resolve(getForcedMode(context))

    @SuppressLint("PrivateApi")
    private fun getProp(property: String): String {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getDeclaredMethod("get", String::class.java)
            method.invoke(null, property) as? String ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    data class ImmersionStatus(val version: String, val isSupported: Boolean)

    fun getImmersionStatus(context: Context): ImmersionStatus {
        val mode = getEffectiveOsMode(context)
        return if (mode == SystemVersion.UNKNOWN) {
            ImmersionStatus("", false)
        } else {
            ImmersionStatus(mode.label, mode.immersiveSupported)
        }
    }
}
