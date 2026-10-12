package cn.ianzb.hypernavbar.hook.gesture

import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewParent
import cn.ianzb.hypernavbar.hook.base.BaseHook
import cn.ianzb.hypernavbar.hook.prefs.HookKeys
import cn.ianzb.hypernavbar.hook.prefs.HookPrefs
import cn.ianzb.hypernavbar.hook.rule.hookVersionGate
import cn.ianzb.hypernavbar.hook.xposed.HookHelper
import cn.ianzb.hypernavbar.hook.xposed.Reflect
import io.github.libxposed.api.XposedInterface
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.roundToInt

/**
 * 小白条（手势提示线）显示控制，仅安装在 SystemUI 进程（参考 HyperModifier 的实现思路）。
 *
 * - 绘制拦截：挂 `NavigationHandle#onDraw`，按应用级策略用 `saveLayerAlpha` 控制透明度，
 *   并支持滑动跟随时整体平移画布；不修改 View alpha、触摸与 insets 逻辑；
 * - 前台识别：挂 `MiuiTopActivityObserver#updateTopActivity`，并结合 `ActivityManagerWrapper`
 *   快照兜底；
 * - 宿主保活：官方「隐藏手势提示线」开启时不创建提示线，按需在
 *   `NavigationBarControllerImpl#createNavigationBar` 期间临时放行、并在规则启用时重建宿主；
 * - 触摸 / 滑动：被动监听（`NavigationBarFrame#dispatchTouchEvent` + `InputMonitorCompat`），
 *   不消费输入事件。
 */
class GestureHandleHook : BaseHook() {

    override val tag = "GestureHandle"

    override val key: String get() = HookKeys.GESTURE_HANDLE

    override val versionGate = hookVersionGate { hyperOs { ge("3.0.3") } }

    // ---------------- 运行时状态 ----------------

    private var loader: ClassLoader? = null
    private var main: Handler? = null
    private var runtimeContext: Context? = null
    private var receiverInstalled = false

    private var inputMonitor: Any? = null
    private var inputReceiver: Any? = null
    private var inputMonitorInstalled = false

    private val handles: MutableMap<View, HandleVisual?> =
        Collections.synchronizedMap(WeakHashMap<View, HandleVisual?>())

    private val motion = GestureHandleMotion()
    private val policy = GestureHandlePolicy()

    private var controller = WeakReference<Any>(null)
    private var stockHidden = false
    private var rulesPresent = false

    private var activeTouchHandle: View? = null
    private var activeSwipeRevealed = false
    private var foregroundObserved = false

    private var cachedRulesRaw: String? = null
    private var cachedRules: Map<String, String> = emptyMap()
    private var lastConfig: Config? = null

    private val hideRunnable = Runnable { invalidateHandles() }
    private val detachedGestureTimeout = Runnable { finishActiveGesture(SystemClock.uptimeMillis()) }

    // ---------------- 安装 ----------------

    override fun init() {
        loader = target.classLoader
        installDrawHooks()
        installForegroundHook()
        installTouchObserver()
        installHostHooks()
        HookHelper.log("$tag: installed @ ${target.packageName}")
    }

    private fun installDrawHooks() {
        val handleClass = Reflect.findClassIfExists(HANDLE, loader)
        val drawMethod = handleClass?.let { findDrawMethod(it) }
        if (drawMethod == null) {
            HookHelper.log("$tag: NavigationHandle.onDraw not found")
            return
        }
        HookHelper.intercept(drawMethod) { chain -> draw(chain) }
        handleClass?.let { clazz ->
            Reflect.findMethodIfExists(clazz, "onAttachedToWindow")?.let { method ->
                HookHelper.hookAfter(method) { param ->
                    val view = param.thisObject as? View ?: return@hookAfter
                    if (enabled()) trackHandle(view)
                }
            }
            Reflect.findMethodIfExists(clazz, "onDetachedFromWindow")?.let { method ->
                HookHelper.hookAfter(method) { param ->
                    val view = param.thisObject as? View ?: return@hookAfter
                    handles.remove(view)
                    if (activeTouchHandle === view) {
                        main?.removeCallbacks(detachedGestureTimeout)
                        main?.postDelayed(detachedGestureTimeout, 750L)
                    }
                }
            }
        }
        // 横屏快捷切换使用独立的提示线 View
        Reflect.findClassIfExists(QUICKSWITCH_HANDLE, loader)?.let { rotated ->
            findDrawMethod(rotated)?.let { method ->
                HookHelper.intercept(method) { chain -> draw(chain) }
            }
        }
    }

