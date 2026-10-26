package com.krishna.apphub

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
        "v80" to listOf("The dot at the end of the download bar is gone. Text and icons on buttons now follow the colour behind them (white on the darker buttons of the light themes), and chips and bars use the theme colour instead of lilac. The prompt page has new input boxes: label above, inset box, green border while you type, a clear button, and a hint. Picked idea and option cards get a soft green fill."),
        "v79" to listOf("The build log looks like GitHub.com: one block per step with a status icon, name and time. Tap a step to open it; a running or failed step starts open. While a build runs the steps and the log refresh by themselves and the page follows the newest line. Errors are red, warnings amber, commands cyan, and every line has its number."),
        "v78" to listOf("Live build steps: open a running build on the Builds page and its steps update every 3 seconds, with the running step marked and counting up, and the time of each finished step. A running job's log page checks again every 4 seconds, follows the newest line, and fills in as soon as GitHub has the text."),
        "v77" to listOf("Tapping a card (build, app, release) now tints the whole card at once, with no half-covering circle."),
        "v76" to listOf("A file turns red while it is being deleted. The Build button shows a small spinner inside the button while it starts the build."),
        "v75" to listOf("Fixed the build error from v74 so the new themes, accent colour and text size install and work. Nothing else changed."),
        "v74" to listOf("Six new themes (AMOLED black, Midnight blue, Sunset, Violet, Slate, Paper) plus an accent colour and a text size on the Theme page. Dialogs share one cleaner look (icon, short text, full width buttons) and use less space. Push type and Theme are rows in Settings that open their own pages. Push type has details, a pick, and a switch to delete files you removed from the zip (off by default). Partial zips: partial.txt lists the deletes, the prompt page has a Partial zip switch, and App Hub checks it before the push."),
        "v73" to listOf("New push type: Settings > Push type > Git pack. It uses the git protocol (one request, raw files, no base64), checks the repo afterwards and falls back to the API if anything is off. The log shows MB and seconds for both push types."),
        "v72" to listOf("Deploy: a failed file upload is retried up to 3 times, big files go two at a time, and the failure line says the size and the reason. The file check warns about files over 4 MB and can leave them out."),
        "v71" to listOf("White theme: Settings > Theme (Dark, White, System). One Update button per app: the Updated pill, the ready-to-install banner and the Update ready card are gone. The hint under Deploy to is removed for apps."),
        "v70" to listOf("App Hub's own zip now carries whatsnew.txt (git-ignored, never pushed). release.sh writes it into the GitHub release notes."),
        "v69" to listOf("Tap an older release on the Releases page to download it: Download and install, Download only, Share file, Delete. The list now shows up to 30 releases. Clear saved APKs also clears these."),
        "v68" to listOf("About is a hub with sub pages (What's new, Crash log, Help) like a project: back arrow, no tabs, no bottom bar. If a project is deleted its pages send you home. What's new logger: the zip may carry whatsnew.txt (the app prompt asks for it). App Hub reads it, keeps a log per project, writes it into the GitHub release of an app, and never pushes the file."),
        "v67" to listOf("New project prompt maker: type, name and ideas are on one page (no empty first page), 2 pages for an app, 3 for a website or server. A new project starts at v0 when Create GitHub repo is on (the first zip becomes v1). After Setup, App Hub asks for the zip and opens the picker."),
        "v66" to listOf("New project prompt maker redesigned: big type cards, idea cards (2 per row) with category chips, option cards for stack and host, a ready page with what to do next, pages slide, the action bar stays at the bottom. Copy turns into Copied."),
        "v65" to listOf("Prompts split in two. Each project has its own Prompt page (task chips, one box, Copy; Adopt is a task there). The Prompt tab is now only for a NEW app, website or server, one page at a time: type, idea, setup, ready."),
        "v64" to listOf("Project page is now a hub with sub pages (Builds, Releases, Files, Info, Prompt, Settings), each with a back arrow. No bottom bar and no tabs on any project page. A failed-build notification opens the build list on top of the project."),
        "v63" to listOf("Project page redesigned: one navigation (its own bottom bar: Overview, Builds, Releases, Settings; the app bar is hidden there), new Overview with status, actions, attention cards, Files/Info/Prompt tiles. Builds: filters, durations, Re-run and Cancel. Notifications: separate channels (failed builds loud, passed quiet), commit and duration in the text, Builds and Install buttons, grouped, Mute alerts per app. Share and copy link from the menu."),
        "v62" to listOf("A changed address shows at once (unlink Vercel = GitHub Pages address right away, Pages off = its address gone right away). The note under Create GitHub repo is removed. Hosting settings moved to their own file."),
        "v61" to listOf("Setup: back to the old way. The new repo is always private (no Private/Public choice); change it any time in the app's Settings > Repository. When Vercel is unlinked from a website, its vercel.app links become the GitHub Pages address (home card, info page, saved address)."),
        "v60" to listOf("Home: a website's chip shows the short name (Vercel or GitHub Pages) instead of the long address. Open site still opens the address."),
        "v59" to listOf("A website can be on GitHub Pages and Vercel at once again. Home shows one address per website: Vercel while it is linked, otherwise the GitHub Pages address when Pages is on. Unlink Vercel hides its address and home falls back to Pages. Settings shows both panels; GitHub Pages says Enabled with Open site and Turn off."),
        "v58" to listOf("A website is on GitHub Pages or on Vercel. Settings shows the one in use: GitHub Pages says Enabled with its address, Open site and Turn off; Vercel keeps its own panel. When neither is on, both are offered. Linking Vercel turns Pages off, enabling Pages forgets the Vercel link (the Vercel project stays)."),
        "v57" to listOf("Settings tab: Repository panel with a Public repo switch (asks before making public). Vercel: the live address is now Vercel's real one, read after a deployment passes (no more guessed name.vercel.app). If a push does not start a Vercel deployment within about 25 s, App Hub starts it itself; Deploy now in the Vercel panel does it by hand."),
        "v56" to listOf("Setup: you choose Private or Public for the new repo. A Website starts on Public (GitHub Pages needs it on the free plan), an App or Server on Private. Your tap is kept while you change the type."),
        "v55" to listOf("Setup for a Server is back, full: Host on Vercel or Render (created from the repo) and Database Supabase (creates the project; SUPABASE_URL is set on the host). A not-logged-in chip opens Accounts. The server's Hosting panel also shows Open on Vercel."),
        "v54" to listOf("Setup: a Server no longer shows Deploy to (Apps and Websites still do). A server's Settings tab has a Hosting panel with Open on Render and Open on Supabase when the server matches a service or project in those accounts. Add a server from Accounts > Open Render / Supabase / Vercel."),
        "v53" to listOf("GitHub Pages: websites get a GitHub Pages chip under Deploy to in Setup, and an Enable GitHub Pages button in the app's Settings tab. It turns Pages on with GitHub Actions as the source and starts the deploy workflow, so you no longer do it by hand on github.com. A private repo on a plan without Pages gets a clear message."),
        "v52" to listOf("Setup: \"Import from\" is now \"Deploy to\". For a website or server pick Vercel, Render or Supabase and the project is created there with the repo (Vercel: linked and live address filled in; Render: free static site or Node web service; Supabase: new project). An app shows GitHub Releases. Not logged in: tap the chip to open Accounts. Existing repos are still added from Accounts > Open GitHub / Vercel / Render / Supabase."),
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
/** v68: About is a hub with sub pages (What's new, Crash log, Help), like a project: back arrow, no tabs, no bottom bar. */
@Composable fun AboutScreen(vm: HubVM, nav: NavHostController) {
    val ctx = LocalContext.current
    val info = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }.getOrNull() }
    val appVer = info?.versionName ?: "?"
    val appCode = info?.let { androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(it) } ?: 0L
    val crash = remember { CrashLog.read(ctx) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Header("About", nav)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Panel {
                Text("App Hub", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(4.dp))
                Mono("version : $appVer (build $appCode)")
                Mono("GitHub  : ${vm.login.ifBlank { "not logged in" }}")
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton({ openUrl(ctx, "https://github.com/krishna000627-pixel/apphub") }, contentPadding = PaddingValues(horizontal = 8.dp)) { BtnText("GitHub") }
                    if (vm.login.isNotBlank()) TextButton({ openUrl(ctx, "https://github.com/${vm.login}") }, contentPadding = PaddingValues(horizontal = 8.dp)) { BtnText("Profile") }
                }
            }
            Panel {
                NavRow(Icons.Default.NewReleases, Green, "What's new", ChangeLog.list.firstOrNull()?.let { "${it.first} and ${ChangeLog.list.size - 1} older" } ?: "Nothing yet") { nav.navigate("about/new") }
                RowLine()
                NavRow(Icons.Default.BugReport, Amber, "Crash log", if (crash.isBlank()) "No crash saved" else "A crash is saved", if (crash.isBlank()) null else "Saved") { nav.navigate("about/crash") }
                RowLine()
                NavRow(Icons.Default.Lightbulb, Cyan, "Help", "${helpItems.size} tips") { nav.navigate("about/help") }
            }
            Panel {
                Text("How it works", fontWeight = FontWeight.Bold)
                Text("Deploy pushes your zip to GitHub as one commit. GitHub builds and signs the APK. Install downloads it. The version label follows every build automatically, however it was started.", Modifier.padding(top = 2.dp), fontSize = 12.sp, color = Muted)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable fun AboutNewScreen(nav: NavHostController) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Header("What's new", nav)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChangeLog.list.forEach { (v, lines) ->
                Panel {
                    Text(v, fontWeight = FontWeight.Bold, color = Cyan)
                    lines.forEach { Text("• $it", Modifier.padding(top = 3.dp), fontSize = 12.sp) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable fun AboutCrashScreen(vm: HubVM, nav: NavHostController) {
    val ctx = LocalContext.current
    var crash by remember { mutableStateOf(CrashLog.read(ctx)) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Header("Crash log", nav)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Panel {
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
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable fun AboutHelpScreen(nav: NavHostController) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Header("Help", nav)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            helpItems.forEach { (t, d) ->
                Panel {
                    Text(t, fontWeight = FontWeight.Bold)
                    Text(d, Modifier.padding(top = 2.dp), fontSize = 12.sp, color = Muted)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
