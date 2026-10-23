package com.krishna.apphub

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Language
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** v38: what kind of project this is. Decides which statuses and which actions a project gets. */
enum class ProjectType(val label: String, val plural: String, val defaultStack: String) {
    APP("App", "Apps", "Kotlin • Compose"),
    WEBSITE("Website", "Websites", "HTML • CSS • JS"),
    SERVER("Server", "Servers", "Node.js");

    companion object {
        /** Unknown or empty text (old rows, hand-edited backups) is an app. */
        fun from(s: String?): ProjectType = values().firstOrNull { it.name.equals(s?.trim(), true) } ?: APP
    }
}

val Project.kind: ProjectType get() = ProjectType.from(type)

fun ProjectType.icon(): ImageVector = when (this) { ProjectType.APP -> Icons.Default.Apps; ProjectType.WEBSITE -> Icons.Default.Language; ProjectType.SERVER -> Icons.Default.Dns }

/**
 * What the card shows. The raw CI result stays in Project.lastStatus (none / running / passed / failed);
 * the stage is that result read for the project's type:
 *  APP     Building, Passed, Installed
 *  WEBSITE Building, Deployed
 *  SERVER  Building, Running
 * plus Failed / No builds for every type, and Offline for a server whose deploy passed but whose endpoint does not answer.
 */
enum class Stage(val label: String) {
    NONE("No builds"), BUILDING("Building"), PASSED("Passed"), INSTALLED("Installed"),
    DEPLOYED("Deployed"), RUNNING("Running"), FAILED("Failed"), OFFLINE("Offline")
}

/** [healthy]: result of the last endpoint check of a server (null = not checked yet). */
fun stageOf(p: Project, healthy: Boolean?): Stage = when (p.lastStatus) {
    "running" -> Stage.BUILDING
    "failed" -> Stage.FAILED
    "passed" -> when (p.kind) {
        ProjectType.APP -> Stage.PASSED
        ProjectType.WEBSITE -> Stage.DEPLOYED
        ProjectType.SERVER -> when {
            !Health.canProbe(p.liveUrl) -> Stage.RUNNING   // nothing to check: the finished deploy is all we know
            healthy == true -> Stage.RUNNING
            healthy == false -> Stage.OFFLINE
            else -> Stage.BUILDING                          // deploy done, waiting for the first healthy answer
        }
    }
    else -> Stage.NONE
}

fun Stage.pill(): String = when (this) {
    Stage.NONE -> "No builds"; Stage.BUILDING -> "… Building"; Stage.PASSED -> "✓ Passed"; Stage.INSTALLED -> "✓ Installed"
    Stage.DEPLOYED -> "✓ Deployed"; Stage.RUNNING -> "● Running"; Stage.FAILED -> "✕ Failed"; Stage.OFFLINE -> "○ Offline"
}

fun Stage.color(): Color = when (this) {
    Stage.NONE -> Muted; Stage.BUILDING -> Amber
    Stage.PASSED, Stage.INSTALLED, Stage.DEPLOYED, Stage.RUNNING -> Green
    Stage.FAILED, Stage.OFFLINE -> Red
}

/** "example.com" from "https://example.com/path", null when empty. */
fun hostOf(url: String): String? = url.trim().takeIf { it.isNotEmpty() }?.substringAfter("://")?.substringBefore('/')?.takeIf { it.isNotEmpty() }

/** Trims, and adds https:// when the user typed a bare host. Empty stays empty. */
fun cleanUrl(s: String): String {
    val t = s.trim()
    return when { t.isEmpty() -> ""; t.contains("://") -> t; else -> "https://$t" }
}

/** Is the endpoint answering? Any HTTP answer below 500 counts (a 401 or 404 still means the server is up). https only: cleartext is blocked on Android. */
object Health {
    fun canProbe(url: String) = url.startsWith("https://", ignoreCase = true)

    suspend fun probe(url: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 5_000; c.readTimeout = 5_000; c.requestMethod = "GET"; c.instanceFollowRedirects = true
                c.responseCode in 100..499
            } finally { c.disconnect() }
        }.getOrDefault(false)
    }
}