    private fun installForegroundHook() {
        val observer = Reflect.findClassIfExists(TOP_OBSERVER, loader)
        if (observer == null) {
            HookHelper.log("$tag: MiuiTopActivityObserver not found")
            return
        }
        var hooked = 0
        for (method in observer.declaredMethods) {
            if (method.name != "updateTopActivity") continue
            val parameters = method.parameterTypes
            val taskArg = parameters.lastIndex
            if (taskArg < 0 || parameters[taskArg] != ActivityManager.RunningTaskInfo::class.java) continue
            method.isAccessible = true
            HookHelper.intercept(method) { chain ->
                val result = chain.proceed()
                val info = chain.args.getOrNull(taskArg) as? ActivityManager.RunningTaskInfo
                dispatch { foreground(info) }
                result
            }
            hooked++
        }
        if (hooked == 0) HookHelper.log("$tag: updateTopActivity(RunningTaskInfo) not found")
    }

    private fun installTouchObserver() {
        val frame = Reflect.findClassIfExists(NAV_BAR_FRAME, loader) ?: return
        val method = Reflect.findMethodIfExists(frame, "dispatchTouchEvent", MotionEvent::class.java) ?: return
        HookHelper.intercept(method) { chain ->
            runCatching {
                observeTouch(chain.thisObject as? View, chain.args.getOrNull(0) as? MotionEvent)
            }
            chain.proceed()
        }
    }

    private fun installHostHooks() {
        val controllerClass = Reflect.findClassIfExists(CONTROLLER, loader) ?: return
        for (method in controllerClass.declaredMethods) {
            when (method.name) {
                "createNavigationBar" -> {
                    method.isAccessible = true
                    HookHelper.intercept(method) { chain -> onCreatingNavigationBar(chain) }
                }

                "removeNavigationBar" -> {
                    method.isAccessible = true
                    HookHelper.intercept(method) { chain -> onRemovingNavigationBar(chain) }
                }
            }
        }
    }

