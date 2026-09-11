package cn.ianzb.hypernavbar

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    private const val REPO = "HyperNavBar/HyperNavBar"
    private const val API_URL = "https://api.github.com/repos/$REPO/releases/latest"
    private const val RELEASES_URL = "https://github.com/$REPO/releases"

    data class UpdateInfo(
        val currentVersion: String,
        val latestVersion: String,
        val releaseUrl: String,
        val releaseName: String,
        val releaseNotes: String,
        val hasUpdate: Boolean,
    )

    suspend fun checkForUpdate(context: Context): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val currentVersion = currentVersion(context)
            val conn = URL(API_URL).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "HyperNavBar/$currentVersion")
                conn.setRequestProperty("Accept", "application/vnd.github+json")

                val code = conn.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    throw Exception("HTTP $code")
                }

                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val tag = json.optString("tag_name", "")
                val latestVersion = tag.removePrefix("v").removePrefix("V").ifEmpty { currentVersion }
                UpdateInfo(
                    currentVersion = currentVersion,
                    latestVersion = latestVersion,
                    releaseUrl = json.optString("html_url", RELEASES_URL).ifEmpty { RELEASES_URL },
                    releaseName = json.optString("name", latestVersion),
                    releaseNotes = json.optString("body", ""),
                    hasUpdate = isNewerVersion(latestVersion, currentVersion),
                )
            } finally {
                conn.disconnect()
            }
        }
    }

    fun currentVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
    } catch (_: Exception) {
        "1.0"
    }

    fun isNewerVersion(latest: String, current: String): Boolean {
        val l = latest.trim().removePrefix("v").removePrefix("V")
        val c = current.trim().removePrefix("v").removePrefix("V")
        if (l == c) return false
        val lp = l.split(Regex("[.\\-+]"))
        val cp = c.split(Regex("[.\\-+]"))
        for (i in 0 until maxOf(lp.size, cp.size)) {
            val lv = lp.getOrNull(i)?.toIntOrNull() ?: 0
            val cv = cp.getOrNull(i)?.toIntOrNull() ?: 0
            if (lv != cv) return lv > cv
        }
        return false
    }
}
