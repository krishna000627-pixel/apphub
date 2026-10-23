package com.krishna.apphub

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

/** v28: short release notes shown on the About page (newest first). */
object ChangeLog {
    val list: List<Pair<String, List<String>>> = listOf(
        "v39" to listOf(
            "Vercel login in Settings (paste a token). Websites and servers get a Deploy on Vercel switch in Setup, above Create GitHub repo.",
            "A Vercel project follows the Vercel deployment: Building, Deployed or Running, build errors included. Push zips as before, no workflow needed.",
            "Prompt Maker: New website and New server, and web or server wording for existing projects."
        ),
        "v38" to listOf(
            "New project types: App, Website and Server. Pick one in Setup, change it in Settings.",
            "Status per type: App Building / Passed / Installed, Website Building / Deployed, Server Building / Running.",
            "A server shows Running once its https endpoint answers; a website keeps its live URL (found automatically for GitHub Pages).",
            "Backups keep the type and URL. Websites and servers get no APK, install or update actions."
        ),
        "v37" to listOf(
            "Info page: app card with name, version and status pills, then App / Build / Repository cards. Tap package or repo to copy.",
            "Files page: search and path stay in place while the list scrolls, Up one folder row, refresh button, match count, clearer delete confirmation."
        ),
        "v36" to listOf("Dashboard card: the orange Update button is now at the right end; the pill says \"Updated\" once the download is finished."),
        "v35" to listOf("Dashboard card: the download pill now says \"Updating\" and sits last, after the Passed and version pills."),
        "v34" to listOf(
            "Files is no longer a top tab: open it from the Files box on Info.",
            "Info shows only status, installed and version. Tap it for the full details."
        ),
        "v33" to listOf("Pill text that does not fit ends with \"...\" instead of escaping the pill."),
        "v32" to listOf("Shorter words on buttons, pills, tabs and dialogs so nothing is cut."),
        "v31" to listOf(
            "Files is a real browser: folders, breadcrumbs, file sizes and icons, search across the repo.",
            "Tap a file to open its own page: line numbers, find in file, copy, open on GitHub, delete. Images are shown as images.",
            "Overview shows the repo file count and opens README, manifest and build files in one tap.",
            "Tabs no longer cut their names; status pills and the download button no longer wrap or clip; \"Up to date\" instead of \"Update\" when current."
        ),
        "v30" to listOf("The older-version warning is back to the same dialog style as the wrong-app dialog (v28 look)."),
        "v28" to listOf(
            "Asks before pushing a zip that is the same as or older than the version on GitHub.",
            "Files tab in every app: search the repo and delete a file (one commit, can be undone).",
            "Install / Update buttons show the download while an update downloads.",
            "Shows Install (not Update) when the app is not on the phone; button text never wraps.",
            "About is its own page: About, What's new, Crash log, Help."
        ),
        "v27" to listOf(
            "Faster login, backup and restore, build and download notifications.",
            "Offline and rate-limit notes, previous build (rollback), storage + live sync controls."
        ),
        "v26" to listOf("Stays logged in: the GitHub token refreshes by itself; red banner if it cannot."),
        "v25" to listOf(
            "Live data every few seconds, resumable APK downloads, redesigned Install page.",
            "Version label follows the newest pushed vNNN commit."
        ),
        "v24" to listOf("A zip is offered once, to the app it is named after; wrong-app dialog before pushing."),
        "v23" to listOf("Version label no longer jumps on merge or cancelled runs; login notification.")
    )
}

private val helpItems = listOf(
    "Push a zip" to "Deploy tab: pick the zip (or tap the + on a card). App Hub uploads only new and changed files in one commit and GitHub builds it.",
    "Wrong zip" to "If the zip is named after another app, or is not newer than what is on GitHub, App Hub asks before pushing.",
    "Install or update" to "Open the app, tap Install (not on the phone) or Update. The button shows the download while it runs.",
    "Delete a file" to "Open the app, tap the Files box on Info, search, tap the bin. It is one commit and a new build starts. Undo last push in the app's Settings brings it back.",
    "Stay logged in" to "Add the client secret in Settings so the token refreshes by itself. If the red banner shows, log in again.",
    "Something broke" to "Crash log page: copy it and paste it to the AI together with what you did."
)

/** v28: About, What's new, Crash log and Help on their own pages, so Settings stays short. */
@Composable fun AboutScreen(vm: HubVM, nav: NavHostController) {
    val ctx = LocalContext.current
    val info = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }.getOrNull() }
    val appVer = info?.versionName ?: "?"
    val appCode = info?.let { androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(it) } ?: 0L
    var crash by remember { mutableStateOf(CrashLog.read(ctx)) }
    var page by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Header("About", nav)
        HubTabs(listOf("App", "New", "Crash", "Help"), page) { page = it }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (page) {
                0 -> {
                    Panel {
                        Text("App Hub", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.height(4.dp))
                        Mono("version : $appVer (build $appCode)")
                        Mono("GitHub  : ${vm.login.ifBlank { "not logged in" }}")
                    }
                    Panel {
                        Text("Links", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton({ openUrl(ctx, "https://github.com/krishna000627-pixel/apphub") }, contentPadding = PaddingValues(horizontal = 8.dp)) { BtnText("GitHub") }
                            if (vm.login.isNotBlank()) TextButton({ openUrl(ctx, "https://github.com/${vm.login}") }, contentPadding = PaddingValues(horizontal = 8.dp)) { BtnText("Profile") }
                        }
                    }
                    Panel {
                        Text("How it works", fontWeight = FontWeight.Bold)
                        Text("Deploy pushes your zip to GitHub as one commit. GitHub builds and signs the APK. Install downloads it. The version label follows every build automatically, however it was started.", fontSize = 12.sp, color = Muted)
                    }
                }
                1 -> ChangeLog.list.forEach { (v, lines) ->
                    Panel {
                        Text(v, fontWeight = FontWeight.Bold, color = Cyan)
                        lines.forEach { Text("• $it", Modifier.padding(top = 3.dp), fontSize = 12.sp) }
                    }
                }
                2 -> Panel {
                    Text("Last crash", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Mono(if (crash.isBlank()) "No crash saved." else crash, color = Muted)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton({ vm.copy(crash) }, enabled = crash.isNotBlank()) {
                            Icon(Icons.Default.BugReport, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); BtnText("Copy log")
                        }
                        TextButton({ CrashLog.clear(ctx); crash = "" }, enabled = crash.isNotBlank()) { BtnText("Clear") }
                    }
                }
                else -> helpItems.forEach { (t, d) ->
                    Panel {
                        Text(t, fontWeight = FontWeight.Bold)
                        Text(d, Modifier.padding(top = 2.dp), fontSize = 12.sp, color = Muted)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
