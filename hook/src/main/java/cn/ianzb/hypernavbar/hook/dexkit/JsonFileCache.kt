package cn.ianzb.hypernavbar.hook.dexkit

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.luckypray.dexkit.DexKitCacheBridge
import org.luckypray.dexkit.annotations.DexKitExperimentalApi
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.StandardOpenOption
import java.util.TreeMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [DexKitCacheBridge.Cache] 的 JSON 文件实现。
 *
 * 缓存保存在 JSON 文件中，构造时做版本校验；写入先入队，再由单消费者串行应用，
 * 最后在 [flush] 时统一落盘。
 */
@OptIn(DexKitExperimentalApi::class)
internal class JsonFileCache(
    private val cacheFile: File,
    private val pkgVersion: String?,
    private val osVersion: String?,
    private val tag: String,
) : DexKitCacheBridge.Cache {

    companion object {
        private const val CACHE_VERSION = 1
        private const val KEY_VERSION = "version"
        private const val KEY_PKG_VERSION = "pkgVersion"
        private const val KEY_OS_VERSION = "osVersion"
        private const val KEY_STRINGS = "strings"
        private const val KEY_LISTS = "lists"
        const val TAG = "HyperNavBar"
    }

    private enum class WriteType { PUT_STRING, PUT_LIST, REMOVE, CLEAR }

    private data class WriteSuggestion(
        val type: WriteType,
        val key: String? = null,
        val stringValue: String? = null,
        val listValue: List<String>? = null,
    )

    private val ioLock = Any()
    private val strings = LinkedHashMap<String, String>()
    private val lists = LinkedHashMap<String, List<String>>()
    private val writeSuggestions = ConcurrentLinkedQueue<WriteSuggestion>()
    private val dirty = AtomicBoolean(false)

    init {
        loadAndValidate()
    }

    override fun getString(key: String, default: String?): String? = synchronized(ioLock) {
        applyWriteSuggestionsLocked()
        strings[key] ?: default
    }

    override fun putString(key: String, value: String) {
        enqueueWriteSuggestion(WriteSuggestion(WriteType.PUT_STRING, key = key, stringValue = value))
    }

    override fun getStringList(key: String, default: List<String>?): List<String>? = synchronized(ioLock) {
        applyWriteSuggestionsLocked()
        lists[key]?.let(::ArrayList) ?: default
    }

    override fun putStringList(key: String, value: List<String>) {
        enqueueWriteSuggestion(WriteSuggestion(WriteType.PUT_LIST, key = key, listValue = ArrayList(value)))
    }

    override fun remove(key: String) {
        enqueueWriteSuggestion(WriteSuggestion(WriteType.REMOVE, key = key))
    }

    override fun getAllKeys(): Collection<String> = synchronized(ioLock) {
        applyWriteSuggestionsLocked()
        LinkedHashSet<String>(strings.size + lists.size).apply {
            addAll(strings.keys)
            addAll(lists.keys)
        }
    }

    override fun clearAll() {
        enqueueWriteSuggestion(WriteSuggestion(WriteType.CLEAR))
    }

    fun flush() {
        synchronized(ioLock) {
            while (true) {
                val suggestionCount = applyWriteSuggestionsLocked()
                if (suggestionCount == 0 && !dirty.get()) return
                val stringsSnapshot = TreeMap(strings)
                val listsSnapshot = TreeMap(lists)
                dirty.set(false)
                if (!saveToDisk(stringsSnapshot, listsSnapshot)) {
                    dirty.set(true)
                    return
                }
                if (writeSuggestions.isEmpty() && !dirty.get()) return
            }
        }
    }

    private fun loadAndValidate() {
        synchronized(ioLock) {
            if (!cacheFile.exists()) return
            try {
                val text = cacheFile.readText(Charsets.UTF_8)
                if (text.isBlank()) return
                val root = JSONObject(text)
                val fileVersion = root.optInt(KEY_VERSION, 0)
                val filePkgVersion = root.takeIf { it.has(KEY_PKG_VERSION) }?.getString(KEY_PKG_VERSION)
                val fileOsVersion = root.takeIf { it.has(KEY_OS_VERSION) }?.getString(KEY_OS_VERSION)

                var needClear = fileVersion != CACHE_VERSION
                if (pkgVersion != null && pkgVersion != filePkgVersion) needClear = true
                if (osVersion != null && osVersion != fileOsVersion) needClear = true
                if (needClear) {
                    dirty.set(true)
                    return
                }

                root.optJSONObject(KEY_STRINGS)?.let { strObj ->
                    for (k in strObj.keys()) strings[k] = strObj.getString(k)
                }
                root.optJSONObject(KEY_LISTS)?.let { listObj ->
                    for (k in listObj.keys()) {
                        val arr = listObj.getJSONArray(k)
                        val list = ArrayList<String>(arr.length())
                        for (i in 0 until arr.length()) list.add(arr.getString(i))
                        lists[k] = list
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "$tag: failed to load DexKit cache, starting fresh", t)
                strings.clear()
                lists.clear()
                dirty.set(true)
            }
        }
    }

    private fun saveToDisk(
        stringsSnapshot: Map<String, String>,
        listsSnapshot: Map<String, List<String>>,
    ): Boolean {
        return try {
            val dir = cacheFile.parentFile
            if (dir != null && !dir.exists() && !dir.mkdirs() && !dir.exists()) return false

            val root = JSONObject()
            root.put(KEY_VERSION, CACHE_VERSION)
            if (pkgVersion != null) root.put(KEY_PKG_VERSION, pkgVersion)
            if (osVersion != null) root.put(KEY_OS_VERSION, osVersion)

            val strObj = JSONObject()
            for ((k, v) in stringsSnapshot) strObj.put(k, v)
            root.put(KEY_STRINGS, strObj)

            val listObj = JSONObject()
            for ((k, v) in listsSnapshot) {
                val arr = JSONArray()
                for (s in v) arr.put(s)
                listObj.put(k, arr)
            }
            root.put(KEY_LISTS, listObj)

            val json = root.toString(2).replace("\\/", "/")
            if (!cacheFile.exists() && !cacheFile.createNewFile() && !cacheFile.exists()) return false

            FileChannel.open(
                cacheFile.toPath(),
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING,
            ).use { channel ->
                var lock: FileLock? = null
                try {
                    lock = channel.lock()
                    val buf = ByteBuffer.wrap(json.toByteArray(Charsets.UTF_8))
                    while (buf.hasRemaining()) channel.write(buf)
                    channel.force(false)
                } finally {
                    lock?.release()
                }
            }
            true
        } catch (t: Throwable) {
            Log.w(TAG, "$tag: failed to save DexKit cache", t)
            false
        }
    }

    private fun enqueueWriteSuggestion(suggestion: WriteSuggestion) {
        writeSuggestions.offer(suggestion)
        dirty.set(true)
    }

    private fun applyWriteSuggestionsLocked(): Int {
        var count = 0
        while (true) {
            val suggestion = writeSuggestions.poll() ?: break
            when (suggestion.type) {
                WriteType.PUT_STRING -> strings[suggestion.key!!] = suggestion.stringValue!!
                WriteType.PUT_LIST -> lists[suggestion.key!!] = suggestion.listValue!!
                WriteType.REMOVE -> {
                    val key = suggestion.key ?: continue
                    strings.remove(key)
                    lists.remove(key)
                }
                WriteType.CLEAR -> {
                    strings.clear()
                    lists.clear()
                }
            }
            count++
        }
        return count
    }
}
