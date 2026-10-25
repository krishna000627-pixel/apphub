package com.krishna.apphub

object Commands {
    fun num(v: String): String = Regex("(\\d+)$").find(v)?.value ?: ""
    fun fileBase(p: Project) = p.name.filter { it.isLetterOrDigit() }
    fun bump(v: String): String = Regex("(\\d+)$").find(v)?.let { v.substring(0, it.range.first) + (it.value.toInt() + 1) } ?: v
}

object Prompts {
    /** v68: asked in every delivery rule. App Hub reads this file from the zip for the release notes and never pushes it. */
    fun whatsNewRule(v: String) = "Also put a file named whatsnew.txt at the top level of the project folder: 3 to 6 short lines, each starting with \"- \", in plain words, " +
        "saying what is new for the user in $v. App Hub reads it for the release notes and never pushes it to GitHub."

    /** v51: previews go in a file inside the zip. Drawing them in the chat fills the context window. */
    const val PREVIEW = "Put every preview (logo, avatars, themes, screens) in a NEW file in the zip, for example preview.html, and never draw it in the chat. Do not add it to the app until I approve it."

    /** v41: tasks per project type. A server has no UI, so it gets API wording instead of UI/UX. */
    fun tasksFor(kind: ProjectType): List<String> = when (kind) {
        ProjectType.SERVER -> listOf("Fix error", "Add feature", "Improve API", "Refactor", "Release notes", "Other")
        else -> listOf("Fix error", "Add feature", "Improve UI", "Refactor", "Release notes", "Other")
    }

    fun ideasPrompt(cat: String, notes: String, kind: ProjectType = ProjectType.APP): String {
        val what = when (kind) {
            ProjectType.APP -> "a new Android app in the \"$cat\" area"
            ProjectType.WEBSITE -> "a new website"
            ProjectType.SERVER -> "a new server (an API or a bot backend)"
        }
        val parts = when (kind) { ProjectType.APP -> "the 3 main screens"; ProjectType.WEBSITE -> "the 3 main pages"; ProjectType.SERVER -> "the main endpoints" }
        return "I want to build $what." + (if (notes.isBlank()) "" else " My interests and must-haves: $notes.") +
            "\n\nDo NOT write any code yet.\n\nStep 1 - Ideas: suggest 8 distinct ideas. For each give: a name, a one-line pitch, $parts, " +
            "the one feature that makes it special, and effort (small / medium / large).\n\nThen stop and ask me to pick one (or mix two). " +
            "Only after I choose: ask up to 3 short questions, show a one-screen plan, and wait for my OK before building. " +
            PREVIEW
    }

    /** v65: stack and host choices of the new project wizard (apps have fixed ones). */
    fun stacksFor(kind: ProjectType): List<String> = when (kind) {
        ProjectType.WEBSITE -> listOf("HTML + CSS + JS", "React + Vite", "Next.js")
        ProjectType.SERVER -> listOf("Node + Express", "Python + FastAPI", "Go")
        ProjectType.APP -> emptyList()
    }
    fun hostsFor(kind: ProjectType): List<String> = when (kind) {
        ProjectType.WEBSITE -> listOf("GitHub Pages", "Vercel")
        ProjectType.SERVER -> listOf("Render", "Vercel", "Docker")
        ProjectType.APP -> emptyList()
    }

    /** v65: build prompt for a new website or server. (A new app uses [buildPrompt].) */
    fun newProject(kind: ProjectType, name: String, pitch: String, stack: String, host: String): String {
        val base = name.filter { it.isLetterOrDigit() }.ifBlank { "NewProject" }
        val site = kind == ProjectType.WEBSITE
        val hostText = when (host) {
            "GitHub Pages" -> "GitHub Pages: add .github/workflows/deploy.yml that publishes the site on every push to main (Pages source = GitHub Actions)."
            "Vercel" -> if (site) "Vercel: it deploys from the GitHub repo on every push to main. Add a vercel.json only if it is needed."
                else "Vercel: serverless functions under /api, deployed from the GitHub repo on every push to main."
            "Render" -> "Render: it deploys from the GitHub repo on every push to main and must listen on the PORT environment variable. Say which build and start commands to enter."
            else -> "Docker: a Dockerfile that runs the server on the PORT environment variable, and a GitHub Actions workflow that builds the image."
        }
        val needs = if (site) "Responsive, accessible, clean dark UI with compact spacing. Show the version in the site footer."
            else "GET /health answers 200 with {\"status\":\"ok\",\"version\":\"v1\"}. Clear JSON errors and input validation. Configuration only through environment variables (list them in the README, no secrets in the repo)."
        return "Build a new ${kind.label.lowercase()} called $name: $pitch\n\nStack: $stack.\n\n$needs\n\nHosting: $hostText\n\n" +
            "Before coding: ask me up to 3 short questions if anything is unclear, then show a short plan and wait for my OK.\n\n" +
            (if (site) "$PREVIEW\n\n" else "") +
            "Delivery: zip the whole project as ${base}_v1.zip with a top-level folder ${base}_v1/. Version v1. " +
            "On every later change always bump the version, even for a string-only change. After the zip, list every file you created (path + one short note). ${whatsNewRule("v1")}"
    }

