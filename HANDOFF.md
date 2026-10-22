# App Hub — handoff
Android app (Kotlin, Compose, Room, OkHttp). Package com.krishna.apphub. Manages build/push/fix/deploy for the user's apps from a phone.
- Screens: Dashboard, Project detail (build history + error card), Prompt Maker, Command Maker, Install/Update, Settings, Setup wizard.
- GitHub: OAuth device flow (Client ID entered in Settings), repo create, signing keystore, Actions secrets (libsodium sealed box).
- Termux: Command Maker bootstraps termux/setup.sh, termux/release.sh and .github/workflows/build.yml into a project if missing, then runs them (same scripts as BgBrowser).
- Secrets used by CI: KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_PASSWORD, KEY_ALIAS. build.gradle.kts reads them via env vars / keystore.properties.
- No gradlew in repo; CI installs Gradle 8.10.2. No compiler in chat: GitHub CI is the first build, paste errors back.
- Update flow: zip AppHub_vN.zip with folder AppHub_vN/, bump versionCode by 1 and versionName N.0, never reuse a name.
- Not built yet: Files tab, Updates filter, background build notifications.
