package cn.ianzb.hypernavbar.hook.nbi

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import cn.ianzb.hypernavbar.hook.base.BaseHook
import cn.ianzb.hypernavbar.hook.prefs.HookKeys
import cn.ianzb.hypernavbar.hook.prefs.HookPrefs
import cn.ianzb.hypernavbar.hook.xposed.HookHelper
import cn.ianzb.hypernavbar.hook.xposed.Reflect
import org.json.JSONObject

/**
 * 强制全屏语义：让非全屏窗口（分屏 / 悬浮 / 小窗）也进入沉浸决策并实时取色。
 *
 * 官方逻辑的限制：
 * - `startFindAndUpdateNavigationBarColor` 开头 `mWindowingMode != 1` 直接跳过；
 * - `handleSFColorCollected` 按 `mWindowingMode != 1` 丢弃实时颜色；
 * - 官方 SF 采样区域按「窗口从屏幕左上角开始」计算，非全屏下会采到窗口外的背景；
 * - `NavigationBarImmersiveController.onPreDraw` 仅在 `shouldNotifyDrawForImmersive` 时触发
 *   （事件驱动，并非每帧），位图取色因此只在焦点 / 布局 / 配置变化时刷新。
 *
 * 分屏与全屏的 windowingMode 同为 multi-window(6)，无法用模式区分，改用窗口配置的
 * `bounds` 与 `maxBounds` 比较来判定是否占满屏幕。
 *
 * 修复：
 * 1. 进入决策 / SF 回调前把 `mWindowingMode` 视为 1；
 * 2. 非全屏按**模块规则**分派：自定义色 / 悬浮（强制沉浸）/ 禁用维持原样，
 *    采样取色类（`style=sf` / `view` / 默认）与未配置的活动走窗口视图采样；
 * 3. 为非全屏窗口注册 `OnPreDrawListener`，采样取色每帧重新取色，使内容变化实时刷新。
 *
 * 说明：
 * - 判定规则直接读模块注入载荷（`rule_payload_<包名>`），不经过合并了官方规则的
 *   `findValueForActivity`，避免官方 / 云控规则（或 `*` 通配）把活动判成禁用 / 强制沉浸
 *   而导致采样取色失效；
 * - 官方 SF 采样对非全屏窗口不可用（采样点按整屏度量计算，会落到窗口外），
 *   因此非全屏下 `style=sf` 与视图采样一致，都用窗口视图采样取色；全屏保持官方 SF 采样。
 */
class ForceFullscreenHook : BaseHook() {

    override val tag = "ForceFullscreen"

    override val key: String get() = HookKeys.FORCE_FULLSCREEN

    override val versionGate = NbiHookSupport.NBI_GATE

    /** 各控制器最近一次解析的注入载荷，避免每帧重复解析 JSON。 */
    private val payloadCache = java.util.WeakHashMap<Any, Pair<String, JSONObject>>()

    override fun init() {
        val loader = target.classLoader
        val clazz = Reflect.findClass("com.android.internal.policy.NavigationBarImmersiveController", loader)

        HookHelper.hookBefore(
            Reflect.findMethod(clazz, "startFindAndUpdateNavigationBarColor", java.lang.Boolean.TYPE),
        ) { param ->
            forceFullscreen(param.thisObject)
        }

        HookHelper.hookBefore(
            Reflect.findMethod(clazz, "handleSFColorCollected", IntArray::class.java),
        ) { param ->
            forceFullscreen(param.thisObject)
        }

        HookHelper.hookReplace(
            Reflect.findMethod(clazz, "enableSFSampling"),
        ) { param ->
            runCatching { enableSampling(param.thisObject) }
            null
        }

        // 非全屏窗口：注册每帧监听，实时刷新位图取色。
        val decorClass = Reflect.findClass("com.android.internal.policy.DecorView", loader)
        val immersiveClass = Reflect.findClass("com.android.internal.policy.DecorViewImmersiveImpl", loader)
        HookHelper.hookAfter(
            Reflect.findMethod(immersiveClass, "onAttachedToWindow", decorClass, Context::class.java),
        ) { param ->
            registerFrameSampling(param.thisObject, param.args.getOrNull(0) as? View)
        }
    }

    private fun forceFullscreen(self: Any?) {
        self ?: return
        runCatching { Reflect.setObjectField(self, "mWindowingMode", 1) }
    }

    private fun enableSampling(self: Any?) {
        self ?: return
        if (!isFullscreen(self)) {
            // 分屏 / 小窗：按模块规则分派，配合每帧监听实时刷新
            applyNonFullscreen(self)
            return
        }
        // 全屏：保持官方 SF 采样
        runCatching { Reflect.setObjectField(self, "mSFSamplingEnabled", true) }
        runCatching { Reflect.callMethod(self, "registerSFSamplingListener") }
        val registered = runCatching { Reflect.getObjectField(self, "mSFSamplingRegistered") as? Boolean }
            .getOrNull() ?: false
        if (!registered) {
            runCatching { Reflect.callMethod(self, "updateNavigationBarColor", false, -1) }
        }
    }

