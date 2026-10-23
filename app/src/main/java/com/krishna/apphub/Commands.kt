package com.krishna.apphub

object Commands {
    fun num(v: String): String = Regex("(\\d+)$").find(v)?.value ?: ""
    fun fileBase(p: Project) = p.name.filter { it.isLetterOrDigit() }
    fun bump(v: String): String = Regex("(\\d+)$").find(v)?.let { v.substring(0, it.range.first) + (it.value.toInt() + 1) } ?: v
}

object Prompts {
    val tasks = listOf("New app", "New website", "New server", "Fix error", "Add feature", "Improve UI", "Refactor", "Release notes", "Other")
    fun isNew(task: String) = task.startsWith("New ")
    /** "Improve UI" reads "Improve API" for a server (same task, other words). */
    fun label(task: String, kind: ProjectType) = if (task == "Improve UI" && kind == ProjectType.SERVER) "Improve API" else task

    // ---- v39: websites and servers ----
    val webStacks = listOf("HTML • CSS • JS", "Next.js", "React • Vite")
    val serverStacks = listOf("Node.js • Express", "Next.js API routes", "Python • FastAPI")

    private fun vercelRules(kind: ProjectType): String =
        "Hosting: Vercel, deployed from GitHub on every push to main. No GitHub Actions workflow is needed. " +
        (if (kind == ProjectType.WEBSITE)
            "Keep it deployable with zero config (static files or the framework default); add vercel.json only if it is really needed. "
        else
            "Structure it for Vercel serverless: an api/ folder of functions, or an Express app exported as the default export. Keep requests stateless " +
            "(no local files or in-memory sessions; use an external database if data must persist). Vercel functions cannot hold long-lived WebSockets; " +
            "if I need them, say so and suggest an alternative host. ") +
        "Use environment variables for secrets, list every variable I must set on Vercel, and never commit them."

    fun buildWeb(kind: ProjectType, name: String, pitch: String, stack: String, vercel: Boolean): String {
        val base = name.filter { it.isLetterOrDigit() }.ifBlank { if (kind == ProjectType.WEBSITE) "NewSite" else "NewServer" }
        val site = kind == ProjectType.WEBSITE
        return "Build a new ${if (site) "website" else "server (API)"} called $name: $pitch\n\n" +
            "Stack: $stack.\n" +
            (if (site) "Mobile-first responsive layout, accessible (semantic HTML, contrast, keyboard use), fast first load, no unused dependencies, dark and light theme."
            else "JSON API with input validation, clear error responses (status code + message), CORS only for the origins I name, structured logs without secrets, " +
                "and a GET /health route that answers 200 without auth and returns {\"ok\":true,\"version\":\"v1\"}.") + "\n\n" +
            (if (vercel) vercelRules(kind) + "\n\n" else "") +
            "Before coding: ask me up to 3 short questions if anything is unclear, then show a short plan (${if (site) "pages" else "routes"}, data, folders) and wait for my OK.\n\n" +
            (if (site) "Show a preview of any new visuals (logo, hero image, colors) first. Do not add them straight into the site.\n\n" else "") +
            "Delivery: zip the whole project as ${base}_v1.zip with a top-level folder ${base}_v1/. Version v1 (package.json \"version\" 1.0.0 if there is one), " +
            "shown ${if (site) "in the site footer" else "in the /health response"}. On every later change always bump the version, even for a string-only change."
    }

    fun ideasPrompt(cat: String, notes: String): String =
        "I want to build a new Android app in the \"$cat\" area." + (if (notes.isBlank()) "" else " My interests and must-haves: $notes.") +
        "\n\nDo NOT write any code yet.\n\nStep 1 - Ideas: suggest 8 distinct app ideas. For each give: a name, a one-line pitch, the 3 main screens, " +
        "the one feature that makes it special, and effort (small / medium / large).\n\nThen stop and ask me to pick one (or mix two). " +
        "Only after I choose: ask up to 3 short questions, show a one-screen plan and a preview of the look, and wait for my OK before building."

