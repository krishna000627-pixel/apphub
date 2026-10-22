package com.krishna.apphub

import android.content.Context
import android.content.Intent

object Commands {
    data class Files(val workflow: String, val release: String, val setup: String)

    private fun rd(ctx: Context, n: String) = ctx.assets.open(n).bufferedReader().readText().trimEnd()
    fun files(ctx: Context) = Files(rd(ctx, "build.yml"), rd(ctx, "release.sh"), rd(ctx, "setup.sh"))

    fun num(v: String): String = Regex("(\\d+)$").find(v)?.value ?: ""
    fun fileBase(p: Project) = p.name.filter { it.isLetterOrDigit() }
    private fun initials(name: String): String {
        val caps = name.filter { it.isUpperCase() }.lowercase()
        return if (caps.length >= 2) caps.take(3) else name.filter { it.isLetterOrDigit() }.take(2).lowercase()
    }

    /** unzip ~/storage/downloads/Name_vN.zip into the project folder, then release (same shape as the QuestBlock command). */
    fun deploy(p: Project, version: String, msg: String): String {
        val base = fileBase(p)
        val z = "${base}_$version"
        val tmp = initials(p.name) + num(version)
        val dir = p.localPath.trimEnd('/').substringAfterLast('/').ifBlank { base }
        val m = msg.filterNot { it in "\"`\\\$" }
        return "cd ~ && unzip -oq ~/storage/downloads/$z.zip -d ~/$tmp && cp -r ~/$tmp/$z/. ~/$dir/ && " +
            "rm -rf ~/$tmp && cd ~/$dir && bash termux/release.sh \"$version: $m\""
    }

    /** Picks the newest Name_v*.zip in Downloads, unzips it over the project and releases it. No typing needed. */
    fun deployLatest(p: Project): String {
        val base = fileBase(p)
        val dir = p.localPath.trimEnd('/').substringAfterLast('/').ifBlank { base }
        return listOf(
            "Z=\$(ls -t ~/storage/downloads/${base}_v*.zip 2>/dev/null | head -1)",
            "[ -n \"\$Z\" ] || { echo 'No ${base}_v*.zip in Downloads'; exit 1; }",
            "B=\$(basename \"\$Z\" .zip)",
            "V=\${B##*_}",
            "rm -rf ~/ahtmp && unzip -oq \"\$Z\" -d ~/ahtmp && cp -r ~/ahtmp/\$B/. ~/$dir/ && rm -rf ~/ahtmp",
            "cd ~/$dir && bash termux/release.sh \"\$V: update from App Hub\""
        ).joinToString("\n")
    }

    /** New app from a zip: unzip newest Name_v*.zip into ~/Name, then create repo + keys, push, build, install. */
    fun setupFromZip(p: Project, token: String, f: Files): String {
        val base = fileBase(p)
        val dir = p.localPath.trimEnd('/').substringAfterLast('/').ifBlank { base }
        return listOf(
            if (token.isNotBlank()) "export GH_TOKEN='$token'" else "# export GH_TOKEN=<your GitHub token>",
            "Z=\$(ls -t ~/storage/downloads/${base}_v*.zip 2>/dev/null | head -1)",
            "[ -n \"\$Z\" ] || { echo 'No ${base}_v*.zip in Downloads'; exit 1; }",
            "rm -rf ~/ahtmp && unzip -oq \"\$Z\" -d ~/ahtmp",
            "S=\$(ls -d ~/ahtmp/*/ 2>/dev/null | head -1); [ -n \"\$S\" ] || S=~/ahtmp/",
            "mkdir -p ~/$dir && cp -r \"\$S.\" ~/$dir/ && rm -rf ~/ahtmp",
            "cd ~/$dir",
            bootstrap(f),
            "bash termux/setup.sh ${p.repo.substringAfter("/")}"
        ).joinToString("\n")
    }

    fun bump(v: String): String = Regex("(\\d+)$").find(v)?.let { v.substring(0, it.range.first) + (it.value.toInt() + 1) } ?: v

    private fun put(path: String, body: String, tag: String) =
        "mkdir -p \$(dirname $path)\n[ -f $path ] || cat > $path <<'$tag'\n$body\n$tag"

