package com.krishna.apphub

import java.io.BufferedOutputStream
import java.io.OutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/**
 * v73: the git protocol side of "Git pack" push. Builds the objects of one push (new blobs, the changed trees, one commit) and writes
 * them as a packfile, the same thing `git push` sends. No delta search (that is the slow part of git on a phone), no base64, one request.
 * Only objects that are new or changed are sent; unchanged files and folders are referenced by their sha, as git does.
 */
object GitPack {
    class Obj(val type: Int, val bytes: ByteArray, val sha: String, val store: Boolean)   // type: 1 commit, 2 tree, 3 blob. store = do not compress
    class Remote(val path: String, val mode: String, val type: String, val sha: String)    // one line of the repo's recursive tree listing
    class Built(val objects: List<Obj>, val commit: String, val rootTree: String, val blobShas: Map<String, String>)

    private class Dir {
        val files = HashMap<String, Pair<String, String>>()   // name -> (mode, sha)
        val dirs = HashMap<String, Dir>()
        var sha: String? = null                                // known tree sha while nothing below changed
    }

    private val noCompress = setOf("png", "jpg", "jpeg", "gif", "webp", "mp3", "mp4", "mov", "mkv", "ogg", "zip", "apk", "aab", "jar", "gz", "7z", "rar", "epub", "pdf", "woff2")

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun unhex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    fun hash(type: String, bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update("$type ${bytes.size}\u0000".toByteArray())
        md.update(bytes)
        return hex(md.digest())
    }

    private fun cmp(a: ByteArray, b: ByteArray): Int {
        val n = minOf(a.size, b.size)
        for (i in 0 until n) { val d = (a[i].toInt() and 255) - (b[i].toInt() and 255); if (d != 0) return d }
        return a.size - b.size
    }

    private fun entry(mode: String, name: String, sha: String): ByteArray = "$mode $name".toByteArray() + byteArrayOf(0) + unhex(sha)

    private fun dirAt(root: Dir, parts: List<String>): Dir {
        var d = root
        for (p in parts) d = d.dirs.getOrPut(p) { Dir() }
        return d
    }

    private fun empty(d: Dir): Boolean = d.files.isEmpty() && d.dirs.values.all { empty(it) }

    private fun treeOf(d: Dir, out: MutableList<Obj>): String {
        d.sha?.let { return it }
        val ents = ArrayList<Pair<ByteArray, ByteArray>>()   // (sort key, entry): git sorts a folder as if its name ended with "/"
        for ((name, ms) in d.files) ents.add(name.toByteArray() to entry(ms.first, name, ms.second))
        for ((name, sub) in d.dirs) if (!empty(sub)) ents.add((name + "/").toByteArray() to entry("40000", name, treeOf(sub, out)))
        ents.sortWith { a, b -> cmp(a.first, b.first) }
        val content = ents.fold(ByteArray(0)) { acc, e -> acc + e.second }
        val sha = hash("tree", content)
        out.add(Obj(2, content, sha, false))
        d.sha = sha
        return sha
    }

    /** [remote] = the repo's current listing, [todo] = new and changed files (paths relative to the repo root). */
    fun build(remote: List<Remote>, todo: List<Pair<String, ByteArray>>, gone: Set<String>, parent: String, message: String, login: String): Built {
        val root = Dir()
        for (e in remote.sortedBy { it.path }) {
            val parts = e.path.split("/")
            when (e.type) {
                "tree" -> dirAt(root, parts).sha = e.sha
                else -> dirAt(root, parts.dropLast(1)).files[parts.last()] = e.mode.trimStart('0').ifBlank { "100644" }.let { m -> if (m == "40000") "100644" else m } to e.sha
            }
        }
        for (path in gone) {   // v74: removed files; everything above one is a new tree, and a folder left empty disappears
            val parts = path.split("/")
            var d = root
            d.sha = null
            for (p in parts.dropLast(1)) { d = d.dirs[p] ?: break; d.sha = null }
            d.files.remove(parts.last())
        }
        val objs = ArrayList<Obj>()
        val blobShas = HashMap<String, String>()
        for ((path, bytes) in todo) {
            val parts = path.split("/")
            var d = root
            d.sha = null
            for (p in parts.dropLast(1)) { d = d.dirs.getOrPut(p) { Dir() }; d.sha = null }   // everything above a changed file is new
            val sha = hash("blob", bytes)
            val mode = if (path.endsWith(".sh") || path == "gradlew") "100755" else "100644"
            d.files[parts.last()] = mode to sha
            blobShas[path] = sha
            objs.add(Obj(3, bytes, sha, path.substringAfterLast('.', "").lowercase() in noCompress))
        }
        val trees = ArrayList<Obj>()
        val rootTree = treeOf(root, trees)
        val now = System.currentTimeMillis() / 1000
        val who = "$login <$login@users.noreply.github.com> $now +0000"
        val commit = "tree $rootTree\nparent $parent\nauthor $who\ncommitter $who\n\n${message.trimEnd()}\n".toByteArray()
        val commitSha = hash("commit", commit)
        return Built(listOf(Obj(1, commit, commitSha, false)) + trees + objs, commitSha, rootTree, blobShas)
    }

    /** The packfile: "PACK", version 2, object count, every object zlib-compressed, then the SHA-1 of all of it. */
    fun writePack(objs: List<Obj>, out: OutputStream) {
        val md = MessageDigest.getInstance("SHA-1")
        val bo = BufferedOutputStream(DigestOutputStream(out, md), 64 * 1024)
        fun w32(n: Int) { bo.write(n ushr 24); bo.write(n ushr 16); bo.write(n ushr 8); bo.write(n) }
        bo.write("PACK".toByteArray()); w32(2); w32(objs.size)
        for (o in objs) {
            var size = o.bytes.size.toLong()
            var first = (o.type shl 4) or (size and 15).toInt()
            size = size ushr 4
            if (size > 0) first = first or 0x80
            bo.write(first)
            while (size > 0) {
                var b = (size and 0x7f).toInt()
                size = size ushr 7
                if (size > 0) b = b or 0x80
                bo.write(b)
            }
            val d = Deflater(if (o.store) Deflater.NO_COMPRESSION else Deflater.BEST_SPEED)
            val dos = DeflaterOutputStream(bo, d, 32 * 1024)
            dos.write(o.bytes)
            dos.finish()   // writes the rest of this object, does not close the pack
            d.end()
        }
        bo.flush()
        out.write(md.digest())
    }

    /** pkt-line payloads of a response (flush packets are skipped). */
    fun pkt(bytes: ByteArray): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i + 4 <= bytes.size) {
            val n = String(bytes, i, 4).toIntOrNull(16) ?: break
            if (n == 0) { i += 4; continue }
            if (n < 4 || i + n > bytes.size) break
            out.add(String(bytes, i + 4, n - 4, Charsets.UTF_8))
            i += n
        }
        return out
    }

    fun pktLine(s: String): ByteArray { val b = s.toByteArray(); return "%04x".format(b.size + 4).toByteArray() + b }
}
