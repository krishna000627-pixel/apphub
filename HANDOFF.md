# App Hub — handoff
Android app (Kotlin, Compose, Room, OkHttp). Package com.krishna.apphub. Manages build/push/fix/deploy for the user's apps from a phone.
- Screens: Dashboard, Project detail (build history + error card), Prompt Maker, Command Maker, Install/Update, Settings, Setup wizard.
- GitHub: OAuth device flow (Client ID entered in Settings), repo create, signing keystore, Actions secrets (libsodium sealed box).
- Termux: Command Maker bootstraps termux/setup.sh, termux/release.sh and .github/workflows/build.yml into a project if missing, then runs them (same scripts as BgBrowser).
- Secrets used by CI: KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_PASSWORD, KEY_ALIAS. build.gradle.kts reads them via env vars / keystore.properties.
- No gradlew in repo; CI installs Gradle 8.10.2. No compiler in chat: GitHub CI is the first build, paste errors back.
- Update flow: zip AppHub_vN.zip with folder AppHub_vN/, bump versionCode by 1 and versionName N.0, never reuse a name.
- v4: compact header (Scaffold insets = 0, status bar padding applied once), preset apps seeded on first launch (QuestBlock, BgBrowser, App Hub; owner filled after login), pins, Updates filter (installed tag vs latest release tag, stored in Prefs), search, stat strip, Deploy-from-zip command (Commands.deploy), prompt delivery rules, version label syncs upward from last pushed "vNNN:" commit, release.sh error paths generic.
- Release tags are build-N (CI); Project.version is the user's vNNN label. Installed tag is tracked separately in Prefs.
- v5: logo C (app grid) added as adaptive icon (res/drawable/ic_launcher_foreground.xml). "Deploy newest zip from Downloads" button (Commands.deployLatest) picks newest Name_v*.zip, unzips, releases.
- v6: Add screen switch "New app from newest Name_v*.zip in Downloads" (HubVM.setupFromZip / Commands.setupFromZip): unzip to ~/Name, bootstrap scripts, setup.sh (repo, keys, push, build, install).
- Not built yet: Files tab, background build notifications.

## v78
- Live builds. `GitHub.Step(name, ok, running, sec, startMs)` now carries the step state and timing (`jobs()` reads status / started_at / completed_at); `GitHub.jobDone(repo, jobId)` reads one job. Builds page (`BuildsBody`): an open card with `r.ok == null` re-reads the jobs every 3 s (`LaunchedEffect(r.id, r.ok)`, one last read when the result arrives) and a 1 s ticker drives the running step's timer (device clock vs GitHub clock, so a few seconds of skew is possible). `HubVM.openLog` is no longer a `launch` itself: it keeps `logPoll`, polls the log every 4 s while `logLive` (max 30 min) and stops when `jobDone`; `stopLog()` runs when the log page closes. GitHub normally gives a job's log text only after the job ends (the log endpoint answers 404 before), so while running the log page shows a short notice and fills in by itself; if GitHub does return partial text it is shown live. `LogScreen` follows the newest line unless you scrolled up. versionCode 78, versionName "78.0". Not compiled here: CI is the first build.

## v77
- `Modifier.boxClick(shape, onClick)` (Screens.kt, next to `Panel`): clips to the card shape, `clickable(indication = null)` and draws a Green 12% tint over the whole box while pressed. It replaces the ripple circle on the 4 clickable `Panel`s (dashboard project card, Builds card, release card, Screens.kt:379 card). Use it for any big clickable card; plain `clickable` is still fine for small rows. versionCode 77, versionName "77.0". Not compiled here: CI is the first build.

## v76
- Files.kt `NodeRow`: when `busy` (this file is the one in `HubVM.deletingFile`) the row gets a red tint, red icon box, red name and the line "Deleting…"; taps are off while busy. HubVM `buildingId` (Long, -1 = none) + `build(p)` keeps it set for the dispatch plus 700 ms; Screens.kt Build button swaps its 16 dp icon for a 16 dp `CircularProgressIndicator` (same slot, so it stays inside the button). versionCode 76, versionName "76.0". Not compiled here: CI is the first build.

## v75
- Build fix only. `HubVM.kt`: `var accent` / `var textSize` already generate `setAccent(I)V` / `setTextSize(I)V`, so the functions of the same name clashed ("Platform declaration clash"). Renamed the functions to `changeAccent` / `changeTextSize` (callers in `SettingsPages.kt`). Rule: never name a function `setX` next to a `var x` in the same class. versionCode 75, versionName "75.0". No new dependencies. Not compiled here: CI is the first build.

