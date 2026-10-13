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
 * system_server 侧规则注入（仅 HyperOS 4.x）。
 *
 * 以官方 `getSystemNBIRules` 返回的规则为基底，用模块规则逐活动覆盖合并（模块优先），
 * 并强制 `enable=true`、清空 `versionCode`（跳过官方版本阈值）。
 *
 * HyperOS 3.3（3.0.3XX）自 3.1.0 起不再使用运行期注入：模块 App 在应用规则时
 * 以 Root 替换系统规则文件（保留 `.bak` 备份）来挂载规则，详见 [NbiHookSupport.OS4_GATE]。
 */
class NbiRuleInjectHook : BaseHook() {

    override val tag = "NbiRuleInject"

    override val key: String get() = HookKeys.INJECT_RULES

    override val versionGate = NbiHookSupport.OS4_GATE

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
        // 读取前刷新远程偏好：hook 进程内的载荷是「按键缓存」，不刷新会一直用旧规则
        // （表现为 App 里改了规则、重启目标应用也没用，必须重启 system_server 才生效）
        HookPrefs.refresh()
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
