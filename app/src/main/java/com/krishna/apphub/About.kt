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
        "v51" to listOf("Includes v49 (v50 was skipped). Prompts now ask Claude to put previews in a new file in the zip (preview.html) instead of drawing them in the chat, so the chat does not fill up."),
        "v49" to listOf("Search open hides everything but the list (dashboard and all pages). New setup or import copies an adopt prompt for Claude; also in app Settings > Shortcuts. File type check before a push. Builds: full history (Load older builds, Copy history)."),
        "v48" to listOf("GitHub, Vercel, Supabase and Render pages: tap the title to refresh, search icon opens in the header (same as the dashboard). The old search box on GitHub is gone."),
        "v47" to listOf("Dashboard: tap App Hub to refresh. The refresh icon is now search; it opens in the header and closes with × or back. The always-on search box is gone."),
        "v46" to listOf("GitHub, Vercel and Supabase have their own pages like Render (Accounts > Open ...). Each lists what you have and adds it as a project in one tap: pick the type only. Vercel starts on Website, Render and Supabase on Server, GitHub has none selected. Setup: Import from chips. Removed the Deploy on picker and the Supabase test."),
        "v45" to listOf("Render has its own page with a card per service (last deploy, Open, Site, Deploy). Setup: Deploy on picks Vercel, Render or Supabase. Supabase: Test legacy API creates one test project and checks its legacy keys."),
        "v44" to listOf("Setup: Deploy on Vercel and Create GitHub repo are now tappable rows that show who is connected. A switch only counts while its account is logged in; tapping a row that is not connected opens Accounts."),
        "v43" to listOf(
            "Accounts: all logins now live on their own page (GitHub, Vercel, Supabase, Render). Settings shows who is connected and opens it.",
            "Supabase and Render connections: paste a token or API key, see your projects and services, tap one to open it. Read only."
        ),
        "v42" to listOf(
            "Secret check: before a push, keys, tokens and .env files in the zip are listed. Leave out the files that are secrets, push anyway, or cancel.",
            "Server alerts: a notification when a server stops answering or is back, checked about every 15 minutes even with the app closed. Switch in Settings.",
            "Vercel environment variables: add, replace or delete them in the app's Settings tab. Names only, values stay hidden."
        ),
        "v41" to listOf("Prompt Maker: the New app / New website / New server buttons are gone. Tasks follow the project type: a server gets Fix error, Add feature, Improve API, Refactor, Release notes, Other (no UI or UX)."),
        "v40" to listOf("Setup and Prompt Maker switches (create repo, signing keys, Deploy on Vercel, Host on Vercel, zip rules) keep their position and stay off until you tap them, even after the app is cleared from cache or force stopped."),
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
