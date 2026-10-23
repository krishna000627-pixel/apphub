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
 * v43: Supabase connection. Login is a personal access token (supabase.com/dashboard/account/tokens), kept in the encrypted Prefs.
 * App Hub reads the project list (Management API). v45: it can also create ONE project and test the legacy API keys of it.
 * Keys are used in memory for the test only, never saved or shown in full.
 */
class SupabaseApi(private val prefs: Prefs) {
    /** [status] is Supabase's own word, for example ACTIVE_HEALTHY or INACTIVE (paused). */
    data class Proj(val id: String, val name: String, val region: String, val status: String)

    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(40, TimeUnit.SECONDS).build()

    /** Your projects, or null and the reason. [token] is passed while logging in, otherwise the saved one is used. */
    suspend fun projects(token: String = prefs.supabaseToken): Pair<List<Proj>?, String> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("https://api.supabase.com/v1/projects")
                .header("Authorization", "Bearer $token").header("Accept", "application/json").get().build()
            http.newCall(req).execute().use { r ->
                val body = r.body?.string().orEmpty()
                if (r.code != 200) return@use null to errorOf(r.code, body)
                val a = runCatching { JSONArray(body) }.getOrNull() ?: return@use null to "Supabase answered in an unexpected way"
                val list = (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map {
                    Proj(it.optString("id"), it.optString("name"), it.optString("region"), it.optString("status"))
                }.filter { it.id.isNotBlank() }.sortedBy { it.name.lowercase() }
                list to ""
            }
        } catch (e: Exception) { null to (e.message ?: "no network") }
    }

    private suspend fun call(method: String, url: String, token: String, body: JSONObject? = null, headers: Map<String, String> = emptyMap()): Pair<Int, String> = withContext(Dispatchers.IO) {
        try {
            val b = Request.Builder().url(url).header("Accept", "application/json")
            if (token.isNotBlank()) b.header("Authorization", "Bearer $token")
            headers.forEach { (k, v) -> b.header(k, v) }
            if (body != null) b.method(method, body.toString().toRequestBody("application/json".toMediaType())) else b.method(method, null)
            http.newCall(b.build()).execute().use { it.code to (it.body?.string() ?: "") }
        } catch (e: Exception) { 0 to (e.message ?: "no network") }
    }

    /** First organization of the account: (id, slug), or null and the reason. */
    suspend fun org(token: String = prefs.supabaseToken): Pair<Pair<String, String>?, String> {
        val (c, b) = call("GET", "https://api.supabase.com/v1/organizations", token)
        if (c != 200) return null to errorOf(c, b)
        val o = runCatching { JSONArray(b).optJSONObject(0) }.getOrNull() ?: return null to "no organization on this account"
        val id = o.optString("id"); val slug = o.optString("slug").ifBlank { id }
        return (id to slug) to ""
    }

    /** Creates a project (smart region, americas). Returns it, or null and the reason. The database password is random and not kept. */
    suspend fun create(name: String, token: String = prefs.supabaseToken): Pair<Proj?, String> {
        val (o, oerr) = org(token)
        if (o == null) return null to oerr
        val chars = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val rnd = java.security.SecureRandom()
        val pass = (1..28).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
        val body = JSONObject().put("name", name).put("organization_slug", o.second).put("db_pass", pass)
            .put("region_selection", JSONObject().put("type", "smartGroup").put("code", "americas"))
        var (c, b) = call("POST", "https://api.supabase.com/v1/projects", token, body)
        if (c == 400 || c == 422) {   // older accounts: organization_id and a fixed region
            val alt = JSONObject().put("name", name).put("organization_id", o.first).put("db_pass", pass).put("region", "us-east-1")
            val r = call("POST", "https://api.supabase.com/v1/projects", token, alt); c = r.first; b = r.second
        }
        if (c !in 200..299) return null to errorOf(c, b)
        val j = runCatching { JSONObject(b) }.getOrNull() ?: return null to "Supabase answered in an unexpected way"
        val ref = j.optString("ref").ifBlank { j.optString("id") }
        return Proj(ref, j.optString("name", name), j.optString("region"), j.optString("status")) to ""
    }

    /** Status word of one project (COMING_UP, ACTIVE_HEALTHY, ...), "" when unknown. */
    suspend fun status(ref: String, token: String = prefs.supabaseToken): String {
        val (c, b) = call("GET", "https://api.supabase.com/v1/projects/$ref", token)
        return if (c == 200) runCatching { JSONObject(b).optString("status") }.getOrDefault("") else ""
    }

    /** Result of the legacy key test. Key values are masked. */
    data class Legacy(val enabled: String, val anon: String, val service: String, val anonCode: Int, val serviceCode: Int, val error: String)

    private fun mask(k: String) = if (k.length < 12) "" else k.take(6) + "…" + k.takeLast(4)

    /** Reads the legacy (anon / service_role) keys of a project and tries each one on its REST endpoint. */
    suspend fun legacyTest(ref: String, token: String = prefs.supabaseToken): Legacy {
        val (lc, lb) = call("GET", "https://api.supabase.com/v1/projects/$ref/api-keys/legacy", token)
        val enabled = if (lc == 200) runCatching { if (JSONObject(lb).optBoolean("enabled")) "on" else "off" }.getOrDefault("?") else "?"
        val (c, b) = call("GET", "https://api.supabase.com/v1/projects/$ref/api-keys?reveal=true", token)
        if (c != 200) return Legacy(enabled, "", "", 0, 0, errorOf(c, b))
        val a = runCatching { JSONArray(b) }.getOrNull() ?: return Legacy(enabled, "", "", 0, 0, "Supabase answered in an unexpected way")
        var anon = ""; var svc = ""
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val n = o.optString("name"); val k = o.optString("api_key")
            if (n == "anon") anon = k else if (n == "service_role") svc = k
        }
        if (anon.isBlank() && svc.isBlank()) return Legacy(enabled, "", "", 0, 0, "no legacy keys on this project")
        suspend fun hit(k: String) = if (k.isBlank()) 0 else call("GET", "https://$ref.supabase.co/rest/v1/", "", null, mapOf("apikey" to k, "Authorization" to "Bearer $k")).first
        return Legacy(enabled, mask(anon), mask(svc), hit(anon), hit(svc), "")
    }

    private fun errorOf(code: Int, body: String): String {
        val msg = runCatching { JSONObject(body).optString("message") }.getOrDefault("")
        return when {
            code == 401 || code == 403 -> "token rejected" + if (msg.isNotBlank()) " ($msg)" else ""
            msg.isNotBlank() -> msg
            else -> "HTTP $code"
        }
    }
}