    /** v65: tasks of the per-app prompt page: the project tasks plus Adopt (before Other). */
    fun appTasks(kind: ProjectType): List<String> = tasksFor(kind).toMutableList().also { it.add(it.size - 1, "Adopt") }

    fun buildPrompt(name: String, pitch: String): String {
        val base = name.filter { it.isLetterOrDigit() }.ifBlank { "NewApp" }
        return "Build a new Android app called $name: $pitch\n\n" +
            "Stack: Kotlin, Jetpack Compose (Material 3), Room for local data, minSdk 26. Clean dark UI with compact headers and no wasted padding.\n\n" +
            "Before coding: ask me up to 3 short questions if anything is unclear, then show a short plan and wait for my OK.\n\n" +
            "CI: add a GitHub Actions workflow that builds a signed release APK (secrets KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD) " +
            "and publishes it as a release asset.\n\n" +
            PREVIEW + "\n\n" +
            "Delivery: zip the whole project as ${base}_v1.zip with a top-level folder ${base}_v1/. Version v1 (versionCode 1). " +
            "On every later change always bump the version, even for a string-only change. ${whatsNewRule("v1")}"
    }

    /** v49: sent to Claude together with the project, so an existing project works with App Hub (workflow, version, secrets, delivery). */
    fun adopt(p: Project): String {
        val head = "Adopt my existing ${p.kind.label.lowercase()} project ${p.name} (${p.version}) for App Hub.\n\nRepo: ${p.repo}\nStack: ${p.stack}\n\n" +
            "I will send you the whole project. Do NOT change how it looks or behaves. Only make it work with App Hub, then list what you changed.\n\nMake sure:\n"
        val rules = if (p.kind == ProjectType.APP)
            "1. .github/workflows/build.yml builds a signed release APK on every push to main and publishes it as a release asset (tag build-<run number>). " +
            "It signs with the secrets KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD (decode the keystore in the workflow; app/build.gradle reads them from env vars or keystore.properties; no keystore file in the repo).\n" +
            "2. applicationId stays the same. versionCode and versionName are plain values in app/build.gradle(.kts), so App Hub can read and bump them.\n" +
            "3. No secrets, .env files or keystores in the zip.\n" +
            "4. minSdk 26 or higher and a launcher icon exists."
        else
            "1. A workflow in .github/workflows deploys on every push to main (or tell me if Vercel or Render should deploy it from the repo instead).\n" +
            "2. The version is shown ${if (p.kind == ProjectType.WEBSITE) "in the site footer" else "by GET /health, which answers 200 with {\"status\":\"ok\",\"version\":\"<version>\"}"} and matches package.json if there is one.\n" +
            "3. No secrets, .env files or node_modules in the zip. List the environment variables I must set.\n" +
            "4. The README says how to run and deploy it."
        return head + rules + "\n\nBefore coding: ask me up to 3 short questions if anything is unclear. Please keep the solution simple and production ready." + delivery(p)
    }

    fun delivery(p: Project): String {
        val next = Commands.bump(p.version)
        val base = Commands.fileBase(p)
        if (p.kind != ProjectType.APP) return "\n\nDelivery rules: zip the whole project as ${base}_$next.zip with a top-level folder ${base}_$next/. " +
            "Always bump the version to $next (in package.json if there is one, and where it is shown: " +
            "${if (p.kind == ProjectType.WEBSITE) "the site footer" else "the /health response"}), even for a string-only change. " +
            "After the zip, list every file you created or changed (path + one short note). ${whatsNewRule(next)} $PREVIEW"
        return "\n\nDelivery rules: zip the whole project as ${base}_$next.zip with a top-level folder ${base}_$next/. " +
            "Always bump the version to $next (versionCode and versionName), even for a string-only change. " +
            "After the zip, list every file you created or changed (path + one short note). ${whatsNewRule(next)} $PREVIEW"
    }

