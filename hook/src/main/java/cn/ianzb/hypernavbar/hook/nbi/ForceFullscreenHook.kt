package cn.ianzb.hypernavbar.hook.nbi

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import cn.ianzb.hypernavbar.hook.base.BaseHook
import cn.ianzb.hypernavbar.hook.prefs.HookKeys
import cn.ianzb.hypernavbar.hook.xposed.HookHelper
import cn.ianzb.hypernavbar.hook.xposed.Reflect

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
 * 2. 全屏保持官方 SF 采样；非全屏改用窗口视图采样；
 * 3. 为非全屏窗口注册 `OnPreDrawListener`，每帧重新取色，使内容变化也能实时刷新。
 */
class ForceFullscreenHook : BaseHook() {

    override val tag = "ForceFullscreen"

    override val key: String get() = HookKeys.FORCE_FULLSCREEN

    override val versionGate = NbiHookSupport.OS4_GATE

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
            // 分屏 / 小窗：窗口视图采样（配合每帧监听实时刷新）
            runCatching { Reflect.callMethod(self, "updateNavigationBarColor", false, -1) }
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
                                Reflect.callMethod(controller, "updateNavigationBarColor", false, -1)
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

    private companion object {
        /** 非全屏窗口每帧取色的最小间隔（ms），避免每帧重绘带来的开销。 */
        const val FRAME_SAMPLE_INTERVAL_MS = 80L
    }
}
