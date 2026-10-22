package com.krishna.apphub

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import android.util.Base64
import org.json.JSONArray
import java.io.File
import java.util.concurrent.TimeUnit

private fun JSONObject.s(k: String): String = if (isNull(k)) "" else optString(k, "")

class GitHub(private val prefs: Prefs) {
    data class Run(val id: Long, val number: Int, val ok: Boolean?, val time: String, val msg: String, val event: String = "")
    data class Info(val desc: String, val lang: String, val branch: String, val sizeKb: Int, val issues: Int, val priv: Boolean, val pushed: String, val stars: Int)
    data class Commit(val sha: String, val msg: String, val author: String, val time: String)
    data class Release(val tag: String, val time: String, val notes: String, val sizeKb: Long)
    data class Step(val name: String, val ok: Boolean?)
    data class Job(val name: String, val ok: Boolean?, val steps: List<Step>)
    data class Rel(val tag: String, val body: String, val assetUrl: String)

    private val http = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).writeTimeout(120, TimeUnit.SECONDS).build()
    private val api = "https://api.github.com"

    private fun rb(url: String) = Request.Builder().url(url)
        .header("Accept", "application/vnd.github+json")
        .header("Authorization", "Bearer ${prefs.token}")

    private suspend fun exec(r: Request) = withContext(Dispatchers.IO) {
        try { http.newCall(r).execute().use { it.code to (it.body?.string() ?: "") } }
        catch (e: Exception) { 0 to (e.message ?: "network error") }
    }

    private suspend fun get(path: String) = exec(rb(api + path).build())
    private suspend fun send(method: String, path: String, body: JSONObject) =
        exec(rb(api + path).method(method, body.toString().toRequestBody("application/json".toMediaType())).build())

    // ---- OAuth device flow (no server, no client secret) ----
    suspend fun deviceCode(): JSONObject {
        val r = Request.Builder().url("https://github.com/login/device/code").header("Accept", "application/json")
            .post(FormBody.Builder().add("client_id", prefs.clientId).add("scope", "repo workflow").build()).build()
        return runCatching { JSONObject(exec(r).second) }.getOrDefault(JSONObject())
    }

    suspend fun pollToken(deviceCode: String, interval: Int): Boolean {
        var wait = interval
        while (true) {
            delay(wait * 1000L)
            val r = Request.Builder().url("https://github.com/login/oauth/access_token").header("Accept", "application/json")
                .post(FormBody.Builder().add("client_id", prefs.clientId).add("device_code", deviceCode)
                    .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code").build()).build()
            val j = runCatching { JSONObject(exec(r).second) }.getOrDefault(JSONObject())
            when {
                j.has("access_token") -> { prefs.token = j.getString("access_token"); return true }
                j.optString("error") == "slow_down" -> wait += 5
                j.optString("error") == "authorization_pending" -> {}
                else -> return false
            }
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

    suspend fun runs(repo: String): List<Run> {
        val (c, b) = get("/repos/$repo/actions/runs?per_page=8")
        if (c != 200) return emptyList()
        val a = JSONObject(b).getJSONArray("workflow_runs")
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Run(
                o.getLong("id"), o.getInt("run_number"),
                if (o.isNull("conclusion")) null else o.getString("conclusion") == "success",
                o.getString("created_at").replace("T", " ").take(16),
                o.optJSONObject("head_commit")?.optString("message")?.lineSequence()?.first() ?: "",
                o.optString("event")
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
        val (c, b) = get("/repos/$repo/releases?per_page=6"); if (c != 200) return emptyList()
        val a = JSONArray(b)
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Release(o.s("tag_name"), o.s("published_at").replace("T", " ").take(16), o.s("body"),
                (o.optJSONArray("assets")?.optJSONObject(0)?.optLong("size") ?: 0L) / 1024)
        }
    }

    suspend fun jobs(repo: String, runId: Long): List<Job> {
        val (c, b) = get("/repos/$repo/actions/runs/$runId/jobs"); if (c != 200) return emptyList()
        val a = JSONObject(b).getJSONArray("jobs")
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            val st = o.optJSONArray("steps") ?: JSONArray()
            Job(o.s("name"), ok(o.s("conclusion")), (0 until st.length()).map { i -> st.getJSONObject(i).let { s -> Step(s.s("name"), ok(s.s("conclusion"))) } })
        }
    }

    suspend fun exists(repo: String, path: String) = get("/repos/$repo/contents/$path").first == 200

    // ---- push a whole set of files as ONE commit (Git Data API, no git or Termux needed) ----
    suspend fun pushFiles(repo: String, files: List<Pair<String, ByteArray>>, message: String, log: (String) -> Unit): Boolean {
        val (c0, b0) = get("/repos/$repo")
        if (c0 != 200) { log("✗ Cannot open $repo ($c0). Check login and repo name."); return false }
        val branch = JSONObject(b0).s("default_branch").ifBlank { "main" }
        var ref = get("/repos/$repo/git/ref/heads/$branch")
        if (ref.first != 200) {
            log("• empty repo, creating first commit")
            val init = send("PUT", "/repos/$repo/contents/.gitkeep", JSONObject().put("message", "init").put("content", "Cg=="))
            if (init.first != 200 && init.first != 201) { log("✗ init failed (${init.first})"); return false }
            ref = get("/repos/$repo/git/ref/heads/$branch")
            if (ref.first != 200) { log("✗ branch not found"); return false }
        }
        val parent = JSONObject(ref.second).getJSONObject("object").getString("sha")
        val (c2, b2) = get("/repos/$repo/git/commits/$parent")
        if (c2 != 200) { log("✗ cannot read last commit ($c2)"); return false }
        val baseTree = JSONObject(b2).getJSONObject("tree").getString("sha")
        val entries = JSONArray()
        for ((i, f) in files.withIndex()) {
            val (path, bytes) = f
            val (c, b) = send("POST", "/repos/$repo/git/blobs",
                JSONObject().put("content", Base64.encodeToString(bytes, Base64.NO_WRAP)).put("encoding", "base64"))
            if (c != 201) { log("✗ upload failed: $path ($c)"); return false }
            entries.put(JSONObject().put("path", path).put("mode", if (path.endsWith(".sh") || path == "gradlew") "100755" else "100644")
                .put("type", "blob").put("sha", JSONObject(b).getString("sha")))
            if ((i + 1) % 10 == 0 || i + 1 == files.size) log("  uploaded ${i + 1}/${files.size}")
        }
        val (c3, b3) = send("POST", "/repos/$repo/git/trees", JSONObject().put("base_tree", baseTree).put("tree", entries))
        if (c3 != 201) { log("✗ tree failed ($c3)"); return false }
        val (c4, b4) = send("POST", "/repos/$repo/git/commits",
            JSONObject().put("message", message).put("tree", JSONObject(b3).getString("sha")).put("parents", JSONArray().put(parent)))
        if (c4 != 201) { log("✗ commit failed ($c4)"); return false }
        val c5 = send("PATCH", "/repos/$repo/git/refs/heads/$branch", JSONObject().put("sha", JSONObject(b4).getString("sha"))).first
        if (c5 != 200) { log("✗ branch update failed ($c5)"); return false }
        return true
    }

    // ---- install / update ----
    suspend fun latest(repo: String): Rel? {
        val (c, b) = get("/repos/$repo/releases/latest")
        if (c != 200) return null
        val j = JSONObject(b)
        val assets = j.getJSONArray("assets")
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.getString("name").endsWith(".apk")) return Rel(j.getString("tag_name"), j.optString("body"), a.getString("url"))
        }
        return null
    }

    suspend fun download(url: String, dest: File, onProgress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        val r = rb(url).header("Accept", "application/octet-stream").build()
        http.newCall(r).execute().use { resp ->
            val body = resp.body ?: return@use
            val total = body.contentLength()
            body.byteStream().use { i -> dest.outputStream().use { o ->
                val buf = ByteArray(16384); var done = 0L
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
