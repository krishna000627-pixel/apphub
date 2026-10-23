package com.krishna.apphub

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * v39: Vercel REST API for websites and servers. Login is a personal access token (vercel.com/account/tokens),
 * kept in the encrypted Prefs. A project is created from the GitHub repo, so every push to main deploys by itself.
 */
class Vercel(private val prefs: Prefs) {
    data class Linked(val id: String, val name: String, val url: String)
    /** [created] = millis on Vercel's clock, [msg] = commit message of the push that started it. */
    data class Dep(val id: String, val state: String, val created: Long, val msg: String)
    /** [ok] = false when Vercel could not be asked (offline, bad token); [dep] = null when the project has no deployment yet. */
    data class Latest(val ok: Boolean, val dep: Dep? = null)

    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    private suspend fun call(
        method: String, path: String, body: JSONObject? = null, query: Map<String, String> = emptyMap(),
        token: String = prefs.vercelToken, team: String = prefs.vercelTeam
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        try {
            val u = "https://api.vercel.com$path".toHttpUrl().newBuilder()
            query.forEach { (k, v) -> u.addQueryParameter(k, v) }
            if (team.isNotBlank()) u.addQueryParameter("teamId", team)
            val rb = body?.toString()?.toRequestBody("application/json".toMediaType()) ?: if (method == "GET") null else "".toRequestBody()
            val req = Request.Builder().url(u.build()).header("Authorization", "Bearer $token").header("Accept", "application/json").method(method, rb).build()
            http.newCall(req).execute().use { it.code to (it.body?.string() ?: "") }
        } catch (e: Exception) { 0 to (e.message ?: "network error") }
    }

    private fun errorOf(code: Int, body: String): String {
        if (code == 0) return "no network"
        val msg = runCatching { JSONObject(body).optJSONObject("error")?.optString("message").orEmpty() }.getOrDefault("")
        val text = msg.ifBlank { "HTTP $code" }
        // the usual cause when a repo cannot be linked: Vercel has no GitHub access yet
        val hint = if (code in 400..403 && Regex("github|integration|repo", RegexOption.IGNORE_CASE).containsMatchIn(text))
            " Connect GitHub in Vercel (Account Settings, Authentication) and give it access to this repo." else ""
        return text + hint
    }

    /** Checks a token (and team). Returns the account name, or null and the reason. */
    suspend fun verify(token: String, team: String): Pair<String?, String> {
        val (c, b) = call("GET", "/v2/user", token = token, team = team)
        if (c != 200) return null to errorOf(c, b)
        val u = runCatching { JSONObject(b).optJSONObject("user") }.getOrNull()
        val name = u?.optString("username").orEmpty().ifBlank { u?.optString("name").orEmpty() }.ifBlank { u?.optString("email").orEmpty() }
        return name.ifBlank { "Vercel user" } to ""
    }

    /** Creates the Vercel project for [repo] (owner/name) linked to GitHub, or reuses one with the same name. */
    suspend fun link(repo: String): Pair<Linked?, String> {
        val name = projectName(repo.substringAfter("/"))
        val git = JSONObject().put("type", "github").put("repo", repo)
        var (c, b) = call("POST", "/v11/projects", JSONObject().put("name", name).put("gitRepository", git))
        if (c == 409) {   // the name is taken: use that project, and connect it to the repo if it is not connected yet
            val r = call("GET", "/v9/projects/$name"); c = r.first; b = r.second
            if (c == 200) {
                val l = runCatching { JSONObject(b).optJSONObject("link") }.getOrNull()
                val have = l?.let { "${it.optString("org")}/${it.optString("repo")}" }.orEmpty()
                val id = JSONObject(b).optString("id")
                if (have.isBlank() || have == "/") {
                    val k = call("POST", "/v9/projects/$id/link", git)
                    if (k.first !in 200..299) return null to errorOf(k.first, k.second)
                } else if (!have.equals(repo, ignoreCase = true)) return null to "Vercel project $name is already linked to $have"
            }
        }
        if (c !in 200..299) return null to errorOf(c, b)
        val id = JSONObject(b).optString("id")
        if (id.isBlank()) return null to "Vercel gave no project id"
        return Linked(id, name, domainOf(id, name)) to ""
    }

