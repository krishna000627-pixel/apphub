package com.krishna.apphub

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import android.util.Base64
import org.json.JSONArray
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.concurrent.TimeUnit

private fun JSONObject.s(k: String): String = if (isNull(k)) "" else optString(k, "")

class GitHub(private val prefs: Prefs) {
    data class Run(val id: Long, val number: Int, val ok: Boolean?, val time: String, val msg: String, val event: String = "", val cancelled: Boolean = false)
    data class Info(val desc: String, val lang: String, val branch: String, val sizeKb: Int, val issues: Int, val priv: Boolean, val pushed: String, val stars: Int)
    data class Commit(val sha: String, val msg: String, val author: String, val time: String)
    data class Release(val tag: String, val time: String, val notes: String, val sizeKb: Long)
    data class Step(val name: String, val ok: Boolean?)
    data class Job(val id: Long, val name: String, val ok: Boolean?, val steps: List<Step>)
    data class Rel(val tag: String, val body: String, val assetUrl: String, val time: String = "", val sizeKb: Long = 0L)

    // ---- token lifetime: GitHub App tokens last 8 hours. Refresh before they end, and again if GitHub answers 401. ----
    /** true once GitHub rejected the token and it could not be refreshed: the user has to log in again. */
    @Volatile var authLost = false
    private val refreshLock = Any()
    private val plain = OkHttpClient()   // no interceptors: used only to refresh the token

    private fun saveToken(j: JSONObject) {
        prefs.token = j.getString("access_token")
        prefs.refreshToken = j.optString("refresh_token", "")
        val ex = j.optLong("expires_in", 0L)
        prefs.tokenExpiry = if (ex > 0) System.currentTimeMillis() + ex * 1000L else 0L
        authLost = false
    }

    /** 1 = refreshed, 0 = network problem (try again later), -1 = GitHub rejected it (or there is no refresh token). */
    private fun refreshBlocking(): Int {
        val rt = prefs.refreshToken
        if (rt.isBlank()) return -1
        val fb = FormBody.Builder().add("client_id", prefs.clientId).add("grant_type", "refresh_token").add("refresh_token", rt)
        if (prefs.clientSecret.isNotBlank()) fb.add("client_secret", prefs.clientSecret)
        return try {
            plain.newCall(Request.Builder().url("https://github.com/login/oauth/access_token").header("Accept", "application/json").post(fb.build()).build())
                .execute().use { r ->
                    val j = JSONObject(r.body?.string() ?: "{}")
                    if (j.has("access_token")) { saveToken(j); 1 }
                    else { if (j.has("error")) { prefs.refreshToken = ""; -1 } else 0 }
                }
        } catch (e: Exception) { 0 }
    }

    private fun proactive(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val exp = prefs.tokenExpiry
        if (req.header("Authorization") != null && exp > 0 && prefs.refreshToken.isNotBlank() && System.currentTimeMillis() > exp - 300_000L) {
            synchronized(refreshLock) {
                if (System.currentTimeMillis() > prefs.tokenExpiry - 300_000L && refreshBlocking() < 0) authLost = true
            }
            return chain.proceed(req.newBuilder().header("Authorization", "Bearer ${prefs.token}").build())
        }
        return chain.proceed(req)
    }

    private fun reauth(resp: Response): Request? {
        if (resp.priorResponse != null) return null
        val sent = resp.request.header("Authorization")?.removePrefix("Bearer ") ?: return null
        synchronized(refreshLock) {
            val now = prefs.token
            if (now.isNotBlank() && now != sent) return resp.request.newBuilder().header("Authorization", "Bearer $now").build()   // already refreshed by another call
            return when (refreshBlocking()) {
                1 -> resp.request.newBuilder().header("Authorization", "Bearer ${prefs.token}").build()
                0 -> null                      // offline: the next poll tries again
                else -> { authLost = true; null }
            }
        }
    }

