package cn.ianzb.hypernavbar.hook.base

import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.ApplicationInfo
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

/**
 * 目标进程信息封装，屏蔽 libxposed 参数细节。
 */
class PackageTarget(
    val packageName: String,
    val processName: String,
    val applicationInfo: ApplicationInfo?,
    val classLoader: ClassLoader?,
    val isSystemServer: Boolean = false,
) {
    /** 目标应用版本名（尽力解析，取不到为空）。 */
    val appVersionName: String get() = appVersion.first

    /** 目标应用 versionCode（尽力解析，取不到为 0）。 */
    val appVersionCode: Long get() = appVersion.second

    private val appVersion: Pair<String, Long> by lazy { resolveAppVersion() }

    @SuppressLint("PrivateApi")
    private fun resolveAppVersion(): Pair<String, Long> {
        val application = runCatching {
            Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication")
                .invoke(null) as? Application
        }.getOrNull()
        val pm = application?.packageManager ?: return "" to 0L
        return runCatching {
            val info = pm.getPackageInfo(packageName, 0)
            (info.versionName ?: "") to info.longVersionCode
        }.getOrDefault("" to 0L)
    }

    companion object {

        fun from(param: PackageReadyParam): PackageTarget = PackageTarget(
            packageName = param.packageName,
            processName = param.applicationInfo.processName ?: param.packageName,
            applicationInfo = param.applicationInfo,
            classLoader = param.classLoader,
        )

        @Suppress("unused")
        fun fromSystemServer(param: SystemServerStartingParam): PackageTarget = PackageTarget(
            packageName = "android",
            processName = "system_server",
            applicationInfo = null,
            classLoader = param.classLoader,
            isSystemServer = true,
        )
    }
}
