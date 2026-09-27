package cn.ianzb.hypernavbar.hook.nbi

import android.os.Bundle
import cn.ianzb.hypernavbar.hook.base.BaseHook
import cn.ianzb.hypernavbar.hook.prefs.HookKeys
import cn.ianzb.hypernavbar.hook.prefs.HookPrefs
import cn.ianzb.hypernavbar.hook.xposed.HookHelper
import cn.ianzb.hypernavbar.hook.xposed.HookParam
import cn.ianzb.hypernavbar.hook.xposed.Reflect
import org.json.JSONObject

/**
 * system_server 侧规则注入。
 *
 * 以官方 `getSystemNBIRules` 返回的规则为基底，用模块规则逐活动覆盖合并（模块优先），
 * 并强制 `enable=true`、清空 `versionCode`（跳过官方版本阈值）。
 */
class NbiRuleInjectHook : BaseHook() {

    override val tag = "NbiRuleInject"

    override val key: String get() = HookKeys.INJECT_RULES

    override fun init() {
        val clazz = Reflect.findClass("com.android.nbi.MiuiNBIController", target.classLoader)
        val method = Reflect.findMethod(clazz, "getSystemNBIRules", String::class.java)
        HookHelper.hookAfter(method) { param ->
            runCatching {
                inject(param)
            }.onFailure { HookHelper.log("inject rules failed", it) }
        }
    }

    private fun inject(param: HookParam) {
        val pkg = param.args.getOrNull(0) as? String ?: return
        val payload = HookPrefs.getString(HookKeys.payloadKey(pkg), null)
        if (payload.isNullOrBlank()) return

        val moduleRules = runCatching { JSONObject(payload) }.getOrNull() ?: return
        if (moduleRules.length() == 0) return

        val bundle = param.result as? Bundle ?: Bundle()
        val official = bundle.getString("activityRules").orEmpty()
        val merged = if (official.isBlank()) JSONObject() else runCatching { JSONObject(official) }.getOrElse { JSONObject() }
        moduleRules.keys().forEach { activity -> merged.put(activity, moduleRules.get(activity)) }

        bundle.putBoolean("enable", true)
        bundle.putString("versionCode", "")
        bundle.putString("activityRules", merged.toString())
        param.result = bundle
        HookHelper.log("inject rules for $pkg: ${moduleRules.length()} activity override(s)")
    }
}
