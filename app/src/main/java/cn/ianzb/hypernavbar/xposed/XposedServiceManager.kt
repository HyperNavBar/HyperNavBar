package cn.ianzb.hypernavbar.xposed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.ianzb.hypernavbar.prefs.PrefsStore
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

/**
 * LSPosed 服务绑定：作用域查询 / 主动申请。
 */
object XposedServiceManager {

    private var service: XposedService? = null

    var isActivated by mutableStateOf(false)
        private set

    /** 是否已获得 Root 权限（异步检测）。 */
    var isRootAvailable by mutableStateOf(false)
        private set

    /** Root 是否已检测完成。 */
    var rootChecked by mutableStateOf(false)
        private set

    var scope by mutableStateOf<List<String>>(emptyList())
        private set

    @Volatile
    private var rootChecking = false

    private val listener = object : XposedServiceHelper.OnServiceListener {
        override fun onServiceBind(service: XposedService) {
            this@XposedServiceManager.service = service
            isActivated = true
            PrefsStore.attachRemote(service.getRemotePreferences(PrefsStore.REMOTE_GROUP))
            SafeModeReader.attach(service)
            refreshScope()
            // 服务绑定后立即读取一次 hook 状态，避免页面先于服务加载而显示默认色。
            HookStatusReader.refresh()
        }

        override fun onServiceDied(service: XposedService) {
            this@XposedServiceManager.service = null
            isActivated = false
            scope = emptyList()
            PrefsStore.attachRemote(null)
            SafeModeReader.attach(null)
        }
    }

    fun init() {
        XposedServiceHelper.registerListener(listener)
        checkRoot()
    }

    /** 异步检测 Root 权限，避免阻塞主线程；重复调用会被合并。 */
    fun checkRoot() {
        if (rootChecking) return
        rootChecking = true
        Thread {
            val root = RootHelper.isRootAvailable()
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                isRootAvailable = root
                rootChecked = true
                rootChecking = false
            }
        }.apply {
            isDaemon = true
            name = "MiuixRootCheck"
        }.start()
    }

    fun getService(): XposedService? = service

    fun refreshScope() {
        scope = service?.scope ?: emptyList()
    }

    @Suppress("unused")
    fun isInScope(packageName: String): Boolean = scope.contains(packageName)

    /**
     * 主动申请作用域：仅对「服务实时作用域」与「历史已申请集合」之外的包发起请求。
     *
     * 不能只依赖 `service.scope`：部分 LSPosed 版本不会把新申请的包立即反映到 `scope`，
     * 会导致每次应用规则都重复申请同一批包、反复弹授权通知。这里额外持久记录已申请的包兜底，
     * 申请失败时撤销记录以便下次重试。
     */
    fun ensureScope(packages: List<String>, onResult: ((Boolean, String?) -> Unit)? = null) {
        val current = service
        if (current == null) {
            onResult?.invoke(false, "service unavailable")
            return
        }
        val authorized = current.scope.orEmpty().toSet()
        scope = authorized.toList()
        val requested = PrefsStore.getStringSet(KEY_REQUESTED_SCOPE, emptySet())
        val known = authorized + requested
        val missing = packages.filter { it.isNotEmpty() && it !in known }
        if (missing.isEmpty()) {
            onResult?.invoke(true, null)
            return
        }
        PrefsStore.put(KEY_REQUESTED_SCOPE, requested + missing)
        current.requestScope(missing, object : XposedService.OnScopeEventListener {
            override fun onScopeRequestApproved(approved: List<String>) {
                refreshScope()
                onResult?.invoke(true, null)
            }

            override fun onScopeRequestFailed(message: String) {
                // 申请失败：撤销记录，允许下次重试
                PrefsStore.put(
                    KEY_REQUESTED_SCOPE,
                    PrefsStore.getStringSet(KEY_REQUESTED_SCOPE, emptySet()) - missing.toSet(),
                )
                onResult?.invoke(false, message)
            }
        })
    }

    @Suppress("unused")
    fun removeScope(packages: List<String>) {
        service?.removeScope(packages)
        if (packages.isNotEmpty()) {
            // 被移出作用域的包允许后续重新申请
            PrefsStore.put(
                KEY_REQUESTED_SCOPE,
                PrefsStore.getStringSet(KEY_REQUESTED_SCOPE, emptySet()) - packages.toSet(),
            )
        }
        refreshScope()
    }

    /** 本模块历史上主动申请过作用域的包（含已自动申请的第三方应用）。 */
    fun requestedPackages(): Set<String> = PrefsStore.getStringSet(KEY_REQUESTED_SCOPE, emptySet())

    /**
     * 取消本模块此前的全部作用域申请：把申请过的包移出作用域并清空申请记录。
     *
     * 用于「默认不 Hook」后清理历史上被自动申请、当前并不需要注入的应用。
     * 用户手动添加的作用域（不在申请记录内）不受影响。
     */
    fun cancelRequestedScope(onResult: ((Boolean, String?) -> Unit)? = null) {
        val requested = requestedPackages()
        if (requested.isEmpty()) {
            onResult?.invoke(true, null)
            return
        }
        val current = service
        if (current == null) {
            onResult?.invoke(false, "service unavailable")
            return
        }
        current.removeScope(requested.toList())
        PrefsStore.put(KEY_REQUESTED_SCOPE, emptySet<String>())
        refreshScope()
        onResult?.invoke(true, null)
    }

    private const val KEY_REQUESTED_SCOPE = "scope_requested_packages"
}