    fun buildPrompt(name: String, pitch: String): String {
        val base = name.filter { it.isLetterOrDigit() }.ifBlank { "NewApp" }
        return "Build a new Android app called $name: $pitch\n\n" +
            "Stack: Kotlin, Jetpack Compose (Material 3), Room for local data, minSdk 26. Clean dark UI with compact headers and no wasted padding.\n\n" +
            "Before coding: ask me up to 3 short questions if anything is unclear, then show a short plan and wait for my OK.\n\n" +
            "CI: add a GitHub Actions workflow that builds a signed release APK (secrets KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD) " +
            "and publishes it as a release asset.\n\n" +
            "Show a preview of any new visuals (logo, avatars, themes) first. Do not add them straight into the app.\n\n" +
            "Delivery: zip the whole project as ${base}_v1.zip with a top-level folder ${base}_v1/. Version v1 (versionCode 1). " +
            "On every later change always bump the version, even for a string-only change."
    }

    fun delivery(p: Project): String {
        val next = Commands.bump(p.version)
        val base = Commands.fileBase(p)
        if (p.kind != ProjectType.APP) return "\n\nDelivery rules: zip the whole project as ${base}_$next.zip with a top-level folder ${base}_$next/. " +
            "Always bump the version to $next (in package.json if there is one, and where it is shown: " +
            "${if (p.kind == ProjectType.WEBSITE) "the site footer" else "the /health response"}), even for a string-only change. " +
            "After the zip, list every file you created or changed (path + one short note)."
        return "\n\nDelivery rules: zip the whole project as ${base}_$next.zip with a top-level folder ${base}_$next/. " +
            "Always bump the version to $next (versionCode and versionName), even for a string-only change. " +
            "After the zip, list every file you created or changed (path + one short note)."
    }

    /** What the text box under step 4 asks for, per task. */
    fun hint(task: String, kind: ProjectType = ProjectType.APP) = when (task) {
        "Fix error" -> "Extra notes (optional). The latest build error is added automatically."
        "Add feature" -> "Describe the feature you want"
        "Improve UI" -> if (kind == ProjectType.SERVER) "Which endpoints or problems should be better?" else "Which screens or problems should be better?"
        "Refactor" -> "What should be cleaned up?"
        "Release notes" -> "List what changed"
        else -> "Describe what you want done"
    }

    /** [vercel] = the project deploys on Vercel (websites / servers only). */
    fun make(task: String, p: Project, stack: String, error: String, deliver: Boolean = false, change: String = "", vercel: Boolean = false): String =
        (if (p.kind == ProjectType.APP) body(task, p, stack, error, change.trim()) else webBody(task, p, stack, error, change.trim(), vercel)) +
            if (deliver) delivery(p) else ""

    /** Prompts for an existing website or server. */
    private fun webBody(task: String, p: Project, stack: String, error: String, change: String, vercel: Boolean): String {
        val site = p.kind == ProjectType.WEBSITE
        val noun = if (site) "website" else "server"
        val host = if (vercel) " It is hosted on Vercel and deploys from GitHub on every push to main." else ""
        val keep = if (site) "" else " Keep existing routes working unless I say otherwise, and keep GET /health answering 200."
        val tail = "Please keep the solution simple and production ready."
        fun fill(placeholder: String) = change.ifBlank { placeholder }
        return when (task) {
            "Improve UI" -> if (site)
                "Improve the design and UX of my website ${p.name} (${p.version}).$host\n\nFocus:\n${fill("<pages or problems to improve>")}\n\n" +
                    "Project stack: $stack.\n\nKeep the existing style, make it responsive and accessible, and show a preview before large visual changes. $tail"
            else
                "Improve the API of my server ${p.name} (${p.version}).$host\n\nFocus:\n${fill("<endpoints or problems to improve>")}\n\n" +
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
                "Project stack: $stack.\n\nKeep the existing dark green/cyan style, reduce wasted padding, and show a preview before large visual changes. $tail"
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