    /** Writes termux/setup.sh, termux/release.sh and the workflow into the project if they are missing. */
    private fun bootstrap(f: Files) = listOf(
        put("termux/release.sh", f.release, "APPHUB_R"),
        put("termux/setup.sh", f.setup, "APPHUB_S"),
        put(".github/workflows/build.yml", f.workflow, "APPHUB_W")
    ).joinToString("\n")

    /** [run]=true builds the executable version (token + bootstrap); false is the short display/copy version. */
    fun script(p: Project, version: String, setup: Boolean, build: Boolean, push: Boolean, release: Boolean, zip: Boolean,
               run: Boolean = false, token: String = "", files: Files? = null): String {
        val s = StringBuilder()
        if (setup || push || release)
            s.appendLine(if (run && token.isNotBlank()) "export GH_TOKEN='$token'" else "# export GH_TOKEN=<your GitHub token>")
        s.appendLine("cd \"${p.localPath}\"")
        if (run && (setup || push || release) && files != null) s.appendLine(bootstrap(files))
        if (setup) { s.appendLine("# Create repo + signing key + secrets"); s.appendLine("bash termux/setup.sh ${p.repo.substringAfter("/")}") }
        if (build) { s.appendLine("# Build APK locally"); s.appendLine("./gradlew assembleRelease") }
        if (push) { s.appendLine("# Commit, push, wait for CI, download + install APK"); s.appendLine("bash termux/release.sh \"Update to $version\"") }
        if (release) {
            s.appendLine("# Tag release (CI publishes the signed APK)")
            s.appendLine("gh auth setup-git; git tag -f $version; git push -f origin $version")
        }
        if (zip) { s.appendLine("# Create ZIP"); s.appendLine("zip -r ${p.name}_$version.zip app/") }
        return s.toString().trimEnd()
    }

    fun runInTermux(ctx: Context, script: String): Boolean = try {
        ctx.startForegroundService(Intent("com.termux.RUN_COMMAND")
            .setClassName("com.termux", "com.termux.app.RunCommandService")
            .putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            .putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", script))
            .putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            .putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
            .putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0"))
        true
    } catch (e: Exception) { false }
}

object Prompts {
    val tasks = listOf("Fix error", "Add feature", "Improve UI", "Refactor", "Release notes", "Other")

    fun delivery(p: Project): String {
        val next = Commands.bump(p.version)
        val base = Commands.fileBase(p)
        return "\n\nDelivery rules: zip the whole project as ${base}_$next.zip with a top-level folder ${base}_$next/. " +
            "Always bump the version to $next (versionCode and versionName), even for a string-only change. " +
            "Then give me this deploy command, with the summary filled in:\n" + Commands.deploy(p, next, "<summary>")
    }

    fun make(task: String, p: Project, stack: String, error: String, deliver: Boolean = false): String =
        body(task, p, stack, error) + if (deliver) delivery(p) else ""

    private fun body(task: String, p: Project, stack: String, error: String): String {
        val tail = "Please keep the solution simple and production ready."
        return when (task) {
            "Improve UI" -> "Improve the UI and UX of my Android project ${p.name} (${p.version}).\n\nFocus:\n<screens or problems to improve>\n\n" +
                "Project stack: $stack.\n\nKeep the existing dark green/cyan style, reduce wasted padding, and show a preview before large visual changes. $tail"
            "Fix error" -> "Fix the following build error in my Android project ${p.name} (${p.version}).\n\nError:\n" +
                "${error.ifBlank { "<paste error here>" }}\n\nProject stack: $stack.\n\nPlease analyze the error, explain the cause, " +
                "and provide the exact code changes with updated files. $tail"
            "Add feature" -> "Add a new feature to my Android project ${p.name} (${p.version}).\n\nFeature:\n<describe the feature>\n\n" +
                "Project stack: $stack.\n\nProvide complete updated files and any new dependencies. $tail"
            "Refactor" -> "Refactor my Android project ${p.name} (${p.version}).\n\nTarget:\n<describe what to clean up>\n\n" +
                "Project stack: $stack.\n\nKeep behavior identical and list every file you change. $tail"
            "Release notes" -> "Write short, user-friendly release notes for ${p.name} ${p.version}.\n\nChanges:\n<list changes>\n\nUse 3-5 bullets."
            else -> "Project: ${p.name} (${p.version})\nStack: $stack\n\nTask:\n<describe your task>\n\n$tail"
        }
    }
}
