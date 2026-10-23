package com.krishna.apphub

import org.json.JSONArray
import org.json.JSONObject

/**
 * v27 feature 2: backup file (JSON). Holds the app list and the stored signing keystore of each repo.
 * The keystores are secrets: the UI warns before exporting and asks before importing over existing data.
 */
object Backup {
    class AppEntry(val name: String, val repo: String, val pkg: String, val version: String, val stack: String)
    class Data(val apps: List<AppEntry>, val keys: Map<String, String>)

    private const val MAGIC = "apphub-backup"

    fun toJson(apps: List<Project>, keysOf: (String) -> String): String {
        val o = JSONObject()
        o.put("type", MAGIC); o.put("format", 1); o.put("created", System.currentTimeMillis())
        val a = JSONArray()
        apps.forEach { p ->
            a.put(JSONObject().put("name", p.name).put("repo", p.repo).put("package", p.packageName)
                .put("version", p.version).put("stack", p.stack))
        }
        o.put("apps", a)
        val k = JSONObject()
        apps.forEach { p -> val v = keysOf(p.repo); if (v.isNotBlank()) k.put(p.repo, v) }
        o.put("keystores", k)
        return o.toString(2)
    }

    /** null = not an App Hub backup. */
    fun parse(text: String): Data? = try {
        val o = JSONObject(text)
        if (o.optString("type") != MAGIC) null else {
            val a = o.optJSONArray("apps") ?: JSONArray()
            val apps = (0 until a.length()).mapNotNull { i ->
                val j = a.getJSONObject(i)
                val name = j.optString("name").trim(); val repo = j.optString("repo").trim()
                if (name.isBlank() || repo.isBlank()) null
                else AppEntry(name, repo, j.optString("package"), j.optString("version").ifBlank { "v1" }, j.optString("stack").ifBlank { "Kotlin • Compose" })
            }
            val keys: Map<String, String> = o.optJSONObject("keystores")?.let { kk ->
                kk.keys().asSequence().associateWith { name -> kk.optString(name) }.filterValues { it.isNotBlank() }
            } ?: emptyMap()
            Data(apps, keys)
        }
    } catch (e: Exception) { null }
}