## v74
- New `Dialogs.kt`: `HubDialog(title, onDismiss, actions, icon, tone, sub) { body }` is the only dialog look now (no `AlertDialog` left in the project). Icon circle + title (+ one grey line), scrolling body, then buttons: 2 actions sit side by side (cancel left), 3+ are full width and stacked (main action on top, cancel as plain text). `tone` colours border, icon and main button (Green normal, Amber risky, Red destructive, Cyan info). Actions are built with `dPrimary` (filled, black text), `dSecondary` (outlined) and `dCancel`. Body helpers: `DialogText`, `DialogNote`, `DialogCard`, `DialogItem(path, why)`.
- Moved to `HubDialog`: Which repo? (Pages.kt), Push X? / Remove / Undo push / Install previous / Clear APKs / Export / Import / Project added / Delete env var / Wrong app / Possible secrets / Check these files / Older version (Screens.kt), Make public (HostingPage.kt), Older release (ProjectPage.kt), Delete file (Files.kt). Texts and button actions are unchanged.
- New `SettingsPages.kt`: Settings > Push type and Theme are NavRows (one Panel) that open `settings/push` (`PushTypeScreen`) and `settings/theme` (`ThemeScreen`), one `ChoiceCard` per choice (radio, green border on the chosen one). Same prefs as before (`Prefs.pushMode`, `HubVM.setTheme`). The Settings tab stays selected on `settings/...`.
- Delete removed files (Settings > Push type, `Prefs.deleteMissing`, default off, both push types): `GitHub.pushFiles` computes `gone` = repo files not in the zip (never `.github/` or `.git/`; skipped with a log line when over half the repo would go = partial zip; only when the repo listing is complete). The preview log shows `N to delete` and `  - path`; API path adds null-sha tree entries, Git pack path passes `gone` to `GitPack.build` (removes the entries, a folder left empty is dropped) and the post-push check expects them gone. `PushResult.removed`; the Deploy log says `N deleted`.
- Partial zips: a zip with `partial.txt` at its root (first line `partial`, then `delete: path` lines, a trailing `/` = folder) is a partial zip. `Zipper.parse` reads it into `Pack.partial` / `Pack.deletes` and never pushes it. `PartialCheck.scan` (Zip.kt) runs before the push through the existing Check these files dialog (empty zip, version file missing for apps, path both in the zip and in delete:, protected, over 20 delete lines); for a partial zip the "does not look like an App" file-type warnings are dropped. `GitHub.pushFiles(partial, deletes)`: only the listed deletes apply (the Delete removed files switch is ignored for that zip), lines not in the repo / protected / also in the zip are counted in the log; the push preview shows them as `- path`. `PartialCheck.isProtected` (.github/, .git/, build.gradle(.kts), settings.gradle(.kts)) also protects the switch's deletes. Prompt page: switch Partial zip (`Prefs.promptPartial`, only with the zip rules on) -> `Prompts.zipRule` / `Prompts.partialRule`. Push type page has a Partial zips panel with a Copy example button.
- Themes: `Theme.kt` rewritten. `Palette` + `Themes.byMode` (0 Dark, 1 White, 2 System = Dark or White by the phone, 3 AMOLED black, 4 Midnight blue, 5 Sunset, 6 Violet, 7 Slate, 8 Paper); `Pal.p` / `Pal.accent` are snapshot state and the colour getters (`Bg Surf Line Green Cyan Red Amber Muted`) read them, so still never cache them in top-level vals. Add-ons: accent colour (`Prefs.accent`, `Themes.accents`, 0 = the theme's own) and text size (`Prefs.textSize`, 0.9 / 1 / 1.15 applied through `LocalDensity` in `HubTheme`). `HubTheme(mode, accent, textSize, systemDark)`. Theme page: Dark / White / System, a More themes list with colour swatches, Accent colour, Text size. Compact spacing and a separate pure black switch were left out (the AMOLED theme is pure black). The preview.html used for the design was deleted after approval.
- Push type page now has detailed info per type, a recommendation and the delete switch (`SettingsPages.kt`).
- `whatsnew.txt` refreshed. versionCode 74, versionName "74.0". No new dependencies. Not compiled here: CI is the first build, paste errors back.

## v73
- Git pack push (opt-in: Settings > Push type, `Prefs.pushMode` 0 API / 1 Git pack; default API). New file `GitPack.kt`: builds blob / tree / commit objects (`GitPack.build`: the repo's recursive listing becomes a `Dir` tree, only folders above a changed file get new tree objects, git's folder sort order, modes kept, 100755 for .sh and gradlew like the API path), `writePack` (PACK v2, object headers, zlib BEST_SPEED, NO_COMPRESSION for png/jpg/mp4/zip/apk/epub/..., SHA-1 trailer), pkt-line helpers. No deltas.
- `GitHub.packPush` (called from `pushFiles` after the changed-file comparison, only when the listing is complete): GET `https://github.com/<repo>.git/info/refs?service=git-receive-pack` with Basic `x-access-token:<token>` (own OkHttp client `gitHttp`, 300 s read / 600 s write), checks the branch sha equals the parent and `report-status`, POSTs `git-receive-pack` (`report-status`, no side-band, streamed chunked body), reads `unpack ok` / `ok refs/heads/<branch>` / `ng`. Then verifies with `GET git/trees/<newRoot>?recursive=1` that the blobs are exactly the old ones plus the pushed ones; if not it PATCHes the branch back to the parent (`force`) and falls back to the API push. Any exception or non-200 also falls back, with the reason in the log. The merge semantics are the same as the API path (base tree kept, nothing deleted).
- Both paths log `• Git pack push: X MB in Y s` / `• API push: X MB in Y s` so they can be compared on the same zip. Not benchmarked here (no phone / network): measure it before relying on a speed claim.
- `whatsnew.txt` refreshed. versionCode 73, versionName "73.0". No new dependencies (okio comes with okhttp). Not compiled here: CI is the first build, paste errors back.

## v72
- Upload failures (seen with big png / epub files, "uploaded 40/49" then stop): `GitHub.pushFiles` blob upload now has two gates (`small` 6 parallel, `big` 2 parallel for files of 1 MB+), up to 3 tries per file (4xx other than 403/408/429 is not retried; waits 3 s then 6 s), and a failure line `✗ upload failed: path (X.X MB, reason)` for up to 5 files plus "Nothing was pushed. Run it again, or leave big files out". `uploadWhy(code, body)` gives the reason (timed out, GitHub rate limit, too large, server error, HTTP + message). HTTP client timeouts were already 120 s.
- `TypeCheck.scan` also flags files over 4 MB (up to 8, largest first, whole-file so "Leave out & push" drops them).
- `whatsnew.txt` refreshed. versionCode 72, versionName "72.0". No new dependencies. Not compiled here: CI is the first build, paste errors back.

## v71
- White theme. `Theme.kt`: `Pal.light` (snapshot state) + the palette (`Bg Surf Line Green Cyan Red Amber Muted`) are now getters (`val Bg: Color get() = ...`) with a dark and a light value each, so every existing use follows the theme with no call-site change (do NOT cache them in top-level vals). `HubTheme(light)` sets `Pal.light` before composing content, picks `darkScheme` / `lightScheme`, and in a `SideEffect` flips the status / navigation bar icon colour and the window background. `MainActivity` computes `light` from `vm.themeMode` (0 dark, 1 white, 2 follow the phone via `isSystemInDarkTheme()`). `Prefs.themeMode`, `HubVM.themeMode/setTheme`, Settings > Theme panel (3 chips). Accent fills keep black text (`Color.Black`); the light accents are darker so they stay readable on white. Files.kt search highlight no longer forces white text.
- One Update control: removed the dashboard "ready to install" banner and the amber Updated / Update pill on the card (the card's Update / Install button stays), the project home "Update ready" card and the "Update" badge on its Releases row (the hero Update button stays), and the "Update" pill on the newest release row (its button stays; "Latest" pill only when up to date).
- Setup: removed the hint under the "GitHub Releases" chip for apps.
- `whatsnew.txt` refreshed for v71. versionCode 71, versionName "71.0". No new dependencies. Not compiled here: CI is the first build, paste errors back.

## v70
- v68 and v69 shipped without a `whatsnew.txt` (only the reader and the prompt rule existed). From v70 every zip carries `whatsnew.txt` at the top of the project folder (3 to 6 lines starting with "- ", what is new for the user in that version). It is listed in `.gitignore`, so `termux/release.sh` (`git add -A`) never commits it. `release.sh` now, after the CI run passed, reads the run number (`gh run view --json number`) and runs `gh release edit build-<number> --notes-file whatsnew.txt` when the file exists (silent if it does not work). Keep writing a fresh whatsnew.txt in every delivery.
- versionCode 70, versionName "70.0". No new dependencies. Not compiled here: CI is the first build, paste errors back.

## v69
- Older releases: on the Releases page (`ReleasesBody`, ProjectPage.kt) a row other than the newest opens `OldReleaseDialog` (download with progress, "Download and install", "Download only", then Install / Share file / Delete). The newest row still opens the Install page. `GitHub.releaseByTag(repo, tag)` finds the APK asset of one release; `HubVM.downloadOld/installOld/shareOld/deleteOld`, state `oldDl` ("id:tag" -> progress) and `oldMsg`. Files: `filesDir/apks/old/app_<id>_<tag>.apk` (`ApkStore.old/oldAll`, covered by the existing FileProvider path `apks/`), downloaded through a `.part` file (resumes) and checked with `getPackageArchiveInfo`. Separate from the latest / previous-build cache, so none of that logic changed. `ApkStore.usedBytes` now counts sub folders; `clearApks` also deletes the saved older releases.
- `GitHub.releases` returns up to 30 (was 8; the Install page still shows 5). A release with no APK (`sizeKb == 0`) shows a toast instead of the dialog.
- Android does not install an older versionCode over a newer one: the dialog says so when the app is installed with another tag.
- versionCode 69, versionName "69.0". No new dependencies. Not compiled here: CI is the first build, paste errors back.

## v68
- About = hub with sub pages (About.kt): hero + links, one Panel of `NavRow`s (What's new, Crash log, Help) and How it works. Routes `about`, `about/new`, `about/crash`, `about/help` (`AboutNewScreen`, `AboutCrashScreen`, `AboutHelpScreen`), no bottom bar (`onProject` prefixes now include "about" and "whatsnew/"). `NavRow` / `RowLine` are public now (ProjectPage.kt). The old tab row is gone from About.
- Deleted project: `projectOrHome(vm, nav, id)` (ProjectPage.kt) is used by every project page (home, builds, releases, settings, aprompt, whatsnew, info, files, file, install, env). If the project is missing for 500 ms it navigates to `dash` (popUpTo dash), so you never see an empty screen.
- What's new logger: the app prompt (`Prompts.delivery`, `buildPrompt`, `newProject`, i.e. when the "zip and version rules" switch is on) asks for `whatsnew.txt` at the top level of the project folder (`Prompts.whatsNewRule`). `Zipper.parse` reads `whatsnew.txt` / `whatsnew.md` into `Pack.whatsNew` and drops it from `entries`, so it is never pushed. On a committed push `HubVM.deploy` saves it with `Prefs.addWhatsNew(repo, ver, text)` (JSON under `wn_<repo>`, last 500). `HubVM.writeWhatsNew(p)` runs from `refresh(p)`: for an app, once the run whose commit message starts with `vNN:` passed, it PATCHes the release `build-<run number>` body (`GitHub.setReleaseBody`) and marks the entry written (retried for 3 days). `notesFor` prefers the logged text of that version. Project home has a "What's new" row -> `whatsnew/{id}` (`WhatsNewScreen`: version, On release / Waiting pill, ago, Copy). The Deploy screen shows "whatsnew: found" when the zip has it.
- versionCode 68, versionName "68.0". No new dependencies. Not compiled here: CI is the first build, paste errors back.

## v67
- Prompt Maker: the 3-card type page is gone. Page 1 = `TypeSegments` (App / Website / Server) + description + Name/What is it? + ideas (cards, category chips for apps) + "Not sure?" prompt; page 2 = stack + host (website/server only); last page = ready (summary, 3 steps, Copy prompt, Set up). `total` = 2 for an app, 3 otherwise. Switching type drops a name/idea that came from the old type's list. "Set up" carries `vm.draftName` and `vm.draftType` to `AddScreen` (`type` starts from `draftType`, reset after).
- New project version: `AddScreen` uses v0 when Create GitHub repo is on (placeholder says so), else v1. The first pushed zip is v1 (`Commands.bump("v0")`), and the label still syncs upward from the pushed commit.
- After Setup: `HubVM.setup` sets `setupDoneId`; `AddScreen` shows "Project added ... give App Hub your zip (Name_vN.zip)" with Choose zip / Later. Choose zip selects the project, sets `vm.askZip` and goes to Deploy, where a `LaunchedEffect(vm.askZip)` opens the zip picker once.
- versionCode 67, versionName "67.0". No new dependencies. Not compiled here: CI is the first build, paste errors back.

## v66
- `NewProjectScreen` (PromptPages.kt) redesigned. Layout: header (back, title, "Step n of N"), segmented step bar (green up to the current page), `AnimatedContent` page slide, pinned action bar at the bottom (steps 1-2: Back + Next, Next disabled until name and "What is it?" are filled; step 3: Copy prompt -> "Copied" + Set up). Page 0: `KindCard` x3 (icon, title, line, tags; tap = go on). Page 1: Name + What is it? in one Panel, category chips in a horizontal scroll (apps), `IdeaCard` grid 2 per row (green border = chosen), "Not sure? Copy a prompt for 8 ideas". Page 2 (website/server only): `OptionCard` radio cards for stack and host with a short reason (`stackNote`, `hostNote`). Page 3: summary card (name, pitch, tags), 3 numbered next steps, Start over. Apps skip page 2.
- Prompt text is unchanged (`Prompts.buildPrompt` / `Prompts.newProject`). No new dependencies (androidx.compose.animation comes with Compose 1.7 via the BOM). Not compiled here: CI is the first build, paste errors back.

## v65
- Prompt tools split (new file `PromptPages.kt`, old `PromptScreen` removed from Screens.kt). `AppPromptScreen` (route `aprompt/{id}`, no bottom bar, opened from the project's Prompt row and the Build failed "AI prompt" button): task chips = `Prompts.appTasks(kind)` (project tasks + Adopt before Other), one note box (Adopt has none), delivery-rules switch, Copy. Uses the project's own stack (`p.stack`) and the build error automatically. `Prompts.make("Adopt", ...)` = `Prompts.adopt(p)`.
- `NewProjectScreen` = route `prompt` (bottom tab "Prompt"), only for a NEW project, one page at a time (BackHandler steps back): 0 type (App / Website / Server), 1 idea (name + what is it + idea rows; `Ideas.list` for apps, new `Ideas.web` / `Ideas.server`; "Copy an ideas prompt" = `Prompts.ideasPrompt(cat, notes, kind)`), 2 setup (stack + host chips from `Prompts.stacksFor/hostsFor`, websites and servers only), 3 ready (summary, Copy prompt, "Set up the project" -> `add`, Start over). Apps use `Prompts.buildPrompt`; websites/servers use new `Prompts.newProject(kind, name, pitch, stack, host)` (host text: GitHub Pages, Vercel, Render, Docker; servers get GET /health; websites get PREVIEW).
- `vm.promptNote`, `vm.sel()`, `ideaCat`, `draftName` are no longer used by any screen (left in place).
- versionCode 65, versionName "65.0". No new dependencies. Not compiled here: CI is the first build, paste errors back.

## v64
- Project pages are sub pages, not tabs (replaces the v63 tab bar): `detail/{id}` = hub (`ProjectScreen`: header, hero, Update/Build-failed cards, one Panel of `NavRow`s: Builds, Releases (apps), Files, Info, Prompt, Settings, then latest 3 builds and 5 commits). Sub routes: `builds/{id}` (`BuildsScreen`), `releases/{id}` (`ReleasesScreen`), `psettings/{id}` (`ProjectSettingsScreen` = `AppSettings`), plus the existing `files/{id}`, `info/{id}`, `file/{id}`, `env/{id}`, `install/{id}`. All use `Header(..., nav)` (back arrow). `KeepFresh` is the shared refresh loop.
- `App` hides the global bottom bar for all of those prefixes (`onProject`) and adds `navigationBarsPadding()` there so content clears the gesture bar. `openRoute` "builds/ID" / "releases/ID" first navigates to `detail/ID`, then the sub page (back = project).
- Notifier: failed build opens `builds/ID` (was `detail/ID?tab=builds`); the `tab` argument and tab bar are gone.
- versionCode 64, versionName "64.0". No new dependencies. Not compiled here: CI is the first build, paste errors back.

## v63
- Project page = `ProjectPage.kt` (`ProjectScreen(vm, nav, id, startTab)`), replaces `Detail` in Screens.kt. Route `detail/{id}?tab={tab}` (tab: overview|builds|releases|settings, default overview). One navigation: `App` hides the global bottom bar for routes starting with detail/, info/, files/, file/, env/, install/ (`onProject`); the project has its own NavigationBar (Overview, Builds + red badge when the newest run failed, Releases for apps only, Settings = `AppSettings` unchanged). BackHandler: other tab -> Overview first.
- Overview: hero card (version, stack, status/installed|live/last build with duration, Deploy|Push + Install|Open), "Update ready" card, "Build failed" card (Copy log, AI prompt, Re-run), tiles Files / Info / Prompt, latest 3 builds, 5 commits. Header: back, name+repo, stage pill, pin, menu (Open on GitHub, Copy link, Share).
- Builds: filter chips All/Passed/Failed, expandable run (Re-run or Cancel build, steps, View full log), Load older, Copy. `GitHub.Run.sec` (duration from run_started_at/updated_at; not stored in SnapCache), `GitHub.rerun/cancel`, `HubVM.rerun/cancelRun`.
- Notifier rewritten: channels build_failed (high), build_ok (low), server_down (high), server_up (low), updates (default); the old builds/servers/apks channels are deleted. Failed build text = commit message + duration, opens `detail/ID?tab=builds`; buttons Builds / Install; one group summary; `Prefs.muted(id)` per app (Settings > Shortcuts > Mute alerts, `HubVM.isMuted/toggleMute`).
- versionCode 63, versionName "63.0". No new dependencies. Not compiled here: CI is the first build, paste errors back.

## v62
- New file `HostingPage.kt`: `ColumnScope.HostingPanels(vm, nav, p)` = the app Settings panels that were inline in `AppSettings` (Repository visibility, server Hosting, GitHub Pages, Vercel), plus `HubVM.siteLink(p)` / `HubVM.siteName(p)` as extensions (removed from HubVM.kt). `AppSettings` just calls `HostingPanels(vm, nav, p)`.
- `siteLink` is state-only (vercelRev via `isVercel`, `pagesOn`, liveUrl): Vercel linked -> liveUrl (github.io ignored); else Pages on -> `pagesUrl(repo)`; else a vercel.app liveUrl -> `pagesUrl(repo)`; a github.io liveUrl with `pagesOn == false` -> ""; else liveUrl. `unlinkVercel` also reads `gh.pagesState` at once.
- Setup: the one-line note under "Create GitHub repo (private)" is removed. Settings Vercel "Unlink" row says "Links switch to GitHub Pages".
- versionCode 62, versionName "62.0". No new dependencies. Not compiled here: CI is the first build, paste errors back.

## v61
- Setup: the v56 Private/Public chips and the automatic "Website = public" rule are gone (`vis` removed from AddScreen). The repo is created private again (`HubVM.setup(..., isPrivate = true)`), with a one-line note that visibility can be changed later in the app's Settings > Repository panel (v57, unchanged). If the Pages target fails on the private repo, the setup log says to make it public in Settings and enable Pages there.
- Unlinked Vercel -> GitHub Pages: `HubVM.unlinkVercel` saves `pagesUrl(repo)` instead of an empty address for a website whose liveUrl was a vercel.app one (other kinds still just lose it). `siteLink` returns `pagesUrl(repo)` for a website that is not on Vercel and still has a vercel.app liveUrl (was ""), even if Pages is off (the Settings Pages panel enables it). The info page (`Info.kt` pill + "Live URL" row) and the card's "live" line now use `siteLink(p)` like the home card, so no screen shows an old vercel.app address.
- versionCode 61, versionName "61.0". No new dependencies. Not compiled here: CI is the first build, paste errors back.

## v60
- Home card chip for a website shows `HubVM.siteName(p)`: "Vercel" (vercel.app address), "GitHub Pages" (github.io address), else the host of a typed address. Other types still show the host. The address itself (`siteLink`) is unchanged and still opened by the Open site button.
- versionCode 60, versionName "60.0". No new dependencies. Not compiled here: CI is the first build, paste errors back.

## v59
- Replaces the v58 "Pages XOR Vercel" rule: both can be on. Settings (website) shows the GitHub Pages panel (Enabled + address, Open site, Turn off, else Enable) and the Vercel panel always. `linkVercel` no longer turns Pages off, `enablePages` no longer unlinks Vercel.
- Home address: `HubVM.siteLink(p)` (Website): Vercel linked -> liveUrl (a github.io value is ignored); else `pagesOn[p.id] == true` -> `pagesUrl(repo)`; else liveUrl unless it is a vercel.app address (hidden once unlinked). Servers and others: liveUrl. Used for the "↗ host" pill and the Open site button on the card. `pagesCheck(p)` (GET /repos/{repo}/pages, at most once a minute per website) runs from `refresh`. `unlinkVercel` clears a vercel.app liveUrl; `fixVercelAddress` also replaces a github.io liveUrl while Vercel is linked.
- versionCode 59, versionName "59.0". No new dependencies. Not compiled here: CI is the first build, paste errors back.

## v58
- Website hosting is Pages XOR Vercel in the app Settings tab: `siteOnVercel = vm.isVercel(p)`, `siteOnPages = vm.pagesOn[p.id] == true` (`GitHub.pagesState` GET /repos/{repo}/pages 200/404, read by `HubVM.loadPages` when the tab opens). Pages panel shows when on Pages or not on Vercel: "Enabled" + address + Open site + Turn off (`GitHub.disablePages` DELETE /repos/{repo}/pages, `HubVM.disablePages`), else the Enable button. The Vercel panel shows for servers always and for websites when on Vercel or not on Pages.
- `HubVM.linkVercel`: for a website, turns Pages off if it was on and drops a github.io liveUrl. `HubVM.enablePages`: forgets the Vercel link (`unlinkVercel`, the Vercel project stays) and replaces a vercel.app liveUrl with the Pages one.
- versionCode 58, versionName "58.0". No new dependencies. Not compiled here: CI is the first build, paste errors back.

## v57
- Repo visibility in the app Settings tab ("Repository" panel, all types): `GitHub.repoInfo(repo)` (id, private), `GitHub.updateVisibility(repo, makePrivate)` (PATCH /repos/{repo}), `HubVM.loadVisibility/changeVisibility` (`repoPrivate` map, `repoBusy`, `repoMsg`); making public asks first. Setup chips from v56 stay.
- Vercel address: the guessed `name.vercel.app` is gone (`Vercel.domainOf` and `projects()` return "" when unknown). `Vercel.liveHost(projectId)` = GET /v9/projects/{id} targets.production.alias (shortest .vercel.app), else /domains. `HubVM.fixVercelAddress` saves it as liveUrl when a deployment passed (from `vercelState`, once per deployment, and from `watchVercel`) if liveUrl is empty or already a vercel.app address.
- Vercel not started by the push: `watchVercel` waits ~24 s, then calls `startVercel` once (`Vercel.deployFromGit`: POST /v13/deployments?forceNew=1 with gitSource github repoId + ref main, repo id from `GitHub.repoInfo`). Same call from the Vercel panel row "Deploy now" (`HubVM.deployVercelNow`). Needs the Vercel GitHub app to have access to the repo; the error text says so.
- versionCode 57, versionName "57.0". No new dependencies. Not compiled here: CI is the first build, paste errors back.

## v56
- Repo visibility: `GitHub.createRepo(name, private)`; `HubVM.setup(..., isPrivate)`. AddScreen `vis` (null = automatic: Website public, App/Server private; a tap on Private/Public chip is kept when the type changes). Chips and a note show under the Create GitHub repo row when it is usable. The row title says (private) or (public). Existing repos are never changed.
- versionCode 56, versionName "56.0". No new dependencies. Not compiled here: CI is the first build, paste errors back.

## v55
- Setup, Server: "Host on" chips (Vercel | Render, single choice, tap again = none) and "Database" chip (Supabase, toggle); each needs its account connected, else the chip reads "(log in)" and opens Accounts. `HubVM.setup(..., target, db)`: host first (`vercel.link` / `render.create(website=false)`; ids kept in hostVercelId/hostRenderId), then `supabase.create(name)`; with a host, SUPABASE_URL (https://<id>.supabase.co) is set via `Vercel.envSet` / new `RenderApi.setEnv` (PUT /v1/services/{id}/env-vars/{name}). Supabase keys and the DB password are NOT set (password random, not kept; keys need the project ACTIVE). The server's liveUrl = host address; Supabase address only when there is no host.
- Hosting panel (server Settings tab) also lists "Open on Vercel" (when linked).
- App and Website Deploy to unchanged. versionCode 55, versionName "55.0". No new dependencies. Not compiled here: CI is the first build, paste errors back.

## v54
- Setup: Deploy to is hidden for a Server (shown for App = disabled GitHub Releases chip, Website = GitHub Pages | Vercel | Render | Supabase). `vm.setup` target is only passed for Websites. Servers are added/linked from Accounts > Open Render / Supabase / Vercel (`importProject`) or the Vercel panel in app Settings.
- App Settings tab (SERVER): Hosting panel, `HubVM.renderFor(p)` (service whose repo = project repo) and `supabaseFor(p)` (liveUrl contains <id>.supabase.co, or project name = app name / repo name); rows "Open on Render" (`Svc.dashboardUrl`) and "Open on Supabase" (dashboard/project/<id>). Lists load when the tab opens if the account is connected and the list is empty.
- versionCode 54, versionName "54.0". No new dependencies. Not compiled here: CI is the first build, paste errors back.

## v53
- GitHub Pages: `GitHub.enablePages(repo)` (POST /repos/{repo}/pages build_type=workflow; 409 = already on, then PUT to make the source GitHub Actions; plan/private-repo errors give a plain message), `GitHub.dispatchDeploy(repo)` (workflow deploy.yml, ref main), top-level `pagesUrl(repo)` (ProjectStatus.kt).
- Setup: "GitHub Pages" chip under Deploy to for Website only (target "pages" in `HubVM.setup`; fills liveUrl). App's Settings tab: Website panel "GitHub Pages" with `HubVM.enablePages(p)` (enable, dispatch deploy, fill liveUrl if empty; `pagesBusy/pagesMsg`).
- versionCode 53, versionName "53.0". No new dependencies. Not compiled here: CI is the first build, paste errors back.

## v52
- Setup (AddScreen): the "Import from" chips are replaced by "Deploy to". Websites/servers pick Vercel | Render | Supabase (FilterChip, tap again = none, state local to the screen, only counts while that account is connected; a chip of a disconnected account reads "(log in)" and opens Accounts). Apps show a disabled "GitHub Releases" chip + note.
- `HubVM.setup(..., liveUrl, target)`: after the repo/keys steps, `vercel.link(repo)` (id stored, live address filled), `render.create(name, repo, website)` (new `RenderApi.create`: GET /owners then POST /v1/services; static_site or free node web_service, autoDeploy yes), `supabase.create(name)` (new `SupabaseApi.create`: GET /v1/organizations then POST /v1/projects, americas, random db_pass not kept, liveUrl https://<id>.supabase.co). A failed target logs a red line and the project is still saved.
- Import of existing repos is unchanged (Accounts > Open GitHub/Vercel/Render/Supabase, `HubVM.importProject`).
- versionCode 52, versionName "52.0". No new dependencies. Not compiled here: CI is the first build, paste errors back.

## v51
- v49 was never pushed and v50 was skipped: v51 contains everything from v49 (see below) plus this. versionCode 51, versionName "51.0".
- Prompts: `Prompts.PREVIEW` ("put every preview in a NEW file in the zip, e.g. preview.html, never draw it in the chat; do not add it to the app until approved"). Used in ideasPrompt, buildPrompt, the Improve UI task text (app and website) and appended to both `delivery()` variants, so every delivery-rule prompt carries it. Reason: previews drawn in the chat burn the context window.
No new dependencies.

## v49
- Search open = hide everything else: `SearchHeader` now takes `searching/onSearching` (state lives in each screen); the screens wrap their summary panels/messages in `if (!searching)`. Dashboard wraps session/net notes, stat strip, install follow-ups and filter chips; the chip filter is ignored while searching.
- Adopt prompt: `Prompts.adopt(p)` (Commands.kt), per type (APP: build.yml signed APK + 4 secrets + plain versionCode/versionName; WEBSITE/SERVER: deploy workflow, version in footer or /health, no secrets/node_modules) + `delivery(p)`. Copied by itself (quietly) by `HubVM.setup` and `HubVM.importProject`, and from app Settings > Shortcuts > "Copy adopt prompt". `HubVM.copy(text, quiet)`.
- File type checker: `TypeCheck.scan(kind, entries)` (Zip.kt) runs in `HubVM.deploy` after the secret check (`typesChecked` param). Flags: no files of the project's type, Android files in a website/server, build outputs/archives (.apk .aab .zip .exe ...), node_modules/. `TypeAsk` + `TypeDialog` (Leave out & push drops whole-file/folder issues via `TypeCheck.dropSet` + `Pack.without`, Push anyway, Cancel).
- Build history: `GitHub.runsPage` (100 per page, total_count) + `HubVM.loadOlder` (olderRuns/olderMore/olderLoading, in memory only). Builds tab lists live runs + loaded older ones, "Load older builds" until the end, "Copy history" (`historyText`).
No new dependencies.

## v48
- Shared `SearchField` + `SearchHeader` (Pages.kt). Provider pages (GitHub, Vercel, Supabase, Render) use `SearchHeader`: back arrow, title (tap = refresh, spinner while loading), search icon that opens the field in place (x clears, then closes; back closes). Lists filter on name (and repo). The dashboard uses the same `SearchField`. The GitHub page's own search box and the private `Refresh` icon helper are removed.
No new dependencies.

## v47
- Dashboard header (Screens.kt `Dashboard`): own Row instead of `Header`. Tap "App Hub" = `vm.refreshAll()` (small spinner next to the name while refreshing). The refresh icon became a search icon; it swaps the header for a `BasicTextField` (same 40dp height, so nothing jumps), closes with x (clears first) or back (`BackHandler`). The old `Search apps` box above the list (shown from 6 projects) is removed; `q` still filters the list.
No new dependencies.

## v46
- Provider pages (`Pages.kt`): `GitHubScreen` (route `github`), `VercelScreen` (`vercel`), `SupabaseScreen` (`supabase`); Render page (`render`) got the same Add. Opened from Accounts ("Open GitHub/Vercel/Supabase/Render") and from the "Import from" chips on Setup. Shared `ImportCard` (title, pill, type chips, Open / Add). `HubVM.importProject(name, repo, type, liveUrl, vercelId)` saves the Project directly (no setup form), skips repos already added, and stores the Vercel project id for Website/Server so the Vercel status works.
- Default type on Add: Vercel = Website, Render = Server, Supabase = Server, GitHub = none (Add stays off until a type is picked). Repo source: Vercel project link, Render service `repo`, Supabase = GitHub repo with the same name (else a dialog asks owner/name), GitHub = the repo itself.
- Lists: `GitHub.repos()` (GET /user/repos, first 100, shows 40 + search), `Vercel.projects()` (GET /v9/projects: link, production alias, latest deployment state), `RenderApi.Svc.repo`, `SupabaseApi.projects()`.
- Removed: Supabase legacy test and project creation, Render service creation, the Setup "Deploy on" picker and `Prefs.setupTarget/setupVercel`, `HubVM.setup` vercelOn/target. Vercel linking for an existing project is still in the app's Settings tab and on import.
- Not compiled here: CI is the first build, paste errors back.

## v45
- Render page: `RenderScreen.kt` (route `render`, opened from Accounts > Render > Open Render). Summary + one card per service (kind icon, status pill from the newest deploy, Open / Site / Deploy). `HubVM.refreshRender` (on open, every 20 s while open), `renderDeploys` map, `renderDeploy(sv)` = POST /v1/services/{id}/deploys. Accounts Render panel is now just connect / Open Render / Disconnect.
- Setup "Deploy on": one choice Vercel | Render | Supabase | none (`Prefs.setupTarget`, old `su_vercel` carries over as vercel). `HubVM.setup(..., vercelOn, target)`. Render: `RenderApi.create` (POST /v1/services; website = static_site, server = Node web service, free plan, repo default branch). Supabase: `SupabaseApi.create` makes a project and fills liveUrl with https://ref.supabase.co. Only for Website/Server.
- Supabase test: Accounts > Supabase > "Test legacy API" (`HubVM.supabaseTest`): creates ONE project `apphub-test` (reuses it if present), waits for ACTIVE, GET /api-keys/legacy (enabled flag), GET /api-keys?reveal=true, then calls /rest/v1/ with the anon and service_role keys. Keys masked in the log, never stored. DB password is random and not kept. Create uses organization_slug + region_selection (americas), retries with organization_id + us-east-1 on 400/422.
- Not compiled here: CI is the first build, paste errors back.

## v44
- AddScreen: "Deploy on Vercel" and "Create GitHub repo" are `ConnRow`s (Panel with icon, title, connection status line, switch; the whole row is tappable). Connected: tap toggles and saves the position (Prefs.setupVercel / setupCreate). Not connected (or GitHub session expired): the switch shows off, the saved position is kept untouched, and a tap opens the Accounts page. `vercelUsable` / `createUsable` (switch AND account connected) are what the screen text and `vm.setup` use, so a switch can never act without its login. The old amber "log in first" line is gone (the row says it).
- Only Vercel is listed as a deploy target: Render and Supabase are connected read-only (no deploy code), so they are not offered here yet.
No new dependencies.

## v43
- Logins moved out of Settings into `AccountsScreen` (route `accounts`, back arrow header): the GitHub panel and the Vercel panel are moved over unchanged, plus new Supabase and Render panels. Settings keeps a small "Accounts" summary (who is connected) with an "Open accounts" button. The dashboard "GitHub session expired" button and the "log in first" hints/toasts now point to Accounts.
- Supabase: `Supabase.kt` (`SupabaseApi.projects`: GET api.supabase.com/v1/projects with a personal access token). Render: `Render.kt` (`RenderApi.owner` = GET /v1/owners, `services` = GET /v1/services). Token / key in encrypted Prefs (`supabaseToken`, `renderKey`, `renderUser`). VM: `supabaseLogin/Logout/load`, `renderLogin/Logout/load`; lists load silently when the Accounts page opens (replaced only when changed). Tapping a project / service opens it on its dashboard.
- Read only on purpose: nothing deploys, pauses or changes on Supabase or Render yet. Natural next steps: link a SERVER project to a Render service (status from Render instead of the endpoint probe), or to a Supabase project (paused warning).
No new dependencies.

## v42
- Secret check: `SecretScan` (Zip.kt) runs when a zip is parsed (file names: .env* except .example/.sample/.template/.dist, .pem/.p12/.pfx/.key/.keystore/.p8, id_rsa..., service-account json; contents of text files up to 400 KB: private key blocks, GitHub/AWS/Stripe-live/Slack/Anthropic/OpenAI-style keys). `Pack.findings`. `HubVM.deploy(..., secretsChecked)` stops and sets `secretAsk` when there are findings; the Deploy screen shows `SecretDialog` (Leave out & push drops whole-file findings via `Pack.without`, Push anyway, Cancel). Every push path goes through `deploy`, so Push now / Undo-free flows are covered.
- Server alerts: `ServerWatch.kt`. `record(ctx, prefs, project, ok)` keeps a per-server state in Prefs (0 unknown, 1 up, 2 down never announced, 3 down announced): down is announced after 2 failed checks in a row and only if the server was up before; up only after an announced down; the first result is only remembered. Fed by `HubVM.probeServer` (app open) and by `ServerWatchWorker` (WorkManager, every 15 min, only SERVER projects with an https liveUrl, second check after 5 s on a failure). Switch `Prefs.serverAlerts` (default on) in Settings > Notifications; needs the global Notify me switch. `Notifier.serverDown/serverUp`, channel "servers".
- Vercel environment variables: `Vercel.envList/envSet/envDelete` (v10 list and create with upsert=true, v9 delete; type "encrypted", targets production+preview+development). `EnvScreen` (route `env/{id}`), opened from the Vercel panel in the app's Settings tab. Values are only sent, never read back or stored. Changes apply from the next deployment (no redeploy button).
- Not built (already there): push preview, Builds tab as timeline, Copy errors + AI prompt on a failed build, Undo last push and previous APK as rollback. Not built (gap): Vercel instant rollback (promote an older deployment); Undo last push triggers a new Vercel build instead.
No new dependencies (WorkManager was already there).

## v41
- Prompt Maker (Screens.kt PromptScreen): removed the New app / New website / New server tasks and their screens (NewAppSection, NewWebSection, Prompts.buildWeb, vm.web*/draftType, Prefs.webVercel). Ideas / Prompts.ideasPrompt / Prompts.buildPrompt are left in the code but nothing opens them.
- Tasks per project type (`Prompts.tasksFor(kind)`): APP and WEBSITE = Fix error, Add feature, Improve UI, Refactor, Release notes, Other; SERVER = Fix error, Add feature, Improve API, Refactor, Release notes, Other. A selected task that the project type does not have falls back to the first one. Website/server wording and Vercel line stay in `webBody`.
No new dependencies.

## v40
- Switch state is stored in Prefs (EncryptedSharedPreferences, survives cache clear and force stop): setupCreate (default on), setupKeys (default on), setupVercel (default OFF, no longer auto-on after Vercel login), webVercel (Prompt Maker, default OFF), promptDeliver (default on). Written on tap in AddScreen / PromptScreen / NewWebSection; read once when the screen opens. Rule: a switch never changes by itself. (Clearing app DATA still resets everything.)
No new dependencies.

## v39
- Vercel for websites and servers. `Vercel.kt` (REST: verify token, `link(repo)` creates/reuses the project from the GitHub repo, `latest(projectId)` newest production deployment, `errorText(id)` last build-log error lines). Login = personal access token (+ optional Team ID) in Settings -> Vercel (Prefs.vercelToken/vercelUser/vercelTeam, encrypted). The Vercel project id is stored per repo (`Prefs.vercelId(repo)`, no Room change, so no migration; backups do not carry it, relink from the app's Settings tab).
- Setup (AddScreen): "Deploy on Vercel" switch for Website/Server, ABOVE "Create GitHub repo" (on by default once logged in). `HubVM.setup(..., vercelOn)` links after the repo step and fills liveUrl with the *.vercel.app address. App Settings tab has a Vercel panel (Link / Unlink) for existing websites and servers.
- Status: `HubVM.vercelState(p)` (max every 4 s) maps the deployment state to lastStatus (READY passed, ERROR failed + log lines, BUILDING/QUEUED/INITIALIZING running, CANCELED ignored), so the v38 stages work unchanged (Website Deployed, Server Running via the endpoint probe). `refresh` no longer returns early for a Vercel project with no GitHub runs. Version label also follows "vNNN" in the Vercel commit message when there are no runs.
- Deploy: for a Vercel project no workflow is needed; `watchVercel` follows the deployment started by the push. APP flow untouched.
- Prompt Maker: tasks New app / New website / New server + the old ones. `Prompts.buildWeb` (name, idea, stack chips, Host on Vercel switch), `Prompts.make(..., vercel)` uses web/server wording when the selected project is not an app, `Prompts.delivery` for web/server (version shown in the site footer / GET /health). Server shows "Improve API" for the Improve UI task. Prompts are copied, never shown. "Set up this website/server" opens Setup with the type preset (`vm.draftType`).
- Needs: GitHub connected to Vercel (Vercel Account Settings -> Authentication) with access to the repo, otherwise linking says so. Vercel functions cannot hold WebSockets (the server prompt tells the AI).
No new dependencies.

## v38
- Project types. Project has `type` ("APP" | "WEBSITE" | "SERVER", text) and `liveUrl`. Room db version 2: `HubDb.build(ctx)` (Data.kt) is the ONLY way the db is opened (HubVM + SyncWorker) and holds MIGRATION 1->2 (ALTER TABLE ... DEFAULT 'APP' / ''). Never open Room elsewhere without it, and any new column needs migration 2->3.
- ProjectStatus.kt: ProjectType (+ defaultStack, icon), `Project.kind`, Stage + `stageOf(p, healthy)`, pill/color, `cleanUrl`, `hostOf`, `Health.probe`. Raw CI result stays in `lastStatus` (none/running/passed/failed); the stage is derived per type: APP Building/Passed(/Installed on Info), WEBSITE Building/Deployed, SERVER Building/Running. Extras: Failed, No builds, Offline (server deploy passed but endpoint not answering).
- Server: after a passed run, HubVM.probeServer checks `liveUrl` (https only, any answer < 500 = up; every 15 s until healthy, then 60 s; result in `vm.health`). Passed + no https URL = Running. Passed + not yet probed = Building.
- Website: when a run passes and liveUrl is blank, `gh.pagesUrl()` fills it (GitHub Pages API, retried every 10 min), saved in the db.
- Websites/servers: no APK release lookup, no checkInstalls, no hasUpdate, no auto-download (SyncWorker filters APP), no standard Android build.yml added on deploy (a log line says to include a workflow in the zip).
- UI: TypePicker (Setup + Settings), card pill uses `vm.buildStage`, Info/overview use `vm.stage`, Websites/Servers filter chips appear once one exists, Detail shows "Open site/server" instead of Install. Backup entries carry `kind` + `liveUrl` (format still 1; old files read as APP).
No new dependencies.

## v37
- Info page (Info.kt InfoScreen): hero card (icon, name, stack, version, status/installed/update pills), then InfoCard sections App / Build / Repository (label left, value right, wraps; package and repo tap to copy via vm.copy). Header title is just "Info".
- Files page (Files.kt FilesScreen): header "Files" with Refresh, FilesHead (search, home icon + breadcrumbs, totals or match count) fixed above a LazyColumn that resets to top on folder/search change; "Up one folder" row; delete icon is DeleteOutline with a 44dp target; DeleteFileDialog redesigned (red icon badge, file name + folder, workflow warning row, Cancel / red Delete). filesBrowser no longer emits the head item.
No new dependencies.

## v36
- Dashboard card (Screens.kt ProjectCard): buttons are Build / Deploy / orange Update (last, right end). The update pill reads "Updating" while downloading and "Updated" once the APK is saved and waiting to be installed (installed app only; a not-installed app keeps "Install").
No new dependencies.

## v35
- Dashboard card pills (Screens.kt ProjectCard): the download pill reads "Updating" (no percent; the button keeps ring + percent) and is the last pill, after status and installed. The "Update"/"Install" pill moved to the end too.
No new dependencies.

## v34
- Files is no longer a top tab on the app page (tabs: Info / Builds / Releases / Settings). The Files box on Info opens a Files page (Files.kt FilesScreen, route files/{id}) with the same browser (breadcrumbs, search, delete); Back goes up a folder first. Tapping a file still opens file/{id}?path=...
- Info (Screens.kt Detail tab 0) is a short box: status, installed, version, with a chevron. Tapping it opens InfoScreen (Info.kt, route info/{id}): name, package, version, stack, repo, status, installed, latest, saved apk, plus repo info (description, language, branch, size, issues/stars, access, pushed). installedLine() in Info.kt is shared by both.
No new dependencies.

## v33
- Pill (Screens.kt): max width 180dp, one line, ellipsis (last characters become "...") so text can never escape the border. HubTabs labels and breadcrumb crumbs also ellipsize.

## v32
- Rule from the user: UI words as SHORT as possible so nothing is cut or wrapped (no long labels). Only label text changed (Screens.kt, Files.kt, About.kt): Passed/Failed pills, "Install"/"Update"/"Latest" buttons, "37%" download text, tabs Info/Builds/Releases/Files/Settings, About tabs App/New/Crash/Help, dialog buttons "Cancel" / "Push vN" / "Push to X". Keep new labels this short.

## v31
1. Files page (Files.kt, GitHub.files/readBlob, HubVM.loadFiles/readFile, Screens.kt Detail): the Files tab is a folder browser (breadcrumbs, folders first with file counts, file icons + sizes, search across the whole repo, Back goes up one folder). Tapping a file opens route file/{id}?path=... (FileViewerScreen): path, size, line count, line numbers, find in file with highlight, copy, open on GitHub, delete (shared DeleteFileDialog); images shown as images; binary/too-large files get a clear message (contents API, 1 MB).
2. Overview: "Repository files" card (count + total size, opens the Files tab) with chips that open README / HANDOFF / manifest / build files directly. Files load quietly when Overview opens.
3. UI fixes from screenshots: HubTabs replaces TabRow (labels were cut to "Overvie"/"Settin"); Pill never wraps; card pills are a FlowRow; the card download button shows ring + percent only (the pill says Downloading); installed and current now says "Up to date" instead of "Update".
No new dependencies.

## v30
- OldVersionDialog is the v28 version again (same centered dialog style as ZipMismatchDialog); the v29 bottom sheet is removed. Everything else unchanged.
No new dependencies.

## v29
- OldVersionDialog (Screens.kt) redesigned as a ModalBottomSheet: big "your zip" vs "on GitHub" version numbers with an arrow and how many versions back, side-by-side Cancel / Push vN buttons. Same call sites and logic as v28. ChangeLog gets a v29 line.
No new dependencies.

## v28
Each item lists the files it touched.
1. Older/same version dialog (Screens.kt DeployScreen + OldVersionDialog, HubVM.pushedVersion/isOlder): Push + build asks before pushing a zip whose version is <= the last pushed vNNN (same style as the wrong-app dialog; runs after it, against the final target app). Quick "Push now" paths only offer newer zips, so they are unchanged.
2. Delete file on GitHub (GitHub.files/deleteFiles, HubVM.repoFiles/loadFiles/deleteFile, Screens.kt Detail "Files" tab): lists the default branch tree, search, bin per file, confirm dialog (extra warning for .github/workflows/). One commit through the git trees API (sha null), message "<next vNNN>: delete <path>", so the label follows and "Undo last push" can restore it.
3. Downloading state (Screens.kt dlPct/dlText, ProjectCard, Detail): while HubVM.dlProgress has the app, the Dashboard Update/Install button, a "Downloading N%" pill and the Detail Install button show a progress ring and the percentage.
4. About page (About.kt, Screens.kt Settings + NavHost route "about"): pages App / What's new (ChangeLog.list, update it every version) / Crash log / Help. Settings keeps one "About App Hub" row.
5. Install label (Screens.kt installLabel, BtnText): "Install" when the app is not on the phone, "Update" / "Update ready" when it is. BtnText now ends with an ellipsis instead of being cut.
No new dependencies.

## v27
Each feature lives in its own part so it is easy to see which files it touched.
1. Faster login (HubVM.kt, Screens.kt Settings/Dashboard): "Finishing login…" is set the moment the apphub://oauth link arrives or the app resumes with a pending device code (HubVM.loginBusy), before any network call. The device code is checked at once on resume; if it is not approved yet the text goes back to "Approve on GitHub…" after 2.5 s. startLogin already uses the web flow whenever a client secret is saved.
2. Backup (Backup.kt, HubVM.exportBackup/readBackup/backupClashes/applyBackup, Settings): JSON via the system file picker with apps (name, repo, package, version, stack) and Prefs.keys(repo) per app. Export warns about the signing keys; import shows how many apps/keystores would be overwritten and asks first. Apps are matched by repo.
3. Notifications (Notifier.kt, Prefs.notifyOn / notifiedRun, HubVM.notifyBuild, ApkStore.fetch announce): one when a build finishes (passed/failed, app name; only builds started in the last hour; the first sync only remembers) and one when an update APK finished downloading ("tap to install", background/auto downloads only). Tap opens detail/<id> or install/<id> (MainActivity "route" extra -> HubVM.openRoute). Switch in Settings; permission asked once at start and again when the switch is turned on.
4. Offline / rate limit (GitHub.offline, GitHub.limitUntil, HubVM.netNote): amber note on the Dashboard ("Offline, showing saved data" / "GitHub limit reached, retrying at HH:MM"). Recomputed after every sync, so it clears itself. autoSync waits quietly until the limit ends.
5. Rollback (ApkStore.prev/archive, Prefs.prevTag, HubVM.installPrevious): when a newer release arrives the older APK is moved to app_<id>_prev.apk (one extra file). Install page: "Previous build (tag)" button with a downgrade warning dialog. Installing it resets installTry so the newer saved APK is never marked installed by mistake.
6. Storage + live sync (Settings, HubVM.syncSecs/live/syncDelayMs/clearApks): shows space used by saved APKs, "Clear saved APKs" (confirm). Live sync = 3 s / 10 s / off (Prefs.syncSecs); the App loop and the Detail/Install loops obey it. Off still syncs once on open/resume and on the Refresh button.
7. Release notes (HubVM.notesFor): empty release notes use the message of the build run with the same number (release tags are build-N), else "Bug fixes and performance improvements.". Only the last 15 runs are known.
8. Polish (Screens.kt EmptyState, Settings About, CrashLog.kt): friendly empty states (Dashboard filters, Builds, Releases), About (version + build, GitHub user, links), last crash saved to filesDir/crash.txt with "Copy crash log" / "Clear" (CrashLog.install in MainActivity.onCreate).
Also: "Zip name does not match" is now ZipMismatchDialog (warning icon, zip name, named-after vs selected cards, three full-width buttons).
No new dependencies.

## v26
- Fix: "app stops after 5-7 hours, have to log in again". GitHub App user tokens expire after 8 hours; App Hub kept only access_token and ignored refresh_token / expires_in, and a 401 just looked like "no data". Now GitHub.saveToken stores refresh_token + expiry (Prefs.refreshToken / tokenExpiry); an OkHttp interceptor refreshes 5 min before expiry and an Authenticator refreshes and retries once on 401 (refresh needs the client secret for GitHub Apps: Settings -> GitHub). If GitHub rejects the refresh (or the token was revoked and there is none) GitHub.authLost -> HubVM.sessionExpired -> red banner on the Dashboard and "Log in again" in Settings; live sync pauses instead of failing silently. Classic OAuth App tokens do not expire. For a GitHub App you can also switch off "Expire user authorization tokens" in the app's settings on GitHub.

## v25
- Live data: App() runs HubVM.autoSync() every 3 s while the app is on screen (and on resume / Dashboard open): runs, newest release, installed check, zip scan. Silent, no spinners (only the Refresh button shows one). The 10-minute staleness gate is gone; the snapshot cache (SnapCache) is only the first paint. GitHub.get caches ETags: a 304 answer is free against the rate limit, so polling is cheap. The error text is read once per failed run (errRun). Detail and Install screens also poll loadDetails every 3 s.
- GitHub.latest now picks the newest APK release by published_at from /releases?per_page=20 (/releases/latest orders by commit date and kept returning a very old build); releases() is sorted the same way. GitHub.Rel carries time + sizeKb.
- Download progress lives in the ViewModel (dlProgress / dlError, HubVM.downloadTracked / download), not in the Install screen, so leaving and reopening the page never shows "Download failed" and the download keeps running. Interrupted downloads keep app_<id>_<tag>.part and resume with HTTP Range (GitHub.download, ApkStore.fetch); a finished file must parse as an APK. ApkStore.fetch waits if the worker/UI is already downloading the same app.
- Install screen redesigned: status card (up to date / update / not installed), from -> to, release age + size, progress with MB, Resume, Open app, What's new, Recent builds.
- Version/push conflicts: pending() is true only while a zip in Downloads is newer than the last pushed build; the label always follows the newest pushed "vNNN:" commit (never kept ahead; manualVer is no longer used). With "Follow builds automatically" on, the Version field and +1 are disabled in app settings. A repeated +1 / re-push can no longer show "+ Push v27" when v24 is the latest build.
- Installed check: one "installed:" line in Overview (yes + version + build / no / unknown). Apps installed outside App Hub are judged by versionCode of the saved APK (behind map, checkInstalls); the latest APK is saved in the background also for apps that are on the phone with an unknown build.
- Prompt Maker step 4 is now a text box ("what do you want changed"); the prompt is built from it and only copied, not shown (HubVM.promptNote, Prompts.make(change)).
- Setup project: no autofill at all (no repo/package/version defaults, no quick-fill chips); placeholders only. Package is found later automatically (v21). Version blank = v1.

## v24
- Zip suggestion is by name and asked once: HubVM.projectFor(zipName) picks the ONE project a zip is named after (app name, then repo name; Downloads.baseOf strips _vN and " (1)"). scanZips only offers a zip to that project, and zipKey is per file, so duplicate/same-named projects no longer ask twice.
- Deploy screen: choosing a zip (or the suggestion) selects the project it is named after. Push + build with a zip whose name does not match the selected project opens a dialog (push to the named project / push to the selected one anyway / cancel) instead of silently pushing to another repo.

## v23
- Version label = newest "vNNN:" commit among the last 15 runs (HubVM.refresh). Merge commits, cancelled runs, re-runs and dispatches no longer bump it (v22 push + merge showed v23 before). The label may only stay ahead of the pushed version when set by hand (Prefs.manualVer, set in HubVM.update / bump). Deploy sets pushedV right after a successful push, so no "+ Push vN" flash.
- Cancelled runs (conclusion=cancelled) are kept out of the shown runs/status (GitHub.Run.cancelled), so a cancelled build is not "failed".
- Device flow cannot redirect (GitHub only redirects the web flow, which needs the client secret; PKCE does not remove that). Instead: after approval is detected while App Hub is in the background (HubVM.inForeground), it tries startActivity(MainActivity) and posts a "Logged in - tap to return" notification (POST_NOTIFICATIONS asked once at start).

## v22
- Login return: manifest has launchMode=singleTask + intent-filter for apphub://oauth. With a client secret (Settings -> GitHub, or -PAPPHUB_CLIENT_SECRET) login uses the web flow with PKCE: browser -> GitHub -> apphub://oauth -> App Hub exchanges the code (HubVM.handleCallback, GitHub.exchangeCode). The OAuth app's Callback URL must be apphub://oauth. Without a secret the device flow is used.
- Device flow fixes: pending code stored in Prefs (pending*), polling restarts and checks at once on every onResume (HubVM.resumeLogin), expiry honored, readable errors (device_flow_disabled, access_denied, expired, wrong client id), no double polls, Cancel button, bad token cleared if /user fails.
- Never name a function setX next to a property x (use updateClientSecret).

## v21
- Fix: the installed check silently skipped every app with no package name (e.g. the QuestBlock preset, apps added without one), so nothing showed. checkInstalls now fills a missing package name from the saved APK (PackageManager.getPackageArchiveInfo), else from applicationId in the repo's app/build.gradle(.kts) (GitHub.packageOf, once per session per app), saves it on the project, then checks the phone. It also runs after every dashboard sync and when a project opens, with a guard against overlapping runs. Cards without a known package show "? Package unknown"; the package can still be set by hand in the app's Settings tab.

## v20
- True background download (ApkStore.kt): SyncWorker (WorkManager, ~hourly, runs with the app closed) checks each app's latest release and saves its APK in filesDir/apks when it is newer than the installed marker and not saved yet. Constraints: Wi-Fi (unmetered) by default, battery not low. Needs a GitHub login. Settings -> "Background download": Auto-download switch + Wi-Fi only switch (Prefs.autoDownload / wifiOnly, HubVM.updateAutoDownload / updateWifiOnly). Sync.schedule runs on every app start and when a switch changes.
- ApkStore.fetch is shared by UI and worker (one download per app at a time). The worker writes only Prefs; checkInstalls copies the saved tag into HubVM.cached on resume. Installing is never automatic: the Dashboard banner / Install screen stays the way in.
- Dependency: androidx.work:work-runtime-ktx:2.9.1.

## v19
- Latest APK is saved in the app's own storage (filesDir/apks, FileProvider files-path), one file per app, replaced by each newer release. It is no longer deleted after install (Reinstall needs no download); only a newer release or a missing file drops it. Old cacheDir/apks is wiped.
- When a sync finds a release newer than the installed one, its APK is downloaded in the background (HubVM.refresh) so Update is one tap.
- Follow-up: Dashboard banner for every saved APK that is not installed (installer ignored/skipped, or never opened), with an Install button. Detected via installedTag != cachedTag.
- Installed check: checkInstalls (every resume) reads PackageManager per app with a package name -> HubVM.onPhone (versionName, "" = not installed). Card pill "On phone x / Not installed", Overview line "on phone", and an uninstalled app clears its installed marker. Apps without a package name stay "unknown".

## v18
- Fix build (the v17 fix did not work: a private setter still generates setAutoInstall(Boolean) on the JVM). The function is now HubVM.updateAutoInstall(v); autoInstall keeps its private set. Never name a function setX next to a property x.
- Local snapshot (SnapCache.kt, cacheDir/snap/<id>.json): last build runs, latest release tag, repo info, commits, releases and error text per app. Loaded into the VM maps at start, so names, version labels, status and update flags show instantly. Version label/status/name were already stored in Room.
- Sync only when stale (Prefs.syncedAt/detailsAt, 10 min), or while a build is running. Dashboard open, project open and Deploy open use the cache; the Refresh button, login, Build/Undo and the deploy watcher still force a sync. A failed/offline fetch never overwrites the cache.

## v17
- Fix build: HubVM.autoInstall now has a private setter, so its generated setAutoInstall(Boolean) no longer clashes with the setAutoInstall(v) function (Platform declaration clash). Screens still call vm.setAutoInstall(it).

## v16
- Build watch: polls every 4 s with one small runs call (full refresh only when the run ends), real elapsed seconds, up to ~9 min.
- Push speed: UTF-8 text files go inline in the tree request (no blob call each); only binary/big files are uploaded as blobs, 8 at a time. First pushes are now a handful of calls.
- Deploy: "Preview changes" lists new/changed files without pushing (GitHub.pushFiles dryRun). Settings tab: "Undo last push" (GitHub.revertLast) adds a commit restoring the previous tree, history kept; message reuses the previous "vNNN:" label so the version follows. Branches/merge are not in the app.

## v15
- Push parity with termux/release.sh: the Deploy log lists every file like git (+ new, ~ changed, up to 30), the commit sha and branch, uploads blobs 6 at a time, then follows the CI run live (elapsed time), prints compile errors on failure (Copy errors / AI prompt buttons on the Deploy screen) and, when the build passes, downloads the APK into the cache and opens the installer. Switch "Open the installer when the build passes" (Prefs.autoInstall, default on). Android may block the installer if App Hub is in the background; the Install this build button still works.
- Project picker is a bottom sheet (ModalBottomSheet) instead of the small floating menu.
- Prompt delivery rules also ask the AI to list every file it created or changed.

## v14
- Dashboard card and project header: action buttons (Build / Update / Deploy) use compact padding and single-line labels (BtnText), so three buttons in a row no longer wrap letter by letter.

## v13
- Deploy: a zip is only offered (card + "Push?" dialog) while it is new. Once pushed by any route (Push now, Use this zip + Push + build, picker) it is marked seen and the card disappears; with nothing new, only "Choose a zip" shows.
- Preset renamed Anywall -> Videowall (repo videowall).

## v12
- Deploy screen: the "Allow" file-access card is gone (auto zip detection still works once All-files access is granted in system settings); the picker card reads "Choose a zip" when auto-detect is off.
- HubSwitch: all toggles use a clear style (green track + dark thumb on, outlined grey off).
- Videowall preset added (repo videowall; it was called Anywall by mistake before v13). Presets are now offered once each (Prefs.presetSeen), so existing installs get Videowall on update and removed apps stay removed; "Restore presets" still re-adds everything.

## v11
- "What's new" fallback: releases with no notes (CI releases have none) show "Bug fixes and performance improvements." like Google Play (DEFAULT_NOTES / notesOrDefault in Data.kt). Used on the Install screen and the Releases tab.
- APK cache tracking (HubVM): download goes to cache/apks/app_<id>.apk via a .part file, prefs remember the cached release tag. Install is only marked done when Android confirms it (package lastUpdateTime >= time the installer was opened, checked in onResume via checkInstalls). Apps without a package name are marked installed on tap (cannot be verified).
- Auto clear: cached APK is deleted when it is installed, when a newer release tag appears (checked on every refresh), when the file is missing, or when the app is deleted. Stray files in cache/apks are swept on resume.
- Install screen: "Install downloaded APK" reuses the cached file without re-downloading; Overview shows "cached: <tag>" while waiting. GitHub.download now fails on HTTP errors instead of saving an error page.
- Manifest: QUERY_ALL_PACKAGES so the install check can see the managed package on Android 11+.

## v10
- Auto zip detection (Downloads.kt): newest Name_vN.zip whose name matches the app name OR repo name (non-alphanumerics ignored). Needs All-files access (MANAGE_EXTERNAL_STORAGE) on Android 11+. A dialog names the zip and asks to push; "Skip this zip" remembers that file; the + icon also appears for a waiting zip. Re-scans on resume.
- Push is now incremental (GitHub.pushFiles): compares git blob hashes with the repo tree, uploads only new/changed files, no commit if nothing changed.

## v9
- Per-app Settings tab rebuilt (AppSettings): edit name/repo/package/stack/version with one Save, auto-follow-builds switch, trigger build, re-send signing secrets, keystore backup, pin, reset installed marker, danger zone.
- Global Settings: OAuth client id field removed (default stays in build config).

## v8
- Prompt Maker: new "New app" task. Ideas first (built-in idea cards per category + "ideas prompt" that makes the AI suggest 8 and wait for a pick), then a build prompt (plan first, CI, preview visuals first, zip + version rules). "Set up this app in App Hub" prefills the Add screen.
- Full build-log viewer (Builds tab -> tap build -> View full log): search, Last 200, Errors only, Copy.
- Deploy release note is now used in the commit message.

## v7
- Termux removed from the app (no RUN_COMMAND, no Command tab, no Termux settings). repo's termux/ folder is left untouched.
- Deploy tab: pick zip with the system picker (Zip.kt), push as ONE commit via Git Data API (GitHub.pushFiles), CI builds, app watches the run and offers Install.
- Version label auto-follows every new run (HubVM.refresh): push-event run with "vNNN:" message sets it, any other run (web edit, dispatch, re-run) bumps it by one. Pending "+" icon = label ahead of last pushed version.
- Project detail fetches repo info, commits, releases and per-step build logs in-app (no browser).
- OAuth client id default is set in build.gradle.kts (APPHUB_CLIENT_ID), Prefs falls back to it when blank.
- QuestBlock preset starts at v285.