    /** What the text box under step 4 asks for, per task. */
    fun hint(task: String, kind: ProjectType = ProjectType.APP) = when (task) {
        "Fix error" -> "Extra notes (optional). The latest build error is added automatically."
        "Add feature" -> "Describe the feature you want"
        "Improve UI" -> "Which screens or problems should be better?"
        "Improve API" -> "Which endpoints or problems should be better?"
        "Refactor" -> "What should be cleaned up?"
        "Release notes" -> "List what changed"
        else -> "Describe what you want done"
    }

    /** [vercel] = the project deploys on Vercel (websites / servers only). */
    fun make(task: String, p: Project, stack: String, error: String, deliver: Boolean = false, change: String = "", vercel: Boolean = false): String {
        if (task == "Adopt") return adopt(p)   // v65: already ends with the delivery rules
        return (if (p.kind == ProjectType.APP) body(task, p, stack, error, change.trim()) else webBody(task, p, stack, error, change.trim(), vercel)) +
            if (deliver) delivery(p) else ""
    }

    /** Prompts for an existing website or server. */
    private fun webBody(task: String, p: Project, stack: String, error: String, change: String, vercel: Boolean): String {
        val site = p.kind == ProjectType.WEBSITE
        val noun = if (site) "website" else "server"
        val host = if (vercel) " It is hosted on Vercel and deploys from GitHub on every push to main." else ""
        val keep = if (site) "" else " Keep existing routes working unless I say otherwise, and keep GET /health answering 200."
        val tail = "Please keep the solution simple and production ready."
        fun fill(placeholder: String) = change.ifBlank { placeholder }
        return when (task) {
            "Improve UI" -> "Improve the design and UX of my website ${p.name} (${p.version}).$host\n\nFocus:\n${fill("<pages or problems to improve>")}\n\n" +
                "Project stack: $stack.\n\nKeep the existing style, make it responsive and accessible. Before large visual changes: $PREVIEW $tail"
            "Improve API" -> "Improve the API of my server ${p.name} (${p.version}).$host\n\nFocus:\n${fill("<endpoints or problems to improve>")}\n\n" +
                "Project stack: $stack.\n\nImprove validation, error responses and consistency.$keep $tail"
            "Fix error" -> "Fix the following ${if (vercel) "Vercel build or deploy" else "build"} error in my $noun ${p.name} (${p.version}).$host\n\nError:\n" +
                "${error.ifBlank { "<paste error here>" }}\n\n" + (if (change.isBlank()) "" else "Extra notes:\n$change\n\n") + "Project stack: $stack.\n\n" +
                "Please analyze the error, explain the cause, and provide the exact code changes with updated files.$keep $tail"
            "Add feature" -> "Add a new feature to my $noun ${p.name} (${p.version}).$host\n\nFeature:\n${fill("<describe the feature>")}\n\n" +
                "Project stack: $stack.\n\nProvide complete updated files and any new dependencies or environment variables.$keep $tail"
            "Refactor" -> "Refactor my $noun ${p.name} (${p.version}).$host\n\nTarget:\n${fill("<describe what to clean up>")}\n\n" +
                "Project stack: $stack.\n\nKeep behavior identical and list every file you change.$keep $tail"
            "Release notes" -> "Write short, user-friendly release notes for ${p.name} ${p.version}.\n\nChanges:\n${fill("<list changes>")}\n\nUse 3-5 bullets."
            else -> "Project: ${p.name} (${p.version}), a $noun.$host\nStack: $stack\n\nTask:\n${fill("<describe your task>")}\n\n$tail"
        }
    }

    private fun body(task: String, p: Project, stack: String, error: String, change: String): String {
        val tail = "Please keep the solution simple and production ready."
        fun fill(placeholder: String) = change.ifBlank { placeholder }
        return when (task) {
            "Improve UI" -> "Improve the UI and UX of my Android project ${p.name} (${p.version}).\n\nFocus:\n${fill("<screens or problems to improve>")}\n\n" +
                "Project stack: $stack.\n\nKeep the existing dark green/cyan style, reduce wasted padding. Before large visual changes: $PREVIEW $tail"
            "Fix error" -> "Fix the following build error in my Android project ${p.name} (${p.version}).\n\nError:\n" +
                "${error.ifBlank { "<paste error here>" }}\n\n" + (if (change.isBlank()) "" else "Extra notes:\n$change\n\n") + "Project stack: $stack.\n\nPlease analyze the error, explain the cause, " +
                "and provide the exact code changes with updated files. $tail"
            "Add feature" -> "Add a new feature to my Android project ${p.name} (${p.version}).\n\nFeature:\n${fill("<describe the feature>")}\n\n" +
                "Project stack: $stack.\n\nProvide complete updated files and any new dependencies. $tail"
            "Refactor" -> "Refactor my Android project ${p.name} (${p.version}).\n\nTarget:\n${fill("<describe what to clean up>")}\n\n" +
                "Project stack: $stack.\n\nKeep behavior identical and list every file you change. $tail"
            "Release notes" -> "Write short, user-friendly release notes for ${p.name} ${p.version}.\n\nChanges:\n${fill("<list changes>")}\n\nUse 3-5 bullets."
            else -> "Project: ${p.name} (${p.version})\nStack: $stack\n\nTask:\n${fill("<describe your task>")}\n\n$tail"
        }
    }
}

