package com.krishna.apphub

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * v43: Render connection. Login is an API key (dashboard.render.com/u/settings#api-keys), kept in the encrypted Prefs.
 * App Hub only reads the account and the service list; it never deploys, suspends or changes anything on Render.
 */
class RenderApi(private val prefs: Prefs) {
    /** [kind] is Render's service type (web_service, static_site, background_worker, ...). */
    data class Svc(val id: String, val name: String, val kind: String, val suspended: Boolean, val url: String) {
        /** Where this service lives on the Render dashboard. */
        val dashboardUrl: String get() {
            val dir = when (kind) { "static_site" -> "static"; "background_worker" -> "worker"; "private_service" -> "pserv"; "cron_job" -> "cron"; else -> "web" }
            return "https://dashboard.render.com/$dir/$id"
        }
    }

    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(40, TimeUnit.SECONDS).build()

    private suspend fun get(path: String, key: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("https://api.render.com/v1$path")
                .header("Authorization", "Bearer $key").header("Accept", "application/json").get().build()
            http.newCall(req).execute().use { it.code to (it.body?.string() ?: "") }
        } catch (e: Exception) { 0 to (e.message ?: "no network") }
    }

    private fun errorOf(code: Int, body: String): String {
        if (code == 0) return "no network"
        val msg = runCatching { JSONObject(body).optString("message") }.getOrDefault("")
        return when {
            code == 401 || code == 403 -> "API key rejected" + if (msg.isNotBlank()) " ($msg)" else ""
            msg.isNotBlank() -> msg
            else -> "HTTP $code"
        }
    }

    /** Checks a key. Returns the account (workspace) name, or null and the reason. */
    suspend fun owner(key: String): Pair<String?, String> {
        val (c, b) = get("/owners?limit=20", key)
        if (c != 200) return null to errorOf(c, b)
        val o = runCatching { JSONArray(b).optJSONObject(0)?.optJSONObject("owner") }.getOrNull()
        val name = o?.optString("name").orEmpty().ifBlank { o?.optString("email").orEmpty() }
        return name.ifBlank { "Render account" } to ""
    }

    suspend fun services(key: String = prefs.renderKey): Pair<List<Svc>?, String> {
        val (c, b) = get("/services?limit=100", key)
        if (c != 200) return null to errorOf(c, b)
        val a = runCatching { JSONArray(b) }.getOrNull() ?: return null to "Render answered in an unexpected way"
        val list = (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optJSONObject("service") }.map {
            Svc(it.optString("id"), it.optString("name"), it.optString("type"), it.optString("suspended") == "suspended",
                it.optJSONObject("serviceDetails")?.optString("url").orEmpty())
        }.filter { it.id.isNotBlank() }.sortedBy { it.name.lowercase() }
        return list to ""
    }
}
