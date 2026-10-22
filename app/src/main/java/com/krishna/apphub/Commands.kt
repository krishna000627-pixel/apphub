package com.krishna.apphub

object Commands {
    fun num(v: String): String = Regex("(\\d+)$").find(v)?.value ?: ""
    fun fileBase(p: Project) = p.name.filter { it.isLetterOrDigit() }
    fun bump(v: String): String = Regex("(\\d+)$").find(v)?.let { v.substring(0, it.range.first) + (it.value.toInt() + 1) } ?: v
}

object Prompts {
    val tasks = listOf("Fix error", "Add feature", "Improve UI", "Refactor", "Release notes", "Other")

    fun delivery(p: Project): String {
        val next = Commands.bump(p.version)
        val base = Commands.fileBase(p)
        return "\n\nDelivery rules: zip the whole project as ${base}_$next.zip with a top-level folder ${base}_$next/. " +
            "Always bump the version to $next (versionCode and versionName), even for a string-only change."
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
