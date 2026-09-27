package cn.ianzb.hypernavbar.xposed

import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONObject
import java.io.FileInputStream

/**
 * 读取 hook 进程写入的远程状态文件，得到每个 hook 的成功 / 失败结果。
 */
object HookStatusReader {

    private const val FILE_PREFIX = "hook_status_"

    private val status = mutableStateMapOf<String, Boolean>()

    fun refresh() {
        val service = XposedServiceManager.getService()
        if (service == null) {
            status.clear()
            return
        }
        val merged = mutableMapOf<String, Boolean>()
        runCatching {
            service.listRemoteFiles()
                .filter { it.startsWith(FILE_PREFIX) }
                .forEach { fileName ->
                    runCatching {
                        service.openRemoteFile(fileName)?.use { descriptor ->
                            val text = FileInputStream(descriptor.fileDescriptor)
                                .bufferedReader()
                                .use { it.readText() }
                            val json = JSONObject(text)
                            val map = json.optJSONObject("status") ?: return@forEach
                            map.keys().forEach { key -> merged[key] = map.getBoolean(key) }
                        }
                    }
                }
        }
        status.clear()
        status.putAll(merged)
    }

    fun statusOf(hookId: String): Boolean? = status[hookId]

    fun clear() {
        status.clear()
    }
}

/** 单个配置项的 hook 状态。 */
enum class HookStatus { SUCCESS, FAILED, NOT_APPLIED }
