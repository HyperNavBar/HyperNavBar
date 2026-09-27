package cn.ianzb.hypernavbar.xposed

/**
 * 简易 root 执行工具，用于直接删除各目标应用的 DexKit 缓存目录。
 */
object RootHelper {

    fun isRootAvailable(): Boolean = runCatching {
        val process = ProcessBuilder("su", "-c", "id")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        output.contains("uid=0")
    }.getOrDefault(false)

    fun exec(command: String): Boolean = runCatching {
        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor() == 0
    }.getOrDefault(false)

    /**
     * 删除指定包名下的 DexKit 缓存目录。
     */
    fun deleteDexKitCache(packages: List<String>, cacheDirName: String): Boolean {
        if (packages.isEmpty()) return false
        val dirs = packages.joinToString(" ") { pkg ->
            "/data/user/0/$pkg/cache/$cacheDirName /data/data/$pkg/cache/$cacheDirName"
        }
        return exec("rm -rf $dirs")
    }
}
