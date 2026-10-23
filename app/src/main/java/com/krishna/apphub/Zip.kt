package com.krishna.apphub

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Reads a project zip chosen with the system file picker (no storage permission needed). */
object Zipper {
    class Entry(val path: String, val bytes: ByteArray)
    /** v42: something that looks like a secret. [wholeFile] = the file itself is the secret (.env, key file), so it can be left out of the push. */
    class Finding(val path: String, val why: String, val wholeFile: Boolean)
    class Pack(val name: String, val version: String, val entries: List<Entry>, val skipped: Int, val bytes: Long, val findings: List<Finding> = emptyList()) {
        /** The same zip without these files (used by "Leave out & push"). */
        fun without(paths: Set<String>): Pack {
            val keep = entries.filter { it.path !in paths }
            return Pack(name, version, keep, skipped + (entries.size - keep.size), keep.sumOf { it.bytes.size.toLong() }, findings.filter { it.path !in paths })
        }
    }

    private val skipDirs = listOf(".git/", "build/", ".gradle/", "__MACOSX/", ".idea/")

    private fun skip(path: String, size: Int): Boolean =
        path.isBlank() || path.contains("../") || skipDirs.any { path.startsWith(it) } || path.endsWith(".jks") ||
            path.endsWith(".DS_Store") || path == "keystore.properties" || path == "local.properties" || size > 50_000_000

    fun read(ctx: Context, uri: Uri): Pack? {
        val name = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "app.zip"
        val ins = ctx.contentResolver.openInputStream(uri) ?: return null
        return ins.use { parse(name, it) }
    }

    fun read(f: File): Pack? = f.inputStream().use { parse(f.name, it) }

    fun versionOf(name: String): String = Regex("_(v\\d+)", RegexOption.IGNORE_CASE).find(name)?.groupValues?.get(1) ?: ""

    private fun parse(name: String, ins: InputStream): Pack? {
        val raw = ArrayList<Pair<String, ByteArray>>()
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
        return Pack(name, versionOf(name), out, skipped, out.sumOf { it.bytes.size.toLong() }, SecretScan.scan(out))
    }
}

/** v42: what App Hub asks about when a zip is pushed (the Deploy screen shows the dialog). */
class SecretAsk(val p: Project, val pack: Zipper.Pack, val note: String, val src: File?)

/** v42: looks through the files that are about to be pushed for keys, tokens and .env files. Nothing leaves the phone. */
object SecretScan {
    private val envFile = Regex("(^|/)\\.env(\\.[^/]+)?$", RegexOption.IGNORE_CASE)
    private val envSample = Regex("\\.(example|sample|template|dist|defaults)$", RegexOption.IGNORE_CASE)
    private val keyExt = listOf(".pem", ".p12", ".pfx", ".key", ".keystore", ".p8")
    private val keyNames = listOf("id_rsa", "id_ed25519", "id_ecdsa")
    private val skipContent = listOf("package-lock.json", "yarn.lock", "pnpm-lock.yaml", ".min.js", ".min.css", ".map")

    private val patterns = listOf(
        "private key" to Regex("-----BEGIN (RSA |EC |DSA |OPENSSH |ENCRYPTED |PGP )?PRIVATE KEY"),
        "GitHub token" to Regex("gh[pousr]_[A-Za-z0-9]{36,}|github_pat_[A-Za-z0-9_]{60,}"),
        "AWS access key" to Regex("AKIA[0-9A-Z]{16}"),
        "Stripe live key" to Regex("sk_live_[0-9a-zA-Z]{20,}"),
        "Slack token" to Regex("xox[abprs]-[0-9A-Za-z-]{10,}"),
        "Anthropic API key" to Regex("sk-ant-[A-Za-z0-9_-]{20,}"),
        "OpenAI-style API key" to Regex("sk-(proj-)?[A-Za-z0-9_-]{40,}")
    )

    fun scan(entries: List<Zipper.Entry>): List<Zipper.Finding> {
        val out = ArrayList<Zipper.Finding>()
        for (e in entries) {
            if (out.size >= 50) break
            val name = e.path.substringAfterLast('/')
            val lower = e.path.lowercase()
            val whole = when {
                envFile.containsMatchIn(e.path) && !envSample.containsMatchIn(e.path) -> "environment file"
                keyExt.any { lower.endsWith(it) } -> "key or certificate file"
                name in keyNames -> "private SSH key"
                Regex("service[-_]?account.*\\.json|.*-firebase-adminsdk-.*\\.json", RegexOption.IGNORE_CASE).matches(name) -> "service account key"
                else -> null
            }
            if (whole != null) { out.add(Zipper.Finding(e.path, whole, true)); continue }
            if (e.bytes.size > 400_000 || skipContent.any { lower.endsWith(it) }) continue
            if (e.bytes.take(512).any { it.toInt() == 0 }) continue   // binary
            val text = String(e.bytes, Charsets.UTF_8)
            patterns.firstOrNull { it.second.containsMatchIn(text) }?.let { out.add(Zipper.Finding(e.path, it.first + " inside the file", false)) }
        }
        return out
    }
}
