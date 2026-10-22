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
