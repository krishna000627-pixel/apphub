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
import java.io.File

class GitHub(private val prefs: Prefs) {
    data class Run(val id: Long, val number: Int, val ok: Boolean?, val time: String, val msg: String)
    data class Rel(val tag: String, val body: String, val assetUrl: String)

    private val http = OkHttpClient()
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
                o.optJSONObject("head_commit")?.optString("message")?.lineSequence()?.first() ?: ""
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
