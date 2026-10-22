package com.krishna.apphub

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.util.zip.ZipInputStream

/** Reads a project zip chosen with the system file picker (no storage permission needed). */
object Zipper {
    class Entry(val path: String, val bytes: ByteArray)
    class Pack(val name: String, val version: String, val entries: List<Entry>, val skipped: Int, val bytes: Long)

    private val skipDirs = listOf(".git/", "build/", ".gradle/", "__MACOSX/", ".idea/")

    private fun skip(path: String, size: Int): Boolean =
        path.isBlank() || path.contains("../") || skipDirs.any { path.startsWith(it) } || path.endsWith(".jks") ||
            path.endsWith(".DS_Store") || path == "keystore.properties" || path == "local.properties" || size > 50_000_000

    fun read(ctx: Context, uri: Uri): Pack? {
        val name = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "app.zip"
        val raw = ArrayList<Pair<String, ByteArray>>()
        val ins = ctx.contentResolver.openInputStream(uri) ?: return null
        ZipInputStream(ins).use { z ->
            var e = z.nextEntry
            while (e != null) {
                if (!e.isDirectory) raw.add(e.name.replace('\\', '/') to z.readBytes())
                e = z.nextEntry
            }
        }
        if (raw.isEmpty()) return null
        val tops = raw.map { it.first.substringBefore('/', "") }.toSet()
        val strip = tops.size == 1 && raw.all { it.first.contains('/') }
        var skipped = 0
        val out = ArrayList<Entry>()
        for ((p0, bytes) in raw) {
            val path = if (strip) p0.substringAfter('/') else p0
            if (skip(path, bytes.size)) { skipped++; continue }
            out.add(Entry(path, bytes))
        }
        val ver = Regex("_(v\\d+)\\.zip$", RegexOption.IGNORE_CASE).find(name)?.groupValues?.get(1) ?: ""
        return Pack(name, ver, out, skipped, out.sumOf { it.bytes.size.toLong() })
    }
}