object Ideas {
    val web: List<Pair<String, String>> = listOf(
        "Portfolio" to "Personal portfolio with projects, about and contact",
        "LinkPage" to "Link-in-bio page with buttons and a clean theme",
        "Landing" to "One-page product landing with features and signup",
        "DocsSite" to "Documentation site with search and a side menu",
        "BlogLite" to "Small blog from markdown files",
        "MenuCard" to "Restaurant menu with prices and a call button")
    val server: List<Pair<String, String>> = listOf(
        "Notes API" to "REST API for notes with tags and search",
        "ShortLink" to "URL shortener with click counts",
        "Webhook Relay" to "Receives webhooks and forwards them to Telegram",
        "BotBack" to "Backend for a Telegram bot with commands and storage",
        "FileDrop" to "Upload API with size limits and expiring links",
        "StatusPing" to "Pings a list of sites and reports their status")
    val cats = listOf("Productivity", "Games", "Tools", "Health", "Money", "Learning", "Creative")
    val list: Map<String, List<Pair<String, String>>> = mapOf(
        "Productivity" to listOf(
            "QuickTasks" to "Swipe-to-finish to-do list with daily streaks",
            "FocusBlock" to "Pomodoro timer with focus stats and ambient sounds",
            "NoteStack" to "Offline notes with tags, pinned cards and search",
            "HabitGrid" to "Habit tracker with a heat-map calendar",
            "DayPlan" to "Time-boxed daily planner with drag to reorder",
            "ShopSync" to "Shopping list with check-off history and quick re-add"),
        "Games" to listOf(
            "WordRush" to "Daily word puzzle with streaks and share card",
            "TapQuest" to "Idle clicker with upgrades and offline earnings",
            "MemoFlip" to "Memory card game with themes and timed modes",
            "BlockDrop" to "Casual block-stacking puzzle with combos",
            "MathDash" to "Quick mental-maths sprints with levels",
            "Dungeon Dice" to "Dice-roll dungeon crawler with loot and bosses"),
        "Tools" to listOf(
            "UnitFlip" to "Fast unit and currency converter with favourites",
            "QRKit" to "Scan, make and save QR codes offline",
            "FileScout" to "Storage analyser that finds big and duplicate files",
            "ClipVault" to "Clipboard history with pinned snippets",
            "Pocket Ruler" to "Camera ruler and level using phone sensors",
            "SplitBill" to "Split bills and share who owes what"),
        "Health" to listOf(
            "WaterLog" to "Hydration tracker with reminders and a daily goal ring",
            "SleepNote" to "Bedtime log with sleep score and trends",
            "StepStreak" to "Walking goals with weekly challenges",
            "MoodMap" to "One-tap mood diary with monthly patterns",
            "StretchTime" to "Guided desk-stretch breaks on a timer",
            "MealPlan" to "Weekly meal planner with a shopping list"),
        "Money" to listOf(
            "SpendLite" to "Expense tracker with categories and monthly charts",
            "BudgetJar" to "Envelope budgeting with simple jars",
            "SubWatch" to "Track subscriptions and renewal dates",
            "GoalSaver" to "Savings goals with progress and projections",
            "BillBell" to "Bill reminders with a due-date calendar",
            "Loan Calc" to "Loan and EMI calculator with payoff schedule"),
        "Learning" to listOf(
            "FlashPad" to "Spaced-repetition flashcards with decks",
            "LangBite" to "Daily 5-minute vocabulary practice",
            "QuizMaker" to "Create and play your own quizzes",
            "CodeSnips" to "Offline cheat sheets for programming languages",
            "ReadTrack" to "Reading log with goals and book notes",
            "TypeCoach" to "Typing speed trainer with lessons"),
        "Creative" to listOf(
            "PixelPad" to "Pixel-art drawing with palettes and export",
            "BeatBox" to "Simple drum-pad beat maker",
            "StoryDice" to "Random story prompts for writers",
            "ColorLab" to "Palette generator with copy-ready hex codes",
            "MemeKit" to "Quick meme maker with templates",
            "Sketch Notes" to "Handwritten notes with pen and highlighter")
    )
}