    /** The project's *.vercel.app address (Vercel adds it by itself); falls back to name.vercel.app. */
    private suspend fun domainOf(id: String, name: String): String {
        val (c, b) = call("GET", "/v9/projects/$id/domains")
        val found = if (c == 200) runCatching {
            val a = JSONObject(b).optJSONArray("domains") ?: JSONArray()
            (0 until a.length()).map { a.getJSONObject(it) }
                .filter { it.optString("redirect").isBlank() && it.optString("name").endsWith(".vercel.app") }
                .minByOrNull { it.optString("name").length }?.optString("name")
        }.getOrNull() else null
        return "https://${found ?: "$name.vercel.app"}"
    }

    /** Newest production deployment of the project. */
    suspend fun latest(projectId: String): Latest {
        val (c, b) = call("GET", "/v6/deployments", query = mapOf("projectId" to projectId, "limit" to "1", "target" to "production"))
        if (c != 200) return Latest(false)
        return runCatching {
            val a = JSONObject(b).optJSONArray("deployments")
            val o = if (a != null && a.length() > 0) a.getJSONObject(0) else null
            Latest(true, o?.let {
                Dep(it.optString("uid"), it.optString("state").ifBlank { it.optString("readyState") }, it.optLong("created", it.optLong("createdAt")),
                    it.optJSONObject("meta")?.optString("githubCommitMessage").orEmpty())
            })
        }.getOrDefault(Latest(false))
    }

    /** Last error lines of a failed deployment (build log), or Vercel's own error message. */
    suspend fun errorText(id: String): String {
        val (c, b) = call("GET", "/v3/deployments/$id/events", query = mapOf("limit" to "300", "direction" to "backward", "builds" to "1"))
        val lines = if (c == 200) eventLines(b) else emptyList()
        if (lines.isNotEmpty()) return lines.joinToString("\n")
        val (c2, b2) = call("GET", "/v13/deployments/$id")
        val m = if (c2 == 200) runCatching { JSONObject(b2).optString("errorMessage") }.getOrDefault("") else ""
        return m.ifBlank { "Vercel build failed. Open the deployment on Vercel for the log." }
    }

    private fun eventLines(body: String): List<String> {
        val a = runCatching { JSONArray(body) }.getOrNull() ?: return emptyList()
        val ansi = Regex("\u001B\\[[0-9;]*m")
        val all = (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val t = ansi.replace(o.optString("text").ifBlank { o.optJSONObject("payload")?.optString("text").orEmpty() }, "").trim()
            if (t.isBlank()) null else o.optString("type") to t
        }.reversed()   // newest first -> oldest first
        val errs = all.filter { it.first == "stderr" || it.first == "error" || it.second.contains("error", ignoreCase = true) }.map { it.second }
        return errs.ifEmpty { all.map { it.second } }.takeLast(25)
    }

    companion object {
        /** Deployment state in the words of Project.lastStatus. null = ignore (cancelled, unknown). */
        fun statusOf(state: String): String? = when (state.uppercase()) {
            "READY" -> "passed"
            "ERROR" -> "failed"
            "BUILDING", "QUEUED", "INITIALIZING" -> "running"
            else -> null
        }

        /** Vercel project names: lowercase letters, digits, . _ - (no runs of dashes), at most 100 characters. */
        fun projectName(s: String): String =
            s.lowercase().replace(Regex("[^a-z0-9._-]"), "-").replace(Regex("-{2,}"), "-").trim('-', '.', '_').take(100).ifBlank { "project" }
    }
}
