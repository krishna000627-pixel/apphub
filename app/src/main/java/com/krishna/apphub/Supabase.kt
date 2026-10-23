package com.krishna.apphub

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * v43: Supabase connection. Login is a personal access token (supabase.com/dashboard/account/tokens), kept in the encrypted Prefs.
 * App Hub only reads the project list (Management API), it never touches your data or your database keys.
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

    private fun errorOf(code: Int, body: String): String {
        val msg = runCatching { JSONObject(body).optString("message") }.getOrDefault("")
        return when {
            code == 401 || code == 403 -> "token rejected" + if (msg.isNotBlank()) " ($msg)" else ""
            msg.isNotBlank() -> msg
            else -> "HTTP $code"
        }
    }
}