    private fun findDrawMethod(clazz: Class<*>): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            val declared = runCatching { current.getDeclaredMethod("onDraw", Canvas::class.java) }.getOrNull()
            if (declared != null) {
                declared.isAccessible = true
                return declared
            }
            current = current.superclass
        }
        return null
    }

    // ---------------- 绘制拦截 ----------------

    private fun draw(chain: XposedInterface.Chain): Any? {
        val view = chain.thisObject as? View ?: return chain.proceed()
        if (!enabled() || view.display?.displayId != 0) return chain.proceed()
        return try {
            trackHandle(view)
            val now = SystemClock.uptimeMillis()
            val config = applyConfig(now)
            val systemHidden = rulesPresent && stockHidden
            val hidden = if (appliesTo(view)) policy.hidden(now, systemHidden) else systemHidden
            val visual = synchronized(handles) {
                val existing = handles[view]
                if (existing != null) existing else HandleVisual(hidden).also { handles[view] = it }
            }
            val alpha = visual.fade.alpha(hidden, now, config.motion && policy.swipeRevealActive(now))
            if (visual.fade.running(now) || motion.running(now)) view.postInvalidateOnAnimation()
            if (alpha <= 0f) return null
            val canvas = chain.args.getOrNull(0) as? Canvas ?: return chain.proceed()
            val save = if (alpha >= 1f) {
                canvas.save()
            } else {
                canvas.saveLayerAlpha(
                    0f, 0f, view.width.toFloat(), view.height.toFloat(), (alpha * 255f).roundToInt()
                )
            }
            try {
                if (config.motion && appliesTo(view)) canvas.translate(motion.x(now), motion.y(now))
                chain.proceed()
            } finally {
                canvas.restoreToCount(save)
            }
        } catch (t: Throwable) {
            HookHelper.log("$tag: draw hook failed", t)
            chain.proceed()
        }
    }

    // ---------------- 前台 / 配置 ----------------

    private fun foreground(info: ActivityManager.RunningTaskInfo?) {
        foregroundObserved = true
        val top = info?.topActivity
        val now = SystemClock.uptimeMillis()
        policy.foreground(top?.packageName, top?.flattenToString(), info?.taskId ?: -1, now)
        applyConfig(now)
        scheduleHide()
        invalidateHandles()
    }

    /** 读取配置并同步到策略；返回当前快照。 */
    private fun applyConfig(now: Long): Config {
        val config = readConfig()
        if (config != lastConfig) {
            lastConfig = config
            policy.configure(config.rules, config.follow, config.globalMode, now)
            motion.configure(
                holdMs = config.motionHoldMs.toLong(),
                followFactor = config.motionScale / 100f,
                returnScale = config.motionReturn / 100f,
            )
            rulesPresent = config.hasPolicy
            if (!config.touchReveal) policy.clearTouchReveal()
            if (!config.motion) {
                policy.clearSwipeReveal()
                motion.reset()
            }
            if (config.hasPolicy && !receiverInstalled) {
                controller.get()?.let { owner ->
                    (Reflect.getObjectField(owner, "mContext") as? Context)?.let { initialize(it) }
                }
            }
            if (!config.enabled) {
                stopInputMonitor()
            } else if (receiverInstalled) {
                installInputMonitor()
            }
            ensureHostAlive()
            scheduleHide()
            invalidateHandles()
        }
        return config
    }

    private fun readConfig(): Config {
        val enabled = enabled()
        val motionEnabled = HookPrefs.getBoolean(HookKeys.GESTURE_MOTION, true)
        val touchReveal = HookPrefs.getBoolean(HookKeys.GESTURE_TOUCH_REVEAL, true)
        val globalMode = HookPrefs.getString(HookKeys.GESTURE_GLOBAL_MODE, GestureVisibility.ALWAYS_SHOW)
            ?: GestureVisibility.ALWAYS_SHOW
        val follow = HookPrefs.getString(HookKeys.GESTURE_FOLLOW_RULES, GestureVisibility.FOLLOW_RULES)
            ?: GestureVisibility.FOLLOW_RULES
        val motionHoldMs = HookPrefs.getFloat(
            HookKeys.GESTURE_MOTION_HOLD, GestureHandleMotion.DEFAULT_HOLD_MS.toFloat()
        )
        val motionScale = HookPrefs.getFloat(
            HookKeys.GESTURE_MOTION_SCALE, GestureHandleMotion.DEFAULT_FOLLOW_SCALE * 100f
        )
        val motionRangeDp = HookPrefs.getFloat(HookKeys.GESTURE_MOTION_RANGE, GestureHandleMotion.DEFAULT_RANGE_DP)
        val motionReturn = HookPrefs.getFloat(
            HookKeys.GESTURE_MOTION_RETURN, GestureHandleMotion.DEFAULT_RETURN_SCALE * 100f
        )
        val raw = HookPrefs.getString(HookKeys.GESTURE_VISIBILITY_RULES, "")
        if (raw != cachedRulesRaw) {
            cachedRulesRaw = raw
            cachedRules = GestureVisibility.parseRules(raw)
        }
        return Config(
            enabled = enabled,
            motion = enabled && motionEnabled,
            touchReveal = enabled && touchReveal,
            globalMode = globalMode,
            follow = follow,
            rules = cachedRules,
            motionHoldMs = motionHoldMs,
            motionScale = motionScale,
            motionRangeDp = motionRangeDp,
            motionReturn = motionReturn,
            hasPolicy = enabled &&
                (follow != GestureVisibility.FOLLOW_RULES || cachedRules.isNotEmpty() || motionEnabled || touchReveal),
        )
    }

    private fun enabled(): Boolean = HookPrefs.getBoolean(HookKeys.GESTURE_HANDLE, false)

    // ---------------- 宿主保活 ----------------

    private fun onCreatingNavigationBar(chain: XposedInterface.Chain): Any? {
        val owner = chain.thisObject ?: return chain.proceed()
        controller = WeakReference(owner)
        val context = Reflect.getObjectField(owner, "mContext") as? Context
        if (context != null && enabled()) initialize(context)
        val injector = injectorOf(owner)
        stockHidden = booleanField(injector, "mHideGestureLine") ?: false
        val override = rulesPresent && booleanField(injector, "mIsFsgMode") == true && stockHidden
        if (override) setDisableGestureLineFlag(injector, false)
        return try {
            chain.proceed()
        } finally {
            if (override) {
                setDisableGestureLineFlag(injector, true)
                stockHidden = true
                invalidateHandles()
            }
        }
    }

    private fun onRemovingNavigationBar(chain: XposedInterface.Chain): Any? {
        val owner = chain.thisObject
        val injector = owner?.let { injectorOf(it) }
        stockHidden = booleanField(injector, "mHideGestureLine") ?: false
        val result = chain.proceed()
        // 允许官方正常销毁（主题 / 折叠切换），随后按需经官方创建路径重建宿主
        val displayId = (chain.args.getOrNull(0) as? Number)?.toInt() ?: -1
        if (displayId == 0 && rulesPresent && booleanField(injector, "mIsFsgMode") == true && stockHidden) {
            Handler(Looper.getMainLooper()).post { ensureHostAlive() }
        }
        return result
    }

    /** 官方「隐藏手势提示线」且规则需要宿主时，保证默认导航栏（提示线宿主）存在。 */
    private fun ensureHostAlive() {
        val owner = controller.get() ?: return
        val injector = injectorOf(owner)
        stockHidden = booleanField(injector, "mHideGestureLine") ?: false
        if (!stockHidden || booleanField(injector, "mIsFsgMode") != true) return
        runCatching {
            val view = defaultNavigationBar(owner)
            if (rulesPresent) {
                if (view == null) Reflect.callMethod(owner, "addDefaultNavigationBar")
            } else if (view != null) {
                Reflect.callMethod(owner, "removeNavigationBar", 0)
            }
        }.onFailure { HookHelper.log("$tag: host refresh failed", it) }
    }

    private fun defaultNavigationBar(owner: Any): Any? = runCatching {
        Reflect.callMethod(owner, "getDefaultNavigationBarView")
    }.getOrElse {
        runCatching { Reflect.callMethod(owner, "getDefaultNavigationBar") }.getOrNull()
    }

    private fun injectorOf(owner: Any): Any? = runCatching {
        Reflect.getObjectField(owner, "mNavigationModeControllerInjector")
    }.getOrNull()

    private fun booleanField(owner: Any?, name: String): Boolean? {
        if (owner == null) return null
        return runCatching { Reflect.getObjectField(owner, name) as? Boolean }.getOrNull()
    }

    /** `mHideGestureLine` 是公开字段（含继承查找），直接按 java 反射读写。 */
    private fun setDisableGestureLineFlag(owner: Any?, value: Boolean) {
        if (owner == null) return
        runCatching {
            val field: Field = publicField(owner.javaClass, "mHideGestureLine") ?: return
            field.setBoolean(owner, value)
        }
    }

    private fun publicField(clazz: Class<*>, name: String): Field? {
        var current: Class<*>? = clazz
        while (current != null) {
            current.fields.firstOrNull { it.name == name }?.let {
                it.isAccessible = true
                return it
            }
            current = current.superclass
        }
        return null
    }

    // ---------------- 触摸 / 滑动监听 ----------------

    private fun observeTouch(frame: View?, event: MotionEvent?) {
        event ?: return
        val config = lastConfig ?: return
        if (!config.enabled || (!config.touchReveal && !config.motion)) return
        if (frame != null && !appliesTo(frame)) return
        val now = SystemClock.uptimeMillis()
        main?.removeCallbacks(detachedGestureTimeout)
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            activeTouchHandle?.let { finishActiveGesture(now) }
            val handle = handleAt(frame, event.rawX, event.rawY)
            activeTouchHandle = handle
            activeSwipeRevealed = false
            if (handle == null || !appliesTo(handle)) return
            if (config.touchReveal) {
                policy.touchDown(now)
                scheduleHide()
            }
            if (config.motion) motion.start(event.rawX, event.rawY, now)
            invalidateHandles()
            return
        }
        val handle = activeTouchHandle ?: return
        val terminal = action == MotionEvent.ACTION_UP ||
            action == MotionEvent.ACTION_CANCEL ||
            action == MotionEvent.ACTION_POINTER_DOWN
        if (config.touchReveal) {
            if (terminal) {
                policy.touchUp(now)
                scheduleHide()
            } else {
                policy.touchEvent(now)
            }
        }
        if (action == MotionEvent.ACTION_MOVE && config.motion) {
            revealForSwipe(handle, event, now)
            if (activeSwipeRevealed) policy.swipeEvent(now)
            updateMotion(handle, event, config.motionRangeDp)
            invalidateHandles()
        } else if (terminal) {
            if (config.motion && action != MotionEvent.ACTION_POINTER_DOWN) {
                revealForSwipe(handle, event, now)
                updateMotion(handle, event, config.motionRangeDp)
            }
            finishActiveGesture(now)
        }
    }

    private fun finishActiveGesture(now: Long) {
        if (activeTouchHandle == null) return
        main?.removeCallbacks(detachedGestureTimeout)
        if (lastConfig?.touchReveal == true) policy.touchUp(now)
        if (activeSwipeRevealed) policy.swipeUp(now)
        motion.release(now)
        activeTouchHandle = null
        activeSwipeRevealed = false
        scheduleHide()
        invalidateHandles()
    }

    private fun revealForSwipe(handle: View, event: MotionEvent, now: Long) {
        if (activeSwipeRevealed) return
        val slop = ViewConfiguration.get(handle.context).scaledTouchSlop.toFloat()
        if (!motion.movedBeyond(event.rawX, event.rawY, slop)) return
        activeSwipeRevealed = true
        policy.swipeDown(now)
        scheduleHide()
    }

    private fun updateMotion(handle: View, event: MotionEvent, maxOffsetDp: Float) {
        val pill = pillRect(handle) ?: return
        val density = handle.resources.displayMetrics.density
        val margin = 2f * density
        // 单方向最大位移：跟随范围设置，且不超过小白条到宿主边缘的剩余空间
        val cap = maxOffsetDp.coerceAtLeast(0f) * density
        motion.move(
            event.rawX,
            event.rawY,
            minOf(cap, maxOf(0f, pill.left - margin)),
            minOf(cap, maxOf(0f, handle.width - pill.right - margin)),
            minOf(cap, maxOf(0f, pill.top - margin)),
            minOf(cap, maxOf(0f, handle.height - pill.bottom - margin)),
            SystemClock.uptimeMillis(),
        )
    }

    private fun handleAt(frame: View?, rawX: Float, rawY: Float): View? {
        val views = synchronized(handles) { handles.keys.toList() }
        for (view in views) {
            if (!view.isAttachedToWindow || view.visibility != View.VISIBLE) continue
            if (view.width <= 0 || view.height <= 0) continue
            var parent: ViewParent? = view.parent
            while (parent != null && parent !== frame) parent = parent.parent
            if (frame != null && parent !== frame) continue
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            val density = view.resources.displayMetrics.density
            val pill = pillRect(view)
            val onPill = pill != null &&
                rawX >= location[0] + pill.left - 12f * density &&
                rawX <= location[0] + pill.right + 12f * density &&
                rawY >= location[1] + pill.top - 12f * density &&
                rawY <= location[1] + pill.bottom + 12f * density
            val display = android.graphics.Point()
            view.display?.getRealSize(display)
            val bottomEdge = location[1] + view.height
            val band = TOUCH_BAND_DP * density
            val inBottomBand = rawY >= bottomEdge - band && rawY <= display.y.toFloat()
            if (onPill || inBottomBand) return view
        }
        return null
    }

    private fun pillRect(handle: View): RectF? {
        runCatching {
            val method = handle.javaClass.getMethod("getPillRect")
            return method.invoke(handle) as? RectF
        }
        // 部分版本不再暴露 getPillRect，按 mRadius / mBottom 还原
        val radius = runCatching { Reflect.getObjectField(handle, "mRadius") as? Number }.getOrNull() ?: return null
        val bottom = runCatching { Reflect.getObjectField(handle, "mBottom") as? Number }.getOrNull() ?: return null
        val bottomEdge = handle.height - bottom.toFloat()
        return RectF(0f, bottomEdge - 2f * radius.toFloat(), handle.width.toFloat(), bottomEdge)
    }

    private fun trackHandle(view: View) {
        synchronized(handles) {
            if (handles.containsKey(view)) return
            handles[view] = null
        }
        motion.rebaseForNewHost(SystemClock.uptimeMillis())
        initialize(view.context)
        applyConfig(SystemClock.uptimeMillis())
    }

    // ---------------- 初始化 / 调度 ----------------

    private fun initialize(context: Context) {
        if (receiverInstalled) return
        main = Handler(Looper.getMainLooper())
        runtimeContext = context.applicationContext ?: context
        receiverInstalled = true
        installInputMonitor()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        runCatching {
            context.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(ignored: Context?, intent: Intent?) {
                    val now = SystemClock.uptimeMillis()
                    when (intent?.action) {
                        Intent.ACTION_USER_PRESENT -> {
                            policy.reveal(now)
                            scheduleHide()
                        }

                        Intent.ACTION_SCREEN_OFF -> {
                            policy.clearTouchReveal()
                            policy.clearSwipeReveal()
                            motion.reset()
                            activeTouchHandle = null
                            main?.removeCallbacks(detachedGestureTimeout)
                            main?.removeCallbacks(hideRunnable)
                        }
                    }
                    invalidateHandles()
                }
            }, filter, Context.RECEIVER_NOT_EXPORTED)
        }
        // 附着可能晚于观察者的首次回调，主动取一次快照兜底
        Thread {
            runCatching {
                val wrapper = Class.forName(
                    "com.android.systemui.shared.system.ActivityManagerWrapper", false, loader
                )
                val instance = wrapper.getField("sInstance").get(null)
                val result = wrapper.getMethod("getRunningTasks", Int::class.javaPrimitiveType)
                    .invoke(instance, 0)
                if (result is Array<*>) {
                    val first = result.firstOrNull() as? ActivityManager.RunningTaskInfo
                    dispatch {
                        if (!foregroundObserved) foreground(first)
                    }
                }
            }
        }.apply {
            isDaemon = true
            name = "hypernavbar-gesture-task"
        }.start()
    }

    private fun installInputMonitor() {
        if (!enabled() || hasInputMonitor() || loader == null) return
        var monitor: Any? = null
        runCatching {
            // 导航栏窗口收不到全部手势输入流：改用与 SystemUI 相同的被动监听包装，不抢占事件
            val monitorType = Class.forName(
                "com.android.systemui.shared.system.InputMonitorCompat", false, loader
            )
            val listenerType = Class.forName(
                "com.android.systemui.shared.system.InputChannelCompat\$InputEventListener", false, loader
            )
            val listener = Proxy.newProxyInstance(loader, arrayOf(listenerType)) { _, method, args ->
                if (method.name == "onInputEvent" && args?.size == 1 && args[0] is MotionEvent) {
                    runCatching { observeTouch(null, args[0] as MotionEvent) }
                }
                null
            }
            monitor = monitorType.getConstructor(String::class.java, Int::class.javaPrimitiveType)
                .newInstance("hypernavbar-gesture-handle", 0)
            val receiver = monitorType.getMethod(
                "getInputReceiver", Looper::class.java, Choreographer::class.java, listenerType
            ).invoke(monitor, Looper.getMainLooper(), Choreographer.getInstance(), listener)
            inputMonitor = monitor
            inputReceiver = receiver
            inputMonitorInstalled = true
        }.onFailure { error ->
            HookHelper.log("$tag: input monitor unavailable", error)
            monitor?.let { runCatching { Reflect.callMethod(it, "dispose") } }
        }
    }

    private fun hasInputMonitor(): Boolean = inputMonitorInstalled && inputMonitor != null && inputReceiver != null

    private fun stopInputMonitor() {
        val receiver = inputReceiver
        val monitor = inputMonitor
        inputReceiver = null
        inputMonitor = null
        inputMonitorInstalled = false
        receiver?.let { runCatching { Reflect.callMethod(it, "dispose") } }
        monitor?.let { runCatching { Reflect.callMethod(it, "dispose") } }
    }

    private fun scheduleHide() {
        val handler = main ?: return
        handler.removeCallbacks(hideRunnable)
        if (!enabled()) return
        val remaining = policy.remaining(SystemClock.uptimeMillis())
        if (remaining > 0L) handler.postDelayed(hideRunnable, remaining)
    }

    private fun invalidateHandles() {
        val views = synchronized(handles) { handles.keys.toList() }
        for (view in views) if (view.isAttachedToWindow) view.invalidate()
    }

    private fun appliesTo(view: View): Boolean {
        if (view.display?.displayId != 0) return false
        val keyguard = view.context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return keyguard == null || !keyguard.isKeyguardLocked
    }

    private fun dispatch(action: Runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run()
        } else {
            Handler(Looper.getMainLooper()).post(action)
        }
    }

    private class HandleVisual(hidden: Boolean) {
        val fade = GestureHandleFade(hidden)
    }

    private data class Config(
        val enabled: Boolean,
        val motion: Boolean,
        val touchReveal: Boolean,
        val globalMode: String,
        val follow: String,
        val rules: Map<String, String>,
        val motionHoldMs: Float,
        val motionScale: Float,
        val motionRangeDp: Float,
        val motionReturn: Float,
        val hasPolicy: Boolean,
    )

    private companion object {
        const val HANDLE = "com.android.systemui.navigationbar.gestural.NavigationHandle"
        const val QUICKSWITCH_HANDLE = "com.android.systemui.navigationbar.gestural.QuickswitchOrientedNavHandle"
        const val NAV_BAR_FRAME = "com.android.systemui.navigationbar.views.NavigationBarFrame"
        const val TOP_OBSERVER = "com.miui.systemui.functions.MiuiTopActivityObserver"
        const val CONTROLLER = "com.android.systemui.navigationbar.NavigationBarControllerImpl"

        /** 底部触摸唤醒区域高度（dp）。 */
        const val TOUCH_BAND_DP = 16f
    }
}
