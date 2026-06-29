package com.ianzb.hypernavbar.rules

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.File

object RootApplier {

    private const val MARKER_PATH = "/data/system/HyperNavBarRules"

    suspend fun applyRules(
        jsonContent: String,
        targetPath: String,
        tempDir: File,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val tempFile = File(tempDir, "hypernavbar_rules.tmp")
            tempFile.writeText(jsonContent, Charsets.UTF_8)

            val process = Runtime.getRuntime().exec("su")
            val stream = DataOutputStream(process.outputStream)

            val bakPath = "$targetPath.bak"
            val marker = shellQuote(MARKER_PATH)
            val target = shellQuote(targetPath)
            val backup = shellQuote(bakPath)
            val temp = shellQuote(tempFile.absolutePath)

            stream.writeBytes("if [ ! -f $marker ]; then cp -f $target $backup 2>/dev/null; touch $marker; fi\n")
            stream.writeBytes("cp -f $temp $target\n")
            stream.writeBytes("chmod 600 $target\n")
            stream.writeBytes("chown system:system $target\n")
            stream.writeBytes("rm -f $temp\n")
            stream.flush()

            stream.writeBytes("cmd miui_navigation_bar_immersive update\n")
            stream.flush()

            stream.writeBytes("exit\n")
            stream.flush()

            process.waitFor()
            val exitCode = process.exitValue()
            process.destroy()
            exitCode == 0
        } catch (_: Exception) {
            false
        }
    }

    @Suppress("unused")
    suspend fun restoreBackup(targetPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val bakPath = "$targetPath.bak"
            val marker = shellQuote(MARKER_PATH)
            val target = shellQuote(targetPath)
            val backup = shellQuote(bakPath)

            val process = Runtime.getRuntime().exec("su")
            val stream = DataOutputStream(process.outputStream)

            stream.writeBytes("if [ -f $backup ]; then cp -f $backup $target && rm -f $backup && rm -f $marker; fi\n")
            stream.writeBytes("chmod 600 $target\n")
            stream.writeBytes("chown system:system $target\n")
            stream.flush()

            stream.writeBytes("cmd miui_navigation_bar_immersive update\n")
            stream.flush()

            stream.writeBytes("exit\n")
            stream.flush()

            process.waitFor()
            val exitCode = process.exitValue()
            process.destroy()
            exitCode == 0
        } catch (_: Exception) {
            false
        }
    }

    fun isCustomRulesApplied(targetPath: String): Boolean {
        val bakPath = "$targetPath.bak"
        return try {
            val process = Runtime.getRuntime().exec("su")
            val stream = DataOutputStream(process.outputStream)
            stream.writeBytes("test -f ${shellQuote(bakPath)} && echo YES || echo NO\n")
            stream.writeBytes("exit\n")
            stream.flush()
            process.waitFor()
            val reader = process.inputStream.bufferedReader()
            reader.use { it.readLine()?.trim() == "YES" }
        } catch (_: Exception) {
            false
        }
    }

    private fun shellQuote(path: String): String {
        return "'${path.replace("'", "'\\''")}'"
    }
}
