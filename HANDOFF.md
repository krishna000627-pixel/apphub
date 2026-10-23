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
