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
