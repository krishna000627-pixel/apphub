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
    /** [whatsNew] = text of whatsnew.txt from the zip root (v68). It is read here and never part of [entries], so it is never pushed. */
    class Pack(val name: String, val version: String, val entries: List<Entry>, val skipped: Int, val bytes: Long, val findings: List<Finding> = emptyList(), val whatsNew: String = "") {
        /** The same zip without these files (used by "Leave out & push"). */
        fun without(paths: Set<String>): Pack {
            val keep = entries.filter { it.path !in paths }
            return Pack(name, version, keep, skipped + (entries.size - keep.size), keep.sumOf { it.bytes.size.toLong() }, findings.filter { it.path !in paths }, whatsNew)
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
        var whats = ""
        for ((p0, bytes) in raw) {
            val path = if (strip) p0.substringAfter('/') else p0
            if (path.equals("whatsnew.txt", true) || path.equals("whatsnew.md", true)) { whats = String(bytes, Charsets.UTF_8).trim().take(4000); continue }   // v68: read, never pushed
            if (skip(path, bytes.size)) { skipped++; continue }
            out.add(Entry(path, bytes))
        }
        return Pack(name, versionOf(name), out, skipped, out.sumOf { it.bytes.size.toLong() }, SecretScan.scan(out), whats)
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

/** v49: a zip whose files do not fit the project type (the Deploy screen asks). [issues] without a path cannot be left out. */
class TypeAsk(val p: Project, val pack: Zipper.Pack, val note: String, val src: File?, val issues: List<Zipper.Finding>)

/** v49: file type checker. Looks at what is about to be pushed: does it fit an App / Website / Server, and is there junk (build outputs, archives, node_modules). */
object TypeCheck {
    private val junkExt = listOf(".apk", ".aab", ".zip", ".exe", ".dmg", ".iso", ".7z", ".rar")
    private val androidNames = listOf("androidmanifest.xml", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts")
    private val serverNames = listOf("package.json", "requirements.txt", "pyproject.toml", "dockerfile", "go.mod", "pom.xml", "cargo.toml", "gemfile", "composer.json", "vercel.json", "render.yaml")
    private val appCode = listOf(".kt", ".java", ".gradle", ".kts")
    private val webCode = listOf(".html", ".css", ".js", ".ts", ".jsx", ".tsx", ".vue", ".svelte")
    private val serverCode = listOf(".js", ".ts", ".py", ".go", ".rs", ".java", ".rb", ".php", ".cs")

    fun scan(kind: ProjectType, entries: List<Zipper.Entry>): List<Zipper.Finding> {
        val out = ArrayList<Zipper.Finding>()
        val paths = entries.map { it.path }
        val names = paths.map { it.substringAfterLast('/').lowercase() }
        fun anyExt(list: List<String>) = paths.any { p -> list.any { p.lowercase().endsWith(it) } }
        val android = names.any { it in androidNames }
        when (kind) {
            ProjectType.APP -> if (!android && !anyExt(appCode)) out.add(Zipper.Finding("", "No Android files (build.gradle, AndroidManifest.xml, .kt). This project is an App.", false))
            ProjectType.WEBSITE -> when {
                android -> out.add(Zipper.Finding("", "Looks like an Android project, not a website.", false))
                !anyExt(webCode) -> out.add(Zipper.Finding("", "No web files (index.html, .css, .js). This project is a Website.", false))
            }
            ProjectType.SERVER -> when {
                android -> out.add(Zipper.Finding("", "Looks like an Android project, not a server.", false))
                names.none { it in serverNames } && !anyExt(serverCode) -> out.add(Zipper.Finding("", "No server files (package.json, .py, Dockerfile). This project is a Server.", false))
            }
        }
        entries.filter { e -> junkExt.any { e.path.lowercase().endsWith(it) } }.take(8)
            .forEach { out.add(Zipper.Finding(it.path, "build output or archive", true)) }
        // v72: big files are the ones that fail to upload on a phone connection
        entries.filter { it.bytes.size > 4_000_000 }.sortedByDescending { it.bytes.size }.take(8).forEach { e ->
            if (out.none { f -> f.path == e.path }) out.add(Zipper.Finding(e.path, "big file, ${"%.1f".format(e.bytes.size / 1048576.0)} MB (large uploads can fail on a phone connection)", true))
        }
        val nm = paths.filter { it.startsWith("node_modules/") || it.contains("/node_modules/") }
        if (nm.isNotEmpty()) out.add(Zipper.Finding(nm.first().substringBefore("node_modules/") + "node_modules/", "${nm.size} dependency files (install on build instead)", true))
        return out
    }

    /** Paths of every file the "Leave out" choice drops (whole files, and whole folders such as node_modules/). */
    fun dropSet(issues: List<Zipper.Finding>, entries: List<Zipper.Entry>): Set<String> {
        val whole = issues.filter { it.wholeFile }
        return entries.map { it.path }.filter { path -> whole.any { path == it.path || (it.path.endsWith("/") && path.startsWith(it.path)) } }.toSet()
    }
}
