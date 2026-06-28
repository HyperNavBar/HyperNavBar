package com.ianzb.hypernavbar.rules

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object RuleFetcher {

    data class FetchResult(
        val rawJson: String,
        val appCount: Int,
        val configName: String,
        val nbiRules: JSONObject,
    )

    suspend fun fetch(config: RuleConfigSource): Result<FetchResult> = when (config.type) {
        RuleType.LOCAL -> parseLocal(config.jsonContent)
        RuleType.CLOUD -> fetchUrl(config.url)
    }

    private fun parseLocal(jsonContent: String): Result<FetchResult> = parseJson(jsonContent)

    fun parseJson(jsonContent: String): Result<FetchResult> {
        return try {
            val root = JSONObject(jsonContent)
            val nbiRules = root.optJSONObject("NBIRules") ?: return Result.failure(Exception("Missing NBIRules"))
            val appCount = nbiRules.length()
            val configName = root.optString("name", "沉浸规则")
            Result.success(FetchResult(
                rawJson = jsonContent,
                appCount = appCount,
                configName = configName,
                nbiRules = nbiRules,
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchUrl(urlString: String): Result<FetchResult> = withContext(Dispatchers.IO) {
        val conn = try {
            URL(urlString).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
        try {
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "HyperNavBar/1.0")

            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(Exception("HTTP $code"))
            }

            val rawJson = conn.inputStream.bufferedReader().use { it.readText() }

            val root = JSONObject(rawJson)
            val nbiRules = root.optJSONObject("NBIRules") ?: return@withContext Result.failure(Exception("Missing NBIRules"))
            val appCount = nbiRules.length()
            val configName = root.optString("name", "沉浸规则")

            Result.success(FetchResult(
                rawJson = rawJson,
                appCount = appCount,
                configName = configName,
                nbiRules = nbiRules,
            ))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn.disconnect()
        }
    }
}
