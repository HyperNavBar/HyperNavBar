package cn.ianzb.hypernavbar.hook.dexkit

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import cn.ianzb.hypernavbar.hook.base.PackageTarget
import org.luckypray.dexkit.DexKitCacheBridge
import org.luckypray.dexkit.DexKitCacheBridge.RecyclableBridge
import org.luckypray.dexkit.annotations.DexKitExperimentalApi
import org.luckypray.dexkit.result.ClassData
import org.luckypray.dexkit.result.ClassDataList
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.FieldDataList
import org.luckypray.dexkit.result.MethodData
import org.luckypray.dexkit.result.MethodDataList
import org.luckypray.dexkit.result.base.BaseData
import org.luckypray.dexkit.wrap.DexClass
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import org.luckypray.dexkit.wrap.ISerializable
import java.io.File

/**
 * DexKit CacheBridge 生命周期与带缓存的成员解析。
 *
 * 缓存命中：直接从 [JsonFileCache] 反序列化，不创建原生桥。
 * 缓存未命中：通过 [RecyclableBridge.withBridge] 执行查询，序列化后写回缓存。
 */
@OptIn(DexKitExperimentalApi::class)
object DexKitCacheManager {

    const val TAG = "HyperNavBar"

    /** 缓存目录名（App 端 root 清理时也使用该常量）。 */
    const val CACHE_DIR = "hypernavbar"
    const val CACHE_FILE = "dexkit_cache.json"

    private val lock = Any()

    @Volatile private var tag: String = TAG
    @Volatile private var target: PackageTarget? = null
    @Volatile private var bridge: RecyclableBridge? = null
    @Volatile private var cache: JsonFileCache? = null
    @Volatile private var cacheInitialized = false

