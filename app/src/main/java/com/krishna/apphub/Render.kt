package com.krishna.apphub

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * v43: Render connection. Login is an API key (dashboard.render.com/u/settings#api-keys), kept in the encrypted Prefs.
 * v45: besides reading the account and services, App Hub can create a service from a GitHub repo and start a deploy.
 * It never suspends, deletes or changes an existing service.
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

    private suspend fun post(path: String, key: String, body: JSONObject): Pair<Int, String> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("https://api.render.com/v1$path")
                .header("Authorization", "Bearer $key").header("Accept", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            http.newCall(req).execute().use { it.code to (it.body?.string() ?: "") }
        } catch (e: Exception) { 0 to (e.message ?: "no network") }
    }

    /** v45: newest deploy status of a service (live, build_in_progress, update_in_progress, build_failed, ...), "" when unknown. */
    suspend fun lastDeploy(id: String, key: String = prefs.renderKey): String {
        val (c, b) = get("/services/$id/deploys?limit=1", key)
        if (c != 200) return ""
        return runCatching { JSONArray(b).optJSONObject(0)?.optJSONObject("deploy")?.optString("status").orEmpty() }.getOrDefault("")
    }

    /** v45: starts a new deploy of the latest commit. Returns an empty string on success, otherwise the reason. */
    suspend fun deploy(id: String, key: String = prefs.renderKey): String {
        val (c, b) = post("/services/$id/deploys", key, JSONObject())
        return if (c in 200..299) "" else errorOf(c, b)
    }

    /** v45: creates a service from a GitHub repo ("owner/name"). Website = static site, otherwise a Node web service on the free plan. */
    suspend fun create(repo: String, name: String, website: Boolean, key: String = prefs.renderKey): Pair<Svc?, String> {
        val (oc, ob) = get("/owners?limit=1", key)
        if (oc != 200) return null to errorOf(oc, ob)
        val ownerId = runCatching { JSONArray(ob).optJSONObject(0)?.optJSONObject("owner")?.optString("id").orEmpty() }.getOrDefault("")
        if (ownerId.isBlank()) return null to "no Render workspace"
        val slug = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "service" }
        val body = JSONObject().put("type", if (website) "static_site" else "web_service").put("name", slug).put("ownerId", ownerId)
            .put("repo", "https://github.com/$repo").put("autoDeploy", "yes")
        body.put("serviceDetails", if (website) JSONObject().put("buildCommand", "echo ok").put("publishPath", ".")
            else JSONObject().put("runtime", "node").put("plan", "free").put("region", "oregon")
                .put("envSpecificDetails", JSONObject().put("buildCommand", "npm install").put("startCommand", "npm start")))
        val (c, b) = post("/services", key, body)
        if (c !in 200..299) return null to errorOf(c, b) + if (c == 400 || c == 401 || c == 403 || c == 404) " (does Render have access to the repo?)" else ""
        val o = runCatching { JSONObject(b).optJSONObject("service") }.getOrNull() ?: return null to "Render answered in an unexpected way"
        return Svc(o.optString("id"), o.optString("name"), o.optString("type"), false, o.optJSONObject("serviceDetails")?.optString("url").orEmpty()) to ""
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