    private val http = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).writeTimeout(120, TimeUnit.SECONDS)
        .addInterceptor { chain -> proactive(chain) }
        .authenticator { _, response -> reauth(response) }
        .build()
    private val api = "https://api.github.com"

    private fun rb(url: String) = Request.Builder().url(url)
        .header("Accept", "application/vnd.github+json")
        .header("Authorization", "Bearer ${prefs.token}")

    private suspend fun exec(r: Request) = withContext(Dispatchers.IO) {
        try { http.newCall(r).execute().use { it.code to (it.body?.string() ?: "") } }
        catch (e: Exception) { 0 to (e.message ?: "network error") }
    }

    // GET with ETag: an unchanged answer is a 304 (free against the GitHub rate limit), so polling every few seconds is cheap.
    private val etags = java.util.concurrent.ConcurrentHashMap<String, Pair<String, String>>()

    /** v27: true while GitHub cannot be reached; limitUntil = when the API limit ends (millis, 0 = not limited). Both clear by themselves on the next good answer. */
    @Volatile var offline = false
    @Volatile var limitUntil = 0L

    private fun noteLimit(r: Response) {
        val left = r.header("X-RateLimit-Remaining")?.toIntOrNull()
        val reset = r.header("X-RateLimit-Reset")?.toLongOrNull()
        val retry = r.header("Retry-After")?.toLongOrNull()
        val now = System.currentTimeMillis()
        if ((r.code == 403 || r.code == 429) && (left == 0 || retry != null)) {
            val until = if (retry != null) now + retry * 1000L else (reset ?: 0L) * 1000L
            limitUntil = if (until > now) until else now + 60_000L
        } else if (r.code in 200..399) limitUntil = 0L
    }

    private suspend fun get(path: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        val cached = etags[path]
        val b = rb(api + path)
        if (cached != null) b.header("If-None-Match", cached.first)
        try {
            http.newCall(b.build()).execute().use { r ->
                offline = false; noteLimit(r)
                if (r.code == 304 && cached != null) 200 to cached.second
                else {
                    val body = r.body?.string() ?: ""
                    if (r.code == 200) r.header("ETag")?.let { etags[path] = it to body }
                    r.code to body
                }
            }
        } catch (e: Exception) { offline = true; 0 to (e.message ?: "network error") }
    }
    private suspend fun send(method: String, path: String, body: JSONObject) =
        exec(rb(api + path).method(method, body.toString().toRequestBody("application/json".toMediaType())).build())

    // ---- OAuth device flow (no server, no client secret) ----
    suspend fun deviceCode(): JSONObject {
        val r = Request.Builder().url("https://github.com/login/device/code").header("Accept", "application/json")
            .post(FormBody.Builder().add("client_id", prefs.clientId).add("scope", "repo workflow").build()).build()
        return runCatching { JSONObject(exec(r).second) }.getOrDefault(JSONObject())
    }

    /** Returns null when the token was saved, otherwise a readable error. */
    suspend fun pollToken(deviceCode: String, interval: Int, expiryMs: Long, immediate: Boolean = false): String? {
        var wait = maxOf(interval, 5)
        var first = immediate
        while (System.currentTimeMillis() < expiryMs) {
            if (!first) delay(wait * 1000L)
            first = false
            val r = Request.Builder().url("https://github.com/login/oauth/access_token").header("Accept", "application/json")
                .post(FormBody.Builder().add("client_id", prefs.clientId).add("device_code", deviceCode)
                    .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code").build()).build()
            val j = runCatching { JSONObject(exec(r).second) }.getOrNull() ?: continue // network blip: try again
            val err = j.optString("error")
            when {
                j.has("access_token") -> { saveToken(j); return null }
                err == "slow_down" -> wait = j.optInt("interval", wait + 5)
                err == "authorization_pending" -> {}
                err == "expired_token" -> return "Code expired, tap Login again"
                err == "access_denied" -> return "You denied access on GitHub"
                err == "device_flow_disabled" -> return "Enable Device Flow in your GitHub OAuth app settings"
                else -> return "GitHub: ${j.optString("error_description", err)}"
            }
        }
        return "Code expired, tap Login again"
    }

    // ---- OAuth web flow with callback apphub://oauth (PKCE; needs the client secret) ----
    fun webAuthUri(state: String, challenge: String): Uri = Uri.parse("https://github.com/login/oauth/authorize").buildUpon()
        .appendQueryParameter("client_id", prefs.clientId)
        .appendQueryParameter("redirect_uri", "apphub://oauth")
        .appendQueryParameter("scope", "repo workflow")
        .appendQueryParameter("state", state)
        .appendQueryParameter("code_challenge", challenge)
        .appendQueryParameter("code_challenge_method", "S256").build()

    /** Returns null when the token was saved, otherwise a readable error. */
    suspend fun exchangeCode(code: String, verifier: String): String? {
        val r = Request.Builder().url("https://github.com/login/oauth/access_token").header("Accept", "application/json")
            .post(FormBody.Builder().add("client_id", prefs.clientId).add("client_secret", prefs.clientSecret)
                .add("code", code).add("redirect_uri", "apphub://oauth").add("code_verifier", verifier).build()).build()
        val (c, b) = exec(r)
        val j = runCatching { JSONObject(b) }.getOrNull() ?: return "Network error ($c)"
        if (j.has("access_token")) { saveToken(j); return null }
        return when (val e = j.optString("error")) {
            "bad_verification_code" -> "Code already used or expired, tap Login again"
            "incorrect_client_credentials" -> "Client ID or client secret is wrong"
            "redirect_uri_mismatch" -> "Set the OAuth app callback URL to apphub://oauth"
            else -> "GitHub: ${j.optString("error_description", e)}"
        }
    }

    suspend fun user(): String? = get("/user").let { (c, b) -> if (c == 200) JSONObject(b).getString("login") else null }

    // ---- repo / secrets ----
    suspend fun createRepo(name: String) =
        send("POST", "/user/repos", JSONObject().put("name", name).put("private", true))

    suspend fun setSecret(repo: String, name: String, value: String): Boolean {
        val (c, b) = get("/repos/$repo/actions/secrets/public-key")
        if (c != 200) return false
        val j = JSONObject(b)
        val body = JSONObject().put("encrypted_value", Sodium.seal(j.getString("key"), value)).put("key_id", j.getString("key_id"))
        return send("PUT", "/repos/$repo/actions/secrets/$name", body).first in 200..204
    }

    // ---- builds ----
    suspend fun dispatch(repo: String) =
        send("POST", "/repos/$repo/actions/workflows/build.yml/dispatches", JSONObject().put("ref", "main")).first == 204

    suspend fun runs(repo: String, n: Int = 15): List<Run> {
        val (c, b) = get("/repos/$repo/actions/runs?per_page=$n")
        if (c != 200) return emptyList()
        val a = JSONObject(b).getJSONArray("workflow_runs")
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Run(
                o.getLong("id"), o.getInt("run_number"),
                if (o.isNull("conclusion")) null else o.getString("conclusion") == "success",
                o.getString("created_at").replace("T", " ").take(16),
                o.optJSONObject("head_commit")?.optString("message")?.lineSequence()?.first() ?: "",
                o.optString("event"),
                o.optString("conclusion") == "cancelled"
            )
        }
    }

    suspend fun errorOf(repo: String, runId: Long): String {
        val (c, b) = get("/repos/$repo/actions/runs/$runId/jobs")
        if (c != 200) return ""
        val jobs = JSONObject(b).getJSONArray("jobs")
        for (i in 0 until jobs.length()) {
            val j = jobs.getJSONObject(i)
            if (j.optString("conclusion") != "failure") continue
            val log = exec(rb("$api/repos/$repo/actions/jobs/${j.getLong("id")}/logs").build()).second
            val hits = log.lines().map { it.substringAfter("Z ") }.filter {
                it.startsWith("e: ") || it.contains("error:") || it.contains("What went wrong") || it.startsWith("FAILURE")
            }
            return hits.take(8).joinToString("\n").ifBlank { "Build failed — see the Actions log on GitHub." }
        }
        return ""
    }

    // ---- in-app repo info (no browser needed) ----
    private fun ok(c: String?): Boolean? = when (c) { null, "", "null" -> null; "success", "skipped" -> true; else -> false }

    suspend fun info(repo: String): Info? {
        val (c, b) = get("/repos/$repo"); if (c != 200) return null
        val j = JSONObject(b)
        return Info(j.s("description"), j.s("language"), j.s("default_branch"), j.optInt("size"), j.optInt("open_issues_count"),
            j.optBoolean("private"), j.s("pushed_at").replace("T", " ").take(16), j.optInt("stargazers_count"))
    }

    suspend fun commits(repo: String): List<Commit> {
        val (c, b) = get("/repos/$repo/commits?per_page=8"); if (c != 200) return emptyList()
        val a = JSONArray(b)
        return (0 until a.length()).map {
            val o = a.getJSONObject(it); val cm = o.getJSONObject("commit")
            Commit(o.s("sha").take(7), cm.s("message"), cm.optJSONObject("author")?.s("name") ?: "", (cm.optJSONObject("author")?.s("date") ?: "").replace("T", " ").take(16))
        }
    }

    suspend fun releases(repo: String): List<Release> {
        val (c, b) = get("/repos/$repo/releases?per_page=20"); if (c != 200) return emptyList()
        val a = JSONArray(b)
        return (0 until a.length()).map { a.getJSONObject(it) }.filter { !it.optBoolean("draft") }
            .sortedByDescending { it.s("published_at").ifBlank { it.s("created_at") } }.take(8).map { o ->
                Release(o.s("tag_name"), o.s("published_at").ifBlank { o.s("created_at") }.replace("T", " ").take(16), o.s("body"),
                    (o.optJSONArray("assets")?.optJSONObject(0)?.optLong("size") ?: 0L) / 1024)
            }
    }

    suspend fun jobs(repo: String, runId: Long): List<Job> {
        val (c, b) = get("/repos/$repo/actions/runs/$runId/jobs"); if (c != 200) return emptyList()
        val a = JSONObject(b).getJSONArray("jobs")
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            val st = o.optJSONArray("steps") ?: JSONArray()
            Job(o.optLong("id"), o.s("name"), ok(o.s("conclusion")), (0 until st.length()).map { i -> st.getJSONObject(i).let { s -> Step(s.s("name"), ok(s.s("conclusion"))) } })
        }
    }

    suspend fun jobLog(repo: String, jobId: Long): String {
        val (c, b) = exec(rb("$api/repos/$repo/actions/jobs/$jobId/logs").build())
        return if (c == 200) b else ""
    }

    suspend fun exists(repo: String, path: String) = get("/repos/$repo/contents/$path").first == 200

    data class RepoFile(val path: String, val size: Long)
    /** A file's content (null bytes + err when it could not be read). */
    class Blob(val bytes: ByteArray?, val size: Long = 0, val err: String = "")

    /** v28/v31: every file on the default branch with its size (blobs only), sorted by path. null = could not be read. */
    suspend fun files(repo: String): List<RepoFile>? {
        val (c0, b0) = get("/repos/$repo"); if (c0 != 200) return null
        val branch = JSONObject(b0).s("default_branch").ifBlank { "main" }
        val (c, b) = get("/repos/$repo/git/trees/$branch?recursive=1"); if (c != 200) return null
        val arr = JSONObject(b).optJSONArray("tree") ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }.filter { it.optString("type") == "blob" }
            .map { RepoFile(it.getString("path"), it.optLong("size")) }.sortedBy { it.path }
    }

    /** v31: one file's content through the contents API (GitHub returns it inline up to 1 MB). */
    suspend fun readBlob(repo: String, path: String): Blob {
        val enc = path.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        val (c, b) = get("/repos/$repo/contents/$enc")
        if (c == 404) return Blob(null, 0, "File not found. It may have been deleted.")
        if (c != 200) return Blob(null, 0, "Could not open the file ($c).")
        return try {
            val j = JSONObject(b)
            val size = j.optLong("size")
            val content = j.optString("content")
            if (j.optString("encoding") != "base64" || (content.isBlank() && size > 0)) Blob(null, size, "Too large to preview here (${size / 1024} KB). Open it on GitHub.")
            else Blob(Base64.decode(content, Base64.DEFAULT), size)
        } catch (e: Exception) { Blob(null, 0, "This is a folder or could not be read.") }
    }

    /**
     * v28: deletes files in ONE new commit (git trees API: a null sha removes the path), so history is kept and the
     * last push can still be undone. Returns (ok, message).
     */
    suspend fun deleteFiles(repo: String, paths: List<String>, message: String): Pair<Boolean, String> {
        if (paths.isEmpty()) return false to "Nothing to delete"
        val (c0, b0) = get("/repos/$repo"); if (c0 != 200) return false to "Cannot open $repo ($c0)"
        val branch = JSONObject(b0).s("default_branch").ifBlank { "main" }
        val ref = get("/repos/$repo/git/ref/heads/$branch"); if (ref.first != 200) return false to "Branch not found"
        val head = JSONObject(ref.second).getJSONObject("object").getString("sha")
        val (c1, b1) = get("/repos/$repo/git/commits/$head"); if (c1 != 200) return false to "Cannot read the last commit ($c1)"
        val baseTree = JSONObject(b1).getJSONObject("tree").getString("sha")
        val entries = JSONArray()
        paths.forEach { entries.put(JSONObject().put("path", it).put("mode", "100644").put("type", "blob").put("sha", JSONObject.NULL)) }
        val (c2, b2) = send("POST", "/repos/$repo/git/trees", JSONObject().put("base_tree", baseTree).put("tree", entries))
        if (c2 != 201) return false to "GitHub refused the delete ($c2)"
        val (c3, b3) = send("POST", "/repos/$repo/git/commits",
            JSONObject().put("message", message).put("tree", JSONObject(b2).getString("sha")).put("parents", JSONArray().put(head)))
        if (c3 != 201) return false to "Delete commit failed ($c3)"
        val c4 = send("PATCH", "/repos/$repo/git/refs/heads/$branch", JSONObject().put("sha", JSONObject(b3).getString("sha"))).first
        return if (c4 == 200) true to message else false to "Branch update failed ($c4)"
    }

    data class PushResult(val ok: Boolean, val committed: Boolean = false, val added: Int = 0, val changed: Int = 0, val same: Int = 0, val sha: String = "", val branch: String = "")

    /** The file as text if it is valid UTF-8 (so it can travel inline), else null. */
    private fun textOf(b: ByteArray): String? {
        if (b.isEmpty() || b.size > 1_000_000 || b.any { it == 0.toByte() }) return null
        return try {
            val t = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(b)).toString()
            if (t.toByteArray(Charsets.UTF_8).contentEquals(b)) t else null
        } catch (e: CharacterCodingException) { null }
    }

    /**
     * Undo the last push safely: adds a NEW commit that restores the previous tree (history is kept, CI builds it).
     * Returns (ok, message).
     */
    suspend fun revertLast(repo: String): Pair<Boolean, String> {
        val (c0, b0) = get("/repos/$repo"); if (c0 != 200) return false to "Cannot open $repo ($c0)"
        val branch = JSONObject(b0).s("default_branch").ifBlank { "main" }
        val ref = get("/repos/$repo/git/ref/heads/$branch"); if (ref.first != 200) return false to "Branch not found"
        val head = JSONObject(ref.second).getJSONObject("object").getString("sha")
        val (c1, b1) = get("/repos/$repo/git/commits/$head"); if (c1 != 200) return false to "Cannot read the last commit ($c1)"
        val hj = JSONObject(b1)
        val parents = hj.optJSONArray("parents") ?: JSONArray()
        if (parents.length() == 0) return false to "This is the first commit, nothing to go back to"
        val parent = parents.getJSONObject(0).getString("sha")
        val (c2, b2) = get("/repos/$repo/git/commits/$parent"); if (c2 != 200) return false to "Cannot read the previous commit ($c2)"
        val pj = JSONObject(b2)
        val pv = Regex("^v\\d+").find(pj.s("message"))?.value
        val first = hj.s("message").lineSequence().firstOrNull().orEmpty()
        val msg = if (pv != null) "$pv: revert \"$first\"" else "Revert \"$first\""
        val (c3, b3) = send("POST", "/repos/$repo/git/commits",
            JSONObject().put("message", msg).put("tree", pj.getJSONObject("tree").getString("sha")).put("parents", JSONArray().put(head)))
        if (c3 != 201) return false to "Revert commit failed ($c3)"
        val c4 = send("PATCH", "/repos/$repo/git/refs/heads/$branch", JSONObject().put("sha", JSONObject(b3).getString("sha"))).first
        return if (c4 == 200) true to msg else false to "Branch update failed ($c4)"
    }

    private fun gitSha(b: ByteArray): String {
        val md = java.security.MessageDigest.getInstance("SHA-1")
        md.update("blob ${b.size}\u0000".toByteArray()); md.update(b)
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Pushes only what changed: new files are created, changed files are updated, identical files are skipped
     * (compared by git blob hash against the repo's current tree). Result is ONE commit, or none if nothing changed.
     */
    suspend fun pushFiles(repo: String, files: List<Pair<String, ByteArray>>, message: String, dryRun: Boolean = false, log: (String) -> Unit): PushResult {
        val (c0, b0) = get("/repos/$repo")
        if (c0 != 200) { log("✗ Cannot open $repo ($c0). Check login and repo name."); return PushResult(false) }
        val branch = JSONObject(b0).s("default_branch").ifBlank { "main" }
        var ref = get("/repos/$repo/git/ref/heads/$branch")
        if (ref.first != 200 && dryRun) { log("• empty repo: all ${files.size} files would be new"); return PushResult(true, false, files.size, 0, 0) }
        if (ref.first != 200) {
            log("• empty repo, creating first commit")
            val init = send("PUT", "/repos/$repo/contents/.gitkeep", JSONObject().put("message", "init").put("content", "Cg=="))
            if (init.first != 200 && init.first != 201) { log("✗ init failed (${init.first})"); return PushResult(false) }
            ref = get("/repos/$repo/git/ref/heads/$branch")
            if (ref.first != 200) { log("✗ branch not found"); return PushResult(false) }
        }
        val parent = JSONObject(ref.second).getJSONObject("object").getString("sha")
        val (c2, b2) = get("/repos/$repo/git/commits/$parent")
        if (c2 != 200) { log("✗ cannot read last commit ($c2)"); return PushResult(false) }
        val baseTree = JSONObject(b2).getJSONObject("tree").getString("sha")

        // what the repo has right now
        val remote = HashMap<String, String>()
        val (c6, b6) = get("/repos/$repo/git/trees/$baseTree?recursive=1")
        var known = false
        if (c6 == 200) {
            val tj = JSONObject(b6)
            known = !tj.optBoolean("truncated")
            val arr = tj.optJSONArray("tree") ?: JSONArray()
            for (k in 0 until arr.length()) { val o = arr.getJSONObject(k); if (o.s("type") == "blob") remote[o.s("path")] = o.s("sha") }
        }
        if (!known) { remote.clear(); log("• could not compare with the repo, uploading everything") }

        val todo = ArrayList<Pair<String, ByteArray>>()
        val marks = ArrayList<String>()
        var added = 0; var changed = 0; var same = 0
        for (f in files.sortedBy { it.first }) {
            val r = remote[f.first]
            when {
                r == null -> { added++; todo.add(f); marks.add("  + ${f.first}") }
                r != gitSha(f.second) -> { changed++; todo.add(f); marks.add("  ~ ${f.first}") }
                else -> same++
            }
        }
        log("• $added new, $changed changed, $same unchanged (skipped)")
        marks.take(30).forEach { log(it) }
        if (marks.size > 30) log("  … and ${marks.size - 30} more")
        if (todo.isEmpty()) { log("✓ Nothing to push, the repo already has these files."); return PushResult(true, false, 0, 0, same) }
        if (dryRun) { log("• Preview only, nothing was pushed."); return PushResult(true, false, added, changed, same) }

        // Text files go inline in the tree request (no extra call each). Only binary or big files need a blob upload.
        var inlineBytes = 0
        val inline = HashMap<String, String>()
        for ((path, bytes) in todo) {
            val t = textOf(bytes)
            if (t != null && inlineBytes + bytes.size <= 6_000_000) { inline[path] = t; inlineBytes += bytes.size }
        }
        val blobFiles = todo.filter { it.first !in inline }
        val shas = HashMap<String, String>()
        if (blobFiles.isNotEmpty()) {
            val gate = Semaphore(8)
            var done = 0
            val res = coroutineScope {
                blobFiles.map { (path, bytes) ->
                    async {
                        gate.withPermit {
                            val (c, b) = send("POST", "/repos/$repo/git/blobs",
                                JSONObject().put("content", Base64.encodeToString(bytes, Base64.NO_WRAP)).put("encoding", "base64"))
                            if (c == 201) {
                                done++
                                if (done % 10 == 0 || done == blobFiles.size) log("  uploaded $done/${blobFiles.size}")
                                path to JSONObject(b).getString("sha")
                            } else path to null
                        }
                    }
                }.awaitAll()
            }
            val bad = res.firstOrNull { it.second == null }
            if (bad != null) { log("✗ upload failed: ${bad.first}"); return PushResult(false) }
            res.forEach { shas[it.first] = it.second!! }
        }
        log("• ${inline.size} text files sent inline, ${blobFiles.size} uploaded")
        val entries = JSONArray()
        for ((path, _) in todo) {
            val e = JSONObject().put("path", path).put("mode", if (path.endsWith(".sh") || path == "gradlew") "100755" else "100644").put("type", "blob")
            if (path in inline) e.put("content", inline[path]) else e.put("sha", shas[path])
            entries.put(e)
        }
        val (c3, b3) = send("POST", "/repos/$repo/git/trees", JSONObject().put("base_tree", baseTree).put("tree", entries))
        if (c3 != 201) { log("✗ tree failed ($c3)"); return PushResult(false) }
        val (c4, b4) = send("POST", "/repos/$repo/git/commits",
            JSONObject().put("message", message).put("tree", JSONObject(b3).getString("sha")).put("parents", JSONArray().put(parent)))
        if (c4 != 201) { log("✗ commit failed ($c4)"); return PushResult(false) }
        val newSha = JSONObject(b4).getString("sha")
        val c5 = send("PATCH", "/repos/$repo/git/refs/heads/$branch", JSONObject().put("sha", newSha)).first
        if (c5 != 200) { log("✗ branch update failed ($c5)"); return PushResult(false) }
        log("• commit ${newSha.take(7)} on $branch")
        return PushResult(true, true, added, changed, same, newSha, branch)
    }

    // ---- install / update ----
    /** Newest APK release by publish time. (/releases/latest orders by commit date and can keep returning a very old build.) */
    suspend fun latest(repo: String): Rel? {
        val (c, b) = get("/repos/$repo/releases?per_page=20")
        if (c != 200) return null
        val a = JSONArray(b)
        var best: Rel? = null; var bestKey = ""
        for (i in 0 until a.length()) {
            val j = a.getJSONObject(i)
            if (j.optBoolean("draft")) continue
            val assets = j.optJSONArray("assets") ?: continue
            for (k in 0 until assets.length()) {
                val x = assets.getJSONObject(k)
                if (!x.s("name").endsWith(".apk")) continue
                val t = j.s("published_at").ifBlank { j.s("created_at") }
                val better = best == null || t > bestKey ||
                    (t == bestKey && (Commands.num(j.s("tag_name")).toIntOrNull() ?: 0) > (Commands.num(best.tag).toIntOrNull() ?: 0))
                if (better) { best = Rel(j.s("tag_name"), j.s("body"), x.s("url"), t.replace("T", " ").take(16), x.optLong("size") / 1024); bestKey = t }
                break
            }
        }
        return best
    }

    /** applicationId of the app, read from app/build.gradle(.kts) in the repo. "" when not found. */
    suspend fun packageOf(repo: String): String {
        for (f in listOf("app/build.gradle.kts", "app/build.gradle")) {
            val (c, b) = get("/repos/$repo/contents/$f"); if (c != 200) continue
            val txt = runCatching { String(Base64.decode(JSONObject(b).optString("content").replace("\n", ""), Base64.DEFAULT)) }.getOrDefault("")
            Regex("applicationId\\s*=?\\s*\"([^\"]+)\"").find(txt)?.let { return it.groupValues[1] }
        }
        return ""
    }

    /** Downloads into dest. A partial file is resumed (HTTP Range); if the server ignores Range the file starts over. */
    suspend fun download(url: String, dest: File, onProgress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        val partial = if (dest.exists()) dest.length() else 0L
        val rq = rb(url).header("Accept", "application/octet-stream")
        if (partial > 0) rq.header("Range", "bytes=$partial-")
        http.newCall(rq.build()).execute().use { resp ->
            if (resp.code == 416) { dest.delete(); error("stale partial file, try again") }
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val body = resp.body ?: error("empty response")
            val append = resp.code == 206 && partial > 0
            val base = if (append) partial else 0L
            val len = body.contentLength()
            val total = if (len >= 0) base + len else -1L
            body.byteStream().use { i -> java.io.FileOutputStream(dest, append).use { o ->
                val buf = ByteArray(16384); var done = base
                onProgress(done, total)
                while (true) { val n = i.read(buf); if (n < 0) break; o.write(buf, 0, n); done += n; onProgress(done, total) }
            } }
        }
    }

    fun install(ctx: Context, f: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
