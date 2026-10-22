package com.krishna.apphub

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Last known GitHub data per app (build runs, latest release tag, repo info, commits, releases),
 * kept as a small JSON file in the app cache so screens open instantly and only go online when the data is stale.
 */
object SnapCache {
    class Snap(
        val runs: List<GitHub.Run> = emptyList(),
        val latest: String = "",
        val error: String = "",
        val info: GitHub.Info? = null,
        val commits: List<GitHub.Commit> = emptyList(),
        val releases: List<GitHub.Release> = emptyList()
    )

    private fun JSONObject.str(k: String) = optString(k, "")

    fun save(dir: File, id: Long, s: Snap) {
        try {
            dir.mkdirs()
            val o = JSONObject()
            o.put("latest", s.latest)
            o.put("error", s.error)
            val runs = JSONArray()
            s.runs.forEach { r ->
                val j = JSONObject()
                j.put("id", r.id); j.put("n", r.number); j.put("t", r.time); j.put("m", r.msg); j.put("e", r.event)
                if (r.ok != null) j.put("ok", r.ok)
                runs.put(j)
            }
            o.put("runs", runs)
            s.info?.let { i ->
                val j = JSONObject()
                j.put("desc", i.desc); j.put("lang", i.lang); j.put("branch", i.branch); j.put("size", i.sizeKb)
                j.put("issues", i.issues); j.put("priv", i.priv); j.put("pushed", i.pushed); j.put("stars", i.stars)
                o.put("info", j)
            }
            val cs = JSONArray()
            s.commits.forEach { c ->
                val j = JSONObject()
                j.put("sha", c.sha); j.put("m", c.msg); j.put("a", c.author); j.put("t", c.time)
                cs.put(j)
            }
            o.put("commits", cs)
            val rs = JSONArray()
            s.releases.forEach { r ->
                val j = JSONObject()
                j.put("tag", r.tag); j.put("t", r.time); j.put("notes", r.notes); j.put("kb", r.sizeKb)
                rs.put(j)
            }
            o.put("releases", rs)
            File(dir, "$id.json").writeText(o.toString())
        } catch (_: Exception) { }
    }

    fun load(dir: File, id: Long): Snap? {
        return try {
            val f = File(dir, "$id.json")
            if (!f.exists()) return null
            val o = JSONObject(f.readText())
            val runs = o.optJSONArray("runs") ?: JSONArray()
            val cs = o.optJSONArray("commits") ?: JSONArray()
            val rs = o.optJSONArray("releases") ?: JSONArray()
            Snap(
                runs = (0 until runs.length()).map {
                    val j = runs.getJSONObject(it)
                    GitHub.Run(j.getLong("id"), j.optInt("n"), if (j.has("ok")) j.getBoolean("ok") else null, j.str("t"), j.str("m"), j.str("e"))
                },
                latest = o.str("latest"),
                error = o.str("error"),
                info = o.optJSONObject("info")?.let { j ->
                    GitHub.Info(j.str("desc"), j.str("lang"), j.str("branch"), j.optInt("size"), j.optInt("issues"),
                        j.optBoolean("priv"), j.str("pushed"), j.optInt("stars"))
                },
                commits = (0 until cs.length()).map {
                    val j = cs.getJSONObject(it)
                    GitHub.Commit(j.str("sha"), j.str("m"), j.str("a"), j.str("t"))
                },
                releases = (0 until rs.length()).map {
                    val j = rs.getJSONObject(it)
                    GitHub.Release(j.str("tag"), j.str("t"), j.str("notes"), j.optLong("kb"))
                }
            )
        } catch (_: Exception) { null }
    }

    fun drop(dir: File, id: Long) { File(dir, "$id.json").delete() }
}
