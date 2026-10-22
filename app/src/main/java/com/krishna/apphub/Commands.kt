package com.krishna.apphub

object Commands {
    fun num(v: String): String = Regex("(\\d+)$").find(v)?.value ?: ""
    fun fileBase(p: Project) = p.name.filter { it.isLetterOrDigit() }
    fun bump(v: String): String = Regex("(\\d+)$").find(v)?.let { v.substring(0, it.range.first) + (it.value.toInt() + 1) } ?: v
}

object Prompts {
    val tasks = listOf("New app", "Fix error", "Add feature", "Improve UI", "Refactor", "Release notes", "Other")

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
