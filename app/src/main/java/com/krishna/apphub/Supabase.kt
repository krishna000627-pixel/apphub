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
 * App Hub reads the project list (Management API) and, from Setup > Deploy to, can create one project. It never touches your data or your database keys.
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

    private fun call(method: String, path: String, token: String, body: JSONObject? = null): Pair<Int, String> =
        try {
            val b = Request.Builder().url("https://api.supabase.com$path").header("Authorization", "Bearer $token").header("Accept", "application/json")
            val req = if (body == null) b.get().build() else b.method(method, body.toString().toRequestBody("application/json".toMediaType())).build()
            http.newCall(req).execute().use { it.code to it.body?.string().orEmpty() }
        } catch (e: Exception) { 0 to (e.message ?: "no network") }

    /**
     * v52: creates a project (region group americas) in your first organization. The database password is random and not kept.
     * Returns the project, or null and the reason. Run it off the main thread.
     */
    suspend fun create(name: String, token: String = prefs.supabaseToken): Pair<Proj?, String> = withContext(Dispatchers.IO) {
        val (oc, ob) = call("GET", "/v1/organizations", token)
        if (oc != 200) return@withContext null to errorOf(oc, ob)
        val org = runCatching { JSONArray(ob).optJSONObject(0) }.getOrNull() ?: return@withContext null to "no Supabase organization found"
        val chars = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val rnd = java.security.SecureRandom()
        val pass = (1..28).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
        var (c, b) = call("POST", "/v1/projects", token, JSONObject().put("name", name).put("organization_slug", org.optString("slug"))
            .put("db_pass", pass).put("region_selection", JSONObject().put("type", "smartGroup").put("code", "americas")))
        if (c == 400 || c == 422) {   // older shape of the same call
            val r = call("POST", "/v1/projects", token, JSONObject().put("name", name).put("organization_id", org.optString("id"))
                .put("db_pass", pass).put("region", "us-east-1").put("plan", "free"))
            c = r.first; b = r.second
        }
        if (c !in 200..299) return@withContext null to errorOf(c, b)
        val o = runCatching { JSONObject(b) }.getOrNull() ?: return@withContext null to "Supabase answered in an unexpected way"
        val id = o.optString("id").ifBlank { o.optString("ref") }
        if (id.isBlank()) null to "Supabase gave no project id" else Proj(id, o.optString("name", name), o.optString("region"), o.optString("status")) to ""
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