    /**
     * 非全屏窗口按当前活动的模块规则分派：
     * `mode=2` 强制沉浸（悬浮）、`mode=0` 尊重禁用、`mode=1` 有自定义色用固定色，
     * 其余采样取色类（`style=sf` / `view` / 默认）与未配置的活动都用窗口视图采样。
     */
    private fun applyNonFullscreen(controller: Any) {
        val rule = moduleRule(controller)
        if (rule == null) {
            runCatching { Reflect.callMethod(controller, "updateNavigationBarColor", false, -1) }
            return
        }
        when (rule.mode) {
            MODE_E2E -> runCatching { Reflect.callMethod(controller, "setNavigationBarForceImmersive") }
            MODE_DISABLE -> Unit
            MODE_FILL -> {
                val color = rule.color
                if (color != null && color != COLOR_SAMPLING) {
                    runCatching { Reflect.callMethod(controller, "updateNavigationBarColor", true, color) }
                } else {
                    runCatching { Reflect.callMethod(controller, "updateNavigationBarColor", false, -1) }
                }
            }
            else -> runCatching { Reflect.callMethod(controller, "updateNavigationBarColor", false, -1) }
        }
    }

    /** 读取模块为当前活动注入的规则（直接来自载荷，不受官方规则影响）；无则返回 null。 */
    private fun moduleRule(controller: Any): RuleDispatch? {
        val decorView = runCatching { Reflect.getObjectField(controller, "mDecorView") }.getOrNull() ?: return null
        val context = runCatching { Reflect.callMethod(decorView, "getContext") }.getOrNull() ?: return null
        val pkg = runCatching { Reflect.callMethod(context, "getPackageName") as? String }.getOrNull() ?: return null
        val activity = activityClassName(controller) ?: return null

        val raw = HookPrefs.getString(HookKeys.payloadKey(pkg), null)
        if (raw.isNullOrBlank()) return null
        val json = synchronized(payloadCache) {
            val cached = payloadCache[controller]
            if (cached != null && cached.first == raw) {
                cached.second
            } else {
                runCatching { JSONObject(raw) }.getOrNull()?.also { payloadCache[controller] = raw to it }
            }
        } ?: return null

        val rule = findActivityRule(json, activity) ?: return null
        val mode = rule.optInt("mode", MODE_DEFAULT)
        val color = if (rule.has("color") && !rule.isNull("color")) rule.optInt("color", COLOR_SAMPLING) else null
        return RuleDispatch(mode, color)
    }

    /** 按「精确活动名 → 通配 → `*`」匹配规则对象，与官方 `findValueForActivity` 语义一致。 */
    private fun findActivityRule(json: JSONObject, activity: String): JSONObject? {
        json.optJSONObject(activity)?.let { return it }
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key == "*") continue
            val regex = key.replace(".", "\\.").replace("*", ".*")
            if (runCatching { activity.matches(Regex(regex)) }.getOrDefault(false)) {
                json.optJSONObject(key)?.let { return it }
            }
        }
        return json.optJSONObject("*")
    }

    /** 从控制器取当前活动的类名；任一环节缺失返回 null。 */
    private fun activityClassName(controller: Any): String? {
        val decorView = runCatching { Reflect.getObjectField(controller, "mDecorView") }.getOrNull() ?: return null
        val activity = runCatching { Reflect.callMethod(decorView, "getAttachedActivity") }.getOrNull() ?: return null
        val component = runCatching { Reflect.callMethod(activity, "getComponentName") }.getOrNull() ?: return null
        return runCatching { Reflect.callMethod(component, "getClassName") as? String }.getOrNull()
    }

    private fun registerFrameSampling(stub: Any?, decorView: View?) {
        stub ?: return
        decorView ?: return
        runCatching {
            var last = 0L
            decorView.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    try {
                        val now = SystemClock.uptimeMillis()
                        if (now - last >= FRAME_SAMPLE_INTERVAL_MS) {
                            val controller = Reflect.getObjectField(stub, "mNavigationBarImmersiveController")
                            if (controller != null && !isFullscreen(controller)) {
                                last = now
                                applyNonFullscreen(controller)
                            }
                        }
                    } catch (_: Throwable) {
                        // 取色失败不影响应用绘制
                    }
                    return true
                }
            })
        }
    }

    /** 窗口是否占满屏幕：比较窗口配置 bounds 与 maxBounds。 */
    private fun isFullscreen(controller: Any): Boolean {
        val context = runCatching { Reflect.getObjectField(controller, "mContext") as? Context }.getOrNull()
            ?: return true
        val config = context.resources?.configuration ?: return true
        val windowConfig = runCatching { Reflect.getObjectField(config, "windowConfiguration") }.getOrNull()
            ?: return true
        val bounds = runCatching { Reflect.callMethod(windowConfig, "getBounds") as? Rect }.getOrNull()
            ?: return true
        val maxBounds = runCatching { Reflect.callMethod(windowConfig, "getMaxBounds") as? Rect }.getOrNull()
            ?: return true
        if (maxBounds.width() <= 0 || maxBounds.height() <= 0) return true
        return bounds.width() >= maxBounds.width() * 0.98f && bounds.height() >= maxBounds.height() * 0.98f
    }

    private data class RuleDispatch(val mode: Int, val color: Int?)

    private companion object {
        /** 非全屏窗口每帧取色的最小间隔（ms），避免每帧重绘带来的开销。 */
        const val FRAME_SAMPLE_INTERVAL_MS = 80L

        /** 活动主模式取值（与官方 field `mode` 一致）。 */
        const val MODE_DEFAULT = -1
        const val MODE_DISABLE = 0
        const val MODE_FILL = 1
        const val MODE_E2E = 2

        /** 视图采样在 color 上的魔法值，非真实颜色。 */
        const val COLOR_SAMPLING = 1
    }
}