    fun init(target: PackageTarget, tag: String) {
        synchronized(lock) {
            this.target = target
            this.tag = tag.ifEmpty { TAG }

            val appInfo = target.applicationInfo
                ?: throw IllegalStateException("DexKit requires ApplicationInfo: ${target.packageName}")

            if (!cacheInitialized) {
                System.loadLibrary("dexkit")
                val jsonCache = createJsonFileCache(appInfo, target)
                try {
                    DexKitCacheBridge.init(jsonCache)
                } catch (_: IllegalStateException) {
                    // 当前进程已初始化过，复用即可
                }
                cache = jsonCache
                cacheInitialized = true
            }

            bridge = createRecyclableBridge(appInfo, target.classLoader)
            Log.d(TAG, "$tag: DexKit initialized for ${target.packageName}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> findMember(key: String, finder: IDexKit): T? {
        val currentTarget = target ?: throw IllegalStateException("DexKit not ready")
        val classLoader = currentTarget.classLoader ?: throw IllegalStateException("DexKit classLoader unavailable")

        cache?.getString(key, null)?.let { cached ->
            return deserializeAndResolve(cached, classLoader) as T
        }

        val currentBridge = bridge ?: throw IllegalStateException("DexKit not initialized")
        var result: Any? = null
        currentBridge.withBridge { rawBridge ->
            val data: BaseData = try {
                finder.dexkit(rawBridge)
            } catch (e: ReflectiveOperationException) {
                throw RuntimeException(e)
            }
            result = resolveAndCache(data, key, classLoader)
        }
        return result as T?
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> findMemberList(key: String, finder: IDexKitList): List<T>? {
        val currentTarget = target ?: throw IllegalStateException("DexKit not ready")
        val classLoader = currentTarget.classLoader ?: throw IllegalStateException("DexKit classLoader unavailable")

        cache?.getStringList(key, null)?.let { cached ->
            return cached.map { deserializeAndResolve(it, classLoader) as T }
        }

        val currentBridge = bridge ?: throw IllegalStateException("DexKit not initialized")
        val resultList = mutableListOf<T>()
        currentBridge.withBridge { rawBridge ->
            val dataList = try {
                finder.dexkit(rawBridge)
            } catch (e: ReflectiveOperationException) {
                throw RuntimeException(e)
            }
            val serialized = mutableListOf<String>()
            when (dataList) {
                is FieldDataList -> for (f in dataList) {
                    serialized.add(f.toDexField().serialize())
                    resultList.add(f.getFieldInstance(classLoader) as T)
                }
                is MethodDataList -> for (m in dataList) {
                    serialized.add(m.toDexMethod().serialize())
                    resultList.add(m.getMethodInstance(classLoader) as T)
                }
                is ClassDataList -> for (c in dataList) {
                    serialized.add(c.toDexClass().serialize())
                    resultList.add(c.getInstance(classLoader) as T)
                }
            }
            cache?.putStringList(key, serialized)
        }
        return resultList
    }

    fun releaseBridge() {
        synchronized(lock) {
            cache?.flush()
            bridge?.close()
            bridge = null
            target = null
            Log.d(TAG, "$tag: DexKit bridge closed")
        }
    }

    /** 当前进程内清空全部 DexKit 缓存。 */
    fun clearAllCache() {
        synchronized(lock) {
            if (cacheInitialized) {
                DexKitCacheBridge.clearAllCache()
                cache?.flush()
            }
        }
    }

    /**
     * 直接从磁盘删除所有缓存文件（root 场景由 App 端调用；这里提供非 root 兜底）。
     */
    fun deleteAllCacheFiles(context: android.content.Context, scopeList: Collection<String>?) {
        if (scopeList.isNullOrEmpty()) return
        for (folderName in scopeList) {
            try {
                val baseDir = File(context.filesDir.parent, "../$folderName/cache/$CACHE_DIR")
                deleteRecursively(baseDir)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to delete DexKit cache for $folderName", t)
            }
        }
    }

    private fun createJsonFileCache(appInfo: ApplicationInfo, target: PackageTarget): JsonFileCache {
        val cacheDir = File(File(appInfo.dataDir, "cache"), CACHE_DIR)
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val cacheFile = File(cacheDir, CACHE_FILE)

        val (versionName, versionCode) = packageVersion(target.packageName)
        val pkgVersion = if (versionName != null && versionCode != null) "$versionName($versionCode)" else null
        val isSystemUi = "com.android.systemui" == target.packageName
        val osVersion = if (isSystemUi) Build.VERSION.INCREMENTAL else null

        return JsonFileCache(cacheFile, pkgVersion, osVersion, tag)
    }

    private fun createRecyclableBridge(appInfo: ApplicationInfo, classLoader: ClassLoader?): RecyclableBridge {
        val appTag = tag
        val splitDirs = appInfo.splitSourceDirs
        return if (!splitDirs.isNullOrEmpty() && classLoader != null) {
            Log.d(TAG, "$tag: DexKit loading by classLoader (split APK)")
            DexKitCacheBridge.create(appTag, classLoader)
        } else {
            DexKitCacheBridge.create(appTag, appInfo.sourceDir)
        }
    }

    private fun packageVersion(packageName: String): Pair<String?, Int?> = runCatching {
        val context = currentApplication()
        val info = context?.packageManager?.getPackageInfo(packageName, 0)
        info?.versionName to info?.let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode.toInt() else @Suppress("DEPRECATION") it.versionCode }
    }.getOrNull() ?: (null to null)

    private fun currentApplication(): Application? = runCatching {
        val thread = Class.forName("android.app.ActivityThread")
        thread.getMethod("currentApplication").invoke(null) as? Application
    }.getOrNull()

    private fun deserializeAndResolve(serialized: String, classLoader: ClassLoader): Any {
        return when (val wrapper = ISerializable.deserialize(serialized)) {
            is DexMethod -> wrapper.getMethodInstance(classLoader)
            is DexField -> wrapper.getFieldInstance(classLoader)
            is DexClass -> wrapper.getInstance(classLoader)
            else -> throw IllegalStateException("Unknown ISerializable type: ${wrapper.javaClass}")
        }
    }

    private fun resolveAndCache(data: BaseData, key: String, classLoader: ClassLoader): Any {
        return when (data) {
            is FieldData -> {
                cache?.putString(key, data.toDexField().serialize())
                data.getFieldInstance(classLoader)
            }
            is MethodData -> {
                cache?.putString(key, data.toDexMethod().serialize())
                data.getMethodInstance(classLoader)
            }
            is ClassData -> {
                cache?.putString(key, data.toDexClass().serialize())
                data.getInstance(classLoader)
            }
            else -> throw IllegalStateException("Unknown BaseData type: ${data.javaClass}")
        }
    }

    private fun deleteRecursively(file: File) {
        if (!file.exists()) return
        if (file.isDirectory) file.listFiles()?.forEach { deleteRecursively(it) }
        file.delete()
    }
}
