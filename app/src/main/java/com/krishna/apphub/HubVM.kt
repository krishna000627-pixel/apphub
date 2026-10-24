package com.krishna.apphub

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class HubVM(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    val gh = GitHub(prefs)
    private val dao = HubDb.build(app).dao()
    private val lock = Mutex()

    val projects = dao.all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val runs = mutableStateMapOf<Long, List<GitHub.Run>>()
    val errors = mutableStateMapOf<Long, String>()
    val log = mutableStateListOf<String>()
    val latest = mutableStateMapOf<Long, String>()
    val latestRel = mutableStateMapOf<Long, GitHub.Rel>()   // newest APK release per app (tag, notes, size, time)
    val dlProgress = mutableStateMapOf<Long, Float>()       // 0..1 while an APK downloads; kept here so leaving the Install screen never loses it
    val dlError = mutableStateMapOf<Long, String>()         // last real download failure (cleared on the next try)
    val behind = mutableStateMapOf<Long, Boolean>()         // installed outside App Hub and older than the newest saved APK
    val pins = mutableStateMapOf<Long, Boolean>()
    val installed = mutableStateMapOf<Long, String>()
    val pushedV = mutableStateMapOf<Long, String>()
    /** Set by MainActivity (onResume/onPause): true while App Hub is on screen. */
    @Volatile var inForeground = false
    val autoV = mutableStateMapOf<Long, Boolean>()
    val infos = mutableStateMapOf<Long, GitHub.Info>()
    val commits = mutableStateMapOf<Long, List<GitHub.Commit>>()
    val releases = mutableStateMapOf<Long, List<GitHub.Release>>()
    val jobs = mutableStateMapOf<Long, List<GitHub.Job>>()
    val health = mutableStateMapOf<Long, Boolean>()   // v38: last endpoint check of a server (absent = not checked yet)
    private val probeAt = mutableMapOf<Long, Long>()
    private val urlTry = mutableMapOf<Long, Long>()
    val cached = mutableStateMapOf<Long, String>()    // release tag of the APK waiting in the cache, per app
    val zipFound = mutableStateMapOf<Long, File>()    // newest unpushed zip per app
    val zipLatest = mutableStateMapOf<Long, File>()   // newest zip per app, pushed or not
    val zipDismissed = mutableStateListOf<String>()   // "ask me later" for this session
    var refreshing by mutableStateOf(false)

    // ---- v27 state ----
    var notifyOn by mutableStateOf(prefs.notifyOn)          // feature 3: build / download notifications
        private set
    var syncSecs by mutableIntStateOf(prefs.syncSecs)        // feature 6: live sync every 3 s, 10 s, or 0 = off
        private set
    var netNote by mutableStateOf("")                        // feature 4: amber Dashboard note ("" = everything fine)
    var loginBusy by mutableStateOf(false)                   // feature 1: "Finishing login…" is showing
    var openRoute by mutableStateOf<String?>(null)           // feature 3: screen to open after a notification tap
    var pendingBackup by mutableStateOf<Backup.Data?>(null)  // feature 2: a backup file waiting for the import confirmation

    var zipPack by mutableStateOf<Zipper.Pack?>(null)
    var deploying by mutableStateOf(false)
    var autoInstall by mutableStateOf(prefs.autoInstall)
        private set
    var autoDownload by mutableStateOf(prefs.autoDownload)
        private set
    var wifiOnly by mutableStateOf(prefs.wifiOnly)
        private set
    private var deployGen = 0
    val deployLog = mutableStateListOf<String>()

    var login by mutableStateOf(prefs.login)
    var sessionExpired by mutableStateOf(false)   // GitHub rejected the token and it could not be refreshed: log in again
    var userCode by mutableStateOf<String?>(null)
    var loginMsg by mutableStateOf("")
    var clientSecret by mutableStateOf(prefs.clientSecret)
    var selId by mutableLongStateOf(0L)
    var task by mutableStateOf("Fix error")

    // ---- v39: Vercel ----
    val vercel = Vercel(prefs)
    var vercelUser by mutableStateOf(prefs.vercelUser)
    var vercelTeam by mutableStateOf(prefs.vercelTeam)
    var vercelBusy by mutableStateOf(false)
    var vercelMsg by mutableStateOf("")
    private var vercelRev by mutableIntStateOf(0)            // bumped when a link changes, so screens reading isVercel redraw
    private val vercelAt = mutableMapOf<Long, Long>()
    private val vercelLast = mutableMapOf<Long, Pair<String, String>>()   // last (status, error) per project
    private val vercelErrFor = mutableMapOf<Long, String>()   // deployment whose error text is in vercelLast
    private val vercelVer = mutableMapOf<Long, String>()      // newest "vNNN" seen in a Vercel commit message

    // ---- v43: Supabase and Render connections (read only: they list your projects and services) ----
    val supabase = SupabaseApi(prefs)
    var supabaseOn by mutableStateOf(prefs.supabaseToken.isNotBlank())
    val supabaseProjects = mutableStateListOf<SupabaseApi.Proj>()
    var supabaseMsg by mutableStateOf("")
    var supabaseBusy by mutableStateOf(false)
    val render = RenderApi(prefs)
    var renderUser by mutableStateOf(prefs.renderUser)
    val renderServices = mutableStateListOf<RenderApi.Svc>()
    var renderMsg by mutableStateOf("")
    var renderBusy by mutableStateOf(false)

    fun supabaseLogin(token: String, onDone: () -> Unit) = viewModelScope.launch {
        if (token.isBlank()) { supabaseMsg = "Paste a Supabase token first"; return@launch }
        supabaseBusy = true; supabaseMsg = ""
        val (list, err) = supabase.projects(token.trim())
        supabaseBusy = false
        if (list == null) { supabaseMsg = "Supabase: $err"; return@launch }
        prefs.supabaseToken = token; supabaseOn = true
        supabaseProjects.clear(); supabaseProjects.addAll(list)
        onDone(); toast("Supabase connected")
    }

    fun supabaseLogout() { prefs.supabaseToken = ""; supabaseOn = false; supabaseProjects.clear(); supabaseMsg = "" }

    /** Silent refresh of the project list (no spinner); the list is only replaced when it changed. */
    var supabaseLoading by mutableStateOf(false)
    fun loadSupabase() = viewModelScope.launch {
        if (!supabaseOn) return@launch
        supabaseLoading = true
        val (list, err) = supabase.projects()
        supabaseLoading = false
        if (list == null) { supabaseMsg = "Supabase: $err"; return@launch }
        supabaseMsg = ""
        if (supabaseProjects.toList() != list) { supabaseProjects.clear(); supabaseProjects.addAll(list) }
    }

    fun renderLogin(key: String, onDone: () -> Unit) = viewModelScope.launch {
        if (key.isBlank()) { renderMsg = "Paste a Render API key first"; return@launch }
        renderBusy = true; renderMsg = ""
        val (who, err) = render.owner(key.trim())
        renderBusy = false
        if (who == null) { renderMsg = "Render: $err"; return@launch }
        prefs.renderKey = key; prefs.renderUser = who; renderUser = who
        onDone(); toast("Render connected as $who")
        loadRender()
    }

    fun renderLogout() { prefs.renderKey = ""; prefs.renderUser = ""; renderUser = ""; renderServices.clear(); renderDeploys.clear(); renderMsg = "" }

    // ---- v45: Render page (last deploy per service, manual deploy) and the Supabase legacy API test ----
    val renderDeploys = mutableStateMapOf<String, String>()
    var renderLoading by mutableStateOf(false)
    fun refreshRender() = viewModelScope.launch {
        if (renderUser.isBlank()) return@launch
        renderLoading = true
        val (list, err) = render.services()
        if (list == null) renderMsg = "Render: $err" else {
            renderMsg = ""
            if (renderServices.toList() != list) { renderServices.clear(); renderServices.addAll(list) }
            list.take(25).forEach { sv -> val st = render.lastDeploy(sv.id); if (st.isNotBlank()) renderDeploys[sv.id] = st }
        }
        renderLoading = false
    }
    fun renderDeploy(sv: RenderApi.Svc) = viewModelScope.launch {
        val err = render.deploy(sv.id)
        if (err.isBlank()) { toast("Deploy started"); renderDeploys[sv.id] = "build_in_progress" } else toast("Render: $err")
    }

    // ---- v46: provider pages (GitHub, Vercel, Supabase, Render) list what you have and add it as a project in one tap ----
    val ghRepos = mutableStateListOf<GitHub.Repo>()
    var ghLoading by mutableStateOf(false)
    var ghMsg by mutableStateOf("")
    fun loadRepos() = viewModelScope.launch {
        if (login.isBlank() || sessionExpired) return@launch
        ghLoading = true
        val (list, err) = gh.repos()
        ghLoading = false
        if (list == null) { ghMsg = "GitHub: $err"; return@launch }
        ghMsg = ""
        if (ghRepos.toList() != list) { ghRepos.clear(); ghRepos.addAll(list) }
    }

    val vcProjects = mutableStateListOf<Vercel.Proj>()
    var vcLoading by mutableStateOf(false)
    var vcMsg by mutableStateOf("")
    fun loadVercelProjects() = viewModelScope.launch {
        if (vercelUser.isBlank()) return@launch
        vcLoading = true
        val (list, err) = vercel.projects()
        vcLoading = false
        if (list == null) { vcMsg = "Vercel: $err"; return@launch }
        vcMsg = ""
        if (vcProjects.toList() != list) { vcProjects.clear(); vcProjects.addAll(list) }
    }

    /** Adds an existing repo as a project, no setup form. A Vercel project id (websites and servers) links it to Vercel too. */
    fun importProject(name: String, repo: String, type: ProjectType, liveUrl: String = "", vercelId: String = "") = viewModelScope.launch {
        if (dao.all().first().any { it.repo.equals(repo, true) }) { toast("Already added"); return@launch }
        val proj = Project(name = name, repo = repo, version = "v1", stack = type.defaultStack, type = type.name,
            liveUrl = if (type == ProjectType.APP) "" else cleanUrl(liveUrl))
        val id = dao.save(proj)
        if (vercelId.isNotBlank() && type != ProjectType.APP) { prefs.setVercelId(repo, vercelId); vercelRev++ }
        selId = id
        copy(Prompts.adopt(proj), quiet = true)   // v49: the adopt prompt is ready to send to Claude
        toast("Added $name. Adopt prompt copied")
    }

    fun loadRender() = viewModelScope.launch {
        if (renderUser.isBlank()) return@launch
        val (list, err) = render.services()
        if (list == null) { renderMsg = "Render: $err"; return@launch }
        renderMsg = ""
        if (renderServices.toList() != list) { renderServices.clear(); renderServices.addAll(list) }
    }

    // ---- v42: environment variables on Vercel (names only; a value is sent once and never kept here) ----
    val envVars = mutableStateListOf<Vercel.Env>()
    var envMsg by mutableStateOf("")
    var envBusy by mutableStateOf(false)
    // ---- v42: secrets found in a zip about to be pushed (the Deploy screen asks) ----
    var secretAsk by mutableStateOf<SecretAsk?>(null)
    var typeAsk by mutableStateOf<TypeAsk?>(null)   // v49
    // ---- v42: server down / up alerts ----
    var serverAlerts by mutableStateOf(prefs.serverAlerts)

    // new-app prompt maker (kept here so it survives switching tabs)
    var ideaCat by mutableStateOf("Productivity")
    var ideaName by mutableStateOf("")
    var ideaPitch by mutableStateOf("")
    var ideaNotes by mutableStateOf("")
    var draftName by mutableStateOf("")
    var promptNote by mutableStateOf("")   // "what do you want changed" text of the Prompt Maker, kept across tabs

    // full build-log viewer
    var logTitle by mutableStateOf("")
    var logLines by mutableStateOf<List<String>>(emptyList())
    var logLoading by mutableStateOf(false)

    // ---- local snapshot: last known GitHub data, only for the first paint. Live data replaces it within 3 s (autoSync). ----
    private val snapDir get() = File(getApplication<Application>().cacheDir, "snap")
    private val snapped = mutableSetOf<Long>()

    private fun loadSnaps() {
        snapDir.listFiles()?.forEach { f ->
            val id = f.nameWithoutExtension.toLongOrNull() ?: return@forEach
            val s = SnapCache.load(snapDir, id) ?: return@forEach
            snapped += id
            runs[id] = s.runs; errors[id] = s.error
            if (s.latest.isNotBlank()) latest[id] = s.latest
            s.info?.let { infos[id] = it }
            commits[id] = s.commits; releases[id] = s.releases
        }
    }

    private fun saveSnap(p: Project) {
        SnapCache.save(snapDir, p.id, SnapCache.Snap(runs[p.id] ?: emptyList(), latest[p.id] ?: "", errors[p.id] ?: "",
            infos[p.id], commits[p.id] ?: emptyList(), releases[p.id] ?: emptyList()))
        snapped += p.id
    }

    init {
        loadSnaps()
        viewModelScope.launch {
            if (prefs.seeded) listOf("questblock", "bgbrowser", "apphub").forEach { prefs.setPresetSeen(it) }  // already offered before v12
            seedMissing(onlyNew = true); prefs.seeded = true
            fixOwners()
        }
    }

    private fun n(v: String) = Commands.num(v).toIntOrNull() ?: 0
    private fun vOf(m: String) = Regex("^v\\d+").find(m)?.value

    fun isPinned(id: Long) = pins[id] ?: prefs.pinned(id)
    fun togglePin(p: Project) { val v = !isPinned(p.id); pins[p.id] = v; prefs.setPinned(p.id, v) }
    fun isAuto(id: Long) = autoV[id] ?: prefs.autoVer(id)
    fun setAuto(id: Long, v: Boolean) { autoV[id] = v; prefs.setAutoVer(id, v) }
    fun clearInstalled(id: Long) { installed[id] = ""; prefs.setInstalled(id, ""); toast("Installed marker cleared") }

    fun installedTag(id: Long) = installed[id] ?: prefs.installed(id)
    fun markInstalled(id: Long, tag: String) { installed[id] = tag; prefs.setInstalled(id, tag) }
    fun hasUpdate(p: Project): Boolean {
        if (p.kind != ProjectType.APP) return false   // websites and servers have no APK to update
        val l = latest[p.id] ?: return false
        val i = installedTag(p.id)
        if (i.isNotBlank()) return i != l
        return behind[p.id] == true   // installed outside App Hub: judged by the versionCode of the saved APK
    }

    /** v38: what the build result means for this project's type (cards use this: an app shows Passed, its installed pill sits next to it). */
    fun buildStage(p: Project): Stage = stageOf(p, health[p.id])
    /** v38: like [buildStage], and an app that passed and is on the phone with the newest build reads Installed (Info page). */
    fun stage(p: Project): Stage {
        val st = buildStage(p)
        return if (p.kind == ProjectType.APP && st == Stage.PASSED && isInstalled(p) == true && !hasUpdate(p)) Stage.INSTALLED else st
    }

    // ---- APK cache: downloaded -> installed -> cleared ----
    // Saved in the app's own storage (not the system cache), so Android does not wipe the latest APK. One file per app, replaced by each newer release.
    private val apkDir get() = ApkStore.dir(getApplication<Application>())
    private val downloading = mutableSetOf<Long>()
    val onPhone = mutableStateMapOf<Long, String>()   // versionName of the app on this phone, "" = not installed (apps without a package name are absent)
    fun apkFile(p: Project) = ApkStore.file(getApplication<Application>(), p.id)
    fun cachedTag(id: Long) = cached[id] ?: prefs.cachedTag(id)
    fun hasCached(p: Project) = cachedTag(p.id).isNotBlank() && apkFile(p).exists()

    /** true / false once checked against the phone, null when unknown (no package name or not checked yet). */
    fun isInstalled(p: Project): Boolean? = onPhone[p.id]?.let { it.isNotEmpty() }
    /** A saved APK that is not installed yet (downloaded but ignored, or the installer was skipped). */
    fun waitingInstall(p: Project): Boolean {
        val tag = cachedTag(p.id)
        return tag.isNotBlank() && hasCached(p) && installedTag(p.id) != tag
    }
    fun installSkipped(p: Project) = prefs.installTry(p.id) > 0L

    /** Downloads the release APK into the cache (via a .part file so a broken download is never installed). */
    suspend fun fetchApk(p: Project, r: GitHub.Rel, announce: Boolean = false, onProgress: (Long, Long) -> Unit): Boolean {
        val ok = ApkStore.fetch(getApplication<Application>(), gh, prefs, p, r, announce, onProgress)
        if (ok) cached[p.id] = r.tag
        return ok
    }

    /**
     * Download with progress kept here in the ViewModel, so it keeps running and showing when the Install screen is left and reopened.
     * An interrupted download keeps its partial file and resumes on the next try.
     */
    private suspend fun downloadTracked(p: Project, r: GitHub.Rel, announce: Boolean = false, onPct: (Int) -> Unit = {}): Boolean {
        if (dlProgress.containsKey(p.id)) {   // already running (started elsewhere): wait for it
            while (dlProgress.containsKey(p.id)) delay(300)
            return cachedTag(p.id) == r.tag && hasCached(p)
        }
        dlError.remove(p.id); dlProgress[p.id] = 0f
        var last = -1
        val ok = try {
            fetchApk(p, r, announce) { d, t ->
                if (t > 0) { val pc = (d * 100 / t).toInt(); if (pc != last) { last = pc; dlProgress[p.id] = pc / 100f; onPct(pc) } }
            }
        } finally { dlProgress.remove(p.id) }
        if (ok) dlError.remove(p.id) else dlError[p.id] = "Download interrupted. Tap Resume to continue where it stopped."
        return ok
    }

    /** Install button: download (or resume), then open the installer. Runs in the ViewModel, not in the screen. */
    fun download(ctx: Context, p: Project, r: GitHub.Rel, thenInstall: Boolean) = viewModelScope.launch {
        if (dlProgress.containsKey(p.id)) return@launch
        val ok = downloadTracked(p, r)
        if (ok && thenInstall) installCached(ctx, p)
    }

    /** Opens the system installer for the cached APK. The app is marked installed only once Android confirms it (checkInstalls). */
    fun installCached(ctx: Context, p: Project) {
        if (!hasCached(p)) { dropCache(p); toast("Cached APK is gone, download again"); return }
        prefs.setInstallTry(p.id, System.currentTimeMillis())
        // Without a package name the install can't be verified, so trust the tap (the cache is still cleared on the next release).
        if (p.packageName.isBlank()) markInstalled(p.id, cachedTag(p.id))
        gh.install(ctx, apkFile(p))
    }

    fun dropCache(p: Project) {
        apkFile(p).delete(); File(apkFile(p).path + ".part").delete()   // partial downloads are kept: the next try resumes them
        prefs.setCachedTag(p.id, ""); prefs.setInstallTry(p.id, 0L); cached.remove(p.id)
    }

    /** Drops the saved APK only when a newer release exists or the file is gone. An installed one stays, so Reinstall needs no download. */
    private fun pruneOne(p: Project) {
        val tag = cachedTag(p.id); if (tag.isBlank()) return
        val newer = latest[p.id]?.let { it != tag } == true
        if (newer) ApkStore.archive(getApplication<Application>(), prefs, p.id)   // v27: the older APK becomes the "Previous build"
        if (newer || !apkFile(p).exists()) dropCache(p)
    }

    /** Confirms installs that finished (package updated after the installer was opened), then clears used or stale APKs. */
    private val checking = java.util.concurrent.atomic.AtomicBoolean(false)
    private val detecting = mutableSetOf<Long>()   // apps whose package name was already looked up online this session

    private val apkCodes = mutableMapOf<Long, Pair<String, Long>>()   // app id -> (release tag, versionCode of the saved APK)
    private fun apkCodeOf(id: Long, tag: String, pm: PackageManager): Long? {
        apkCodes[id]?.let { if (it.first == tag) return it.second }
        val p = projects.value.firstOrNull { it.id == id } ?: return null
        val info = runCatching { pm.getPackageArchiveInfo(apkFile(p).path, 0) }.getOrNull() ?: return null
        return PackageInfoCompat.getLongVersionCode(info).also { apkCodes[id] = tag to it }
    }

    /** Package name of an app that has none yet: from its saved APK, else from applicationId in the repo's app/build.gradle(.kts). */
    private suspend fun detectPackage(p: Project, pm: PackageManager): String {
        val f = apkFile(p)
        if (f.exists()) {
            val n = runCatching { pm.getPackageArchiveInfo(f.path, 0)?.packageName }.getOrNull()
            if (!n.isNullOrBlank()) return n
        }
        if (p.repo.contains("/") && prefs.token.isNotBlank() && detecting.add(p.id)) {
            val r = runCatching { gh.packageOf(p.repo) }
            if (r.isFailure) detecting.remove(p.id)
            return r.getOrDefault("")
        }
        return ""
    }

    /** Runs on every resume: finds missing package names, checks which apps are on this phone, confirms finished installs, clears stale APKs. */
    fun checkInstalls() = viewModelScope.launch(Dispatchers.IO) {
        if (!checking.compareAndSet(false, true)) return@launch
        try {
            val list = dao.all().first()
            val pm = getApplication<Application>().packageManager
            getApplication<Application>().cacheDir.let { File(it, "apks").deleteRecursively() }   // old location (before v19)
            list.forEach { p0 ->
                if (p0.kind != ProjectType.APP) return@forEach   // v38: nothing to install for websites and servers
                var p = p0
                if (p.packageName.isBlank()) {
                    val found = detectPackage(p, pm)
                    if (found.isNotBlank()) {
                        p = p.copy(packageName = found)
                        lock.withLock { projects.value.firstOrNull { it.id == p0.id }?.let { dao.save(it.copy(packageName = found)) } }
                    }
                }
                // the background worker saves APKs without the UI: pick up what it stored
                prefs.cachedTag(p.id).let { if (it.isBlank()) cached.remove(p.id) else if (cached[p.id] != it) cached[p.id] = it }
                // is the app on this phone? an uninstalled app also clears its installed marker
                if (p.packageName.isNotBlank()) {
                    val info = runCatching { pm.getPackageInfo(p.packageName, 0) }.getOrNull()
                    val ver = info?.let { it.versionName ?: "?" }
                    if (onPhone[p.id] != (ver ?: "")) onPhone[p.id] = ver ?: ""
                    if (info == null && installedTag(p.id).isNotBlank()) markInstalled(p.id, "")
                    // Which build is on the phone? Compare its versionCode with the saved APK (also when it was installed outside App Hub).
                    val cTag0 = cachedTag(p.id)
                    val aCode = if (info != null && cTag0.isNotBlank() && apkFile(p).exists()) apkCodeOf(p.id, cTag0, pm) else null
                    if (info != null && aCode != null) {
                        val code = PackageInfoCompat.getLongVersionCode(info)
                        if (code >= aCode && installedTag(p.id) != cTag0) markInstalled(p.id, cTag0)
                        else if (code < aCode && installedTag(p.id) == cTag0) markInstalled(p.id, "")
                        val isBehind = code < aCode && cTag0 == latest[p.id]
                        if (isBehind) { if (behind[p.id] != true) behind[p.id] = true } else behind.remove(p.id)
                    } else if (info == null) behind.remove(p.id)
                }
                val tag = cachedTag(p.id)
                val tried = prefs.installTry(p.id)
                if (tag.isNotBlank() && tried > 0L && p.packageName.isNotBlank()) {
                    val updated = runCatching { pm.getPackageInfo(p.packageName, 0).lastUpdateTime }.getOrNull()
                    if (updated != null && updated >= tried) markInstalled(p.id, tag)
                }
                pruneOne(p)
            }
            // leftovers from older versions or deleted apps (finished downloads only, never a .part in progress)
            val app = getApplication<Application>()
            val keep = list.filter { cachedTag(it.id).isNotBlank() }.map { apkFile(it).name }.toSet() +
                list.filter { ApkStore.hasPrev(app, prefs, it.id) }.map { ApkStore.prev(app, it.id).name }
            apkDir.listFiles()?.filter { it.name !in keep && !it.name.endsWith(".part") }?.forEach { it.delete() }
            apkDir.listFiles()?.filter { it.name.endsWith(".part") && System.currentTimeMillis() - it.lastModified() > 7 * 86_400_000L }?.forEach { it.delete() }
        } finally { checking.set(false) }
    }

    /**
     * True only while a zip in Downloads is newer than the last pushed build. The version label alone never makes it true,
     * so a repeated "+1" or a re-push of the same version can no longer show "+ Push v27" when v24 is the latest build.
     */
    fun pending(p: Project): Boolean {
        val z = zipFound[p.id] ?: return false
        val zv = Zipper.versionOf(z.name)
        val pv = (pushedV[p.id] ?: prefs.pushedVer(p.id)).ifBlank { p.version }
        return zv.isBlank() || n(zv) > n(pv)
    }

    /** v28: the version the repo is at now (last pushed "vNNN:" commit). */
    fun pushedVersion(p: Project): String = (pushedV[p.id] ?: prefs.pushedVer(p.id)).ifBlank { p.version }

    /** v28: true when the zip's version is the same as or older than what is already on GitHub (asks before pushing). */
    fun isOlder(p: Project, pack: Zipper.Pack): Boolean {
        val zv = n(pack.version)
        return pack.version.isNotBlank() && zv > 0 && zv <= n(pushedVersion(p))
    }

    // ---- v28: delete files on GitHub ----
    val repoFiles = mutableStateMapOf<Long, List<GitHub.RepoFile>>()
    var filesLoading by mutableStateOf(false)
    var deletingFile by mutableStateOf("")

    fun loadFiles(p: Project, quiet: Boolean = false) = viewModelScope.launch {
        if (!p.repo.contains("/")) { if (!quiet) toast("Log in with GitHub first (Accounts)"); return@launch }
        filesLoading = true
        val l = gh.files(p.repo)
        if (l != null) repoFiles[p.id] = l else if (!quiet) toast("Could not read the file list")
        filesLoading = false
    }

    /** v31: content of one repo file for the file page. */
    suspend fun readFile(p: Project, path: String): GitHub.Blob = gh.readBlob(p.repo, path)

    /** One commit that removes the file. The message starts with the next vNNN, like every push, so the version label follows. */
    fun deleteFile(p: Project, path: String) = viewModelScope.launch {
        if (deletingFile.isNotEmpty()) return@launch
        deletingFile = path
        try {
            val ver = Commands.bump(pushedVersion(p))
            val before = gh.runs(p.repo).firstOrNull()?.id ?: 0L
            if (prefs.notifiedRun(p.id) == 0L && before != 0L) prefs.setNotifiedRun(p.id, before)
            val (ok, m) = gh.deleteFiles(p.repo, listOf(path), "$ver: delete $path")
            if (ok) {
                projects.value.firstOrNull { it.id == p.id }?.let { dao.save(it.copy(version = ver)) }
                prefs.setPushedVer(p.id, ver); pushedV[p.id] = ver
                repoFiles[p.id] = (repoFiles[p.id] ?: emptyList()).filter { it.path != path }
                toast("Deleted $path. A new build starts.")
                delay(3_000); refresh(p)
            } else toast(m)
        } finally { deletingFile = "" }
    }

    /** What the + icon is waiting to push: the new zip's version, or the bumped label. */
    fun waitLabel(p: Project): String = zipFound[p.id]?.let { Zipper.versionOf(it.name).ifBlank { it.name } } ?: p.version

    /** One key per zip file (not per project), so a zip is asked about only once. */
    fun zipKey(id: Long, f: File) = Downloads.key(f)

    private fun nn(s: String) = s.filter { it.isLetterOrDigit() }.lowercase()

    /** The ONE project a zip belongs to, going by its file name: app name first, then repo name. */
    fun projectFor(zipName: String): Project? {
        val base = nn(Downloads.baseOf(zipName))
        if (base.isBlank()) return null
        val ps = projects.value
        return ps.firstOrNull { nn(it.name) == base } ?: ps.firstOrNull { nn(it.repo.substringAfter("/")) == base }
    }

    /** True when the zip's name is this project's app name or repo name. */
    fun nameMatches(p: Project, zipName: String): Boolean {
        val base = nn(Downloads.baseOf(zipName))
        return base.isNotBlank() && (nn(p.name) == base || nn(p.repo.substringAfter("/")) == base)
    }

    /** Looks in Downloads for the newest zip named like each app (app name or repo name). */
    fun scanZips(ctx: Context) {
        if (!Downloads.hasAccess(ctx)) { zipFound.clear(); zipLatest.clear(); return }
        viewModelScope.launch(Dispatchers.IO) {
            projects.value.forEach { p ->
                val f = Downloads.latestFor(p)
                if (f == null) { zipLatest.remove(p.id); zipFound.remove(p.id) } else {
                    zipLatest[p.id] = f
                    val zv = Zipper.versionOf(f.name)
                    val pushed = (pushedV[p.id] ?: prefs.pushedVer(p.id)).ifBlank { p.version }
                    val waiting = projectFor(f.name)?.id == p.id && prefs.zipSeen(p.id) != Downloads.key(f) && (zv.isBlank() || n(zv) > n(pushed))   // only the project the zip is named after is asked
                    if (waiting) zipFound[p.id] = f else zipFound.remove(p.id)
                }
            }
        }
    }

    /** Shows what a push would change (new / changed files) without pushing anything. */
    fun preview(p: Project, pack: Zipper.Pack) = viewModelScope.launch {
        if (deploying) return@launch
        if (!p.repo.contains("/")) { toast("Log in with GitHub first (Accounts)"); return@launch }
        deploying = true; deployLog.clear(); deployGen++
        try {
            deployLog += "Comparing ${pack.entries.size} files with ${p.repo}…"
            gh.pushFiles(p.repo, pack.entries.map { it.path to it.bytes }, "preview", dryRun = true) { deployLog += it }
        } finally { deploying = false }
    }

    /** Adds a commit that restores the previous version of the code (history is kept). */
    fun undoLastPush(p: Project) = viewModelScope.launch {
        if (!p.repo.contains("/")) { toast("Log in with GitHub first (Accounts)"); return@launch }
        val (ok, m) = gh.revertLast(p.repo)
        toast(if (ok) "Undone: $m. A new build starts." else m)
        if (ok) { delay(3_000); refresh(p) }
    }

    fun updateAutoInstall(v: Boolean) { autoInstall = v; prefs.autoInstall = v }

    fun skipZip(p: Project, f: File) { prefs.setZipSeen(p.id, Downloads.key(f)); zipFound.remove(p.id) }

    /** Loads a zip found in Downloads into the Deploy screen. */
    fun useFile(f: File) = viewModelScope.launch {
        val pack = withContext(Dispatchers.IO) { runCatching { Zipper.read(f) }.getOrNull() }
        if (pack == null || pack.entries.isEmpty()) toast("Could not read ${f.name}") else zipPack = pack
    }

    /** Pushes a zip from Downloads straight away. */
    fun deployFile(ctx: Context, p: Project, f: File) = viewModelScope.launch {
        val pack = withContext(Dispatchers.IO) { runCatching { Zipper.read(f) }.getOrNull() }
        if (pack == null || pack.entries.isEmpty()) { toast("Could not read ${f.name}"); return@launch }
        zipPack = pack
        deploy(ctx, p, pack, "", f)
    }

    private suspend fun seedMissing(onlyNew: Boolean = false): Int {
        val have = dao.all().first()
        var c = 0
        Presets.all.forEach { pr ->
            if (onlyNew && prefs.presetSeen(pr.repo)) return@forEach
            prefs.setPresetSeen(pr.repo)
            if (have.none { it.name.equals(pr.name, true) || it.repo.substringAfter("/").equals(pr.repo, true) }) {
                dao.save(Project(name = pr.name, repo = if (login.isBlank()) pr.repo else "$login/${pr.repo}",
                    packageName = pr.pkg, localPath = pr.path, version = pr.version, stack = pr.stack))
                c++
            }
        }
        return c
    }

    private suspend fun fixOwners() {
        if (login.isBlank()) return
        dao.all().first().filter { !it.repo.contains("/") }.forEach { dao.save(it.copy(repo = "$login/${it.repo}")) }
    }

    fun restorePresets() = viewModelScope.launch {
        val c = seedMissing(); fixOwners()
        toast(if (c > 0) "Added $c preset app(s)" else "All preset apps are already added")
    }

    fun updateAutoDownload(v: Boolean) { autoDownload = v; prefs.autoDownload = v; Sync.schedule(getApplication<Application>(), prefs) }
    fun updateWifiOnly(v: Boolean) { wifiOnly = v; prefs.wifiOnly = v; Sync.schedule(getApplication<Application>(), prefs) }

    fun bumpVersion(p: Project) = update(p.copy(version = Commands.bump(p.version)))

    fun sel(): Project? = projects.value.firstOrNull { it.id == selId } ?: projects.value.firstOrNull()

    // =====================================================================================
    // v39: Vercel (websites and servers)
    // =====================================================================================
    /** True for a website / server that deploys on Vercel (linked in Setup or in its Settings). */
    fun isVercel(p: Project): Boolean = vercelRev >= 0 && p.kind != ProjectType.APP && prefs.vercelId(p.repo).isNotBlank()

    fun vercelLogin(token: String, team: String) = viewModelScope.launch {
        if (token.isBlank()) { vercelMsg = "Paste a Vercel token first"; return@launch }
        vercelBusy = true; vercelMsg = ""
        val (user, err) = vercel.verify(token.trim(), team.trim())
        vercelBusy = false
        if (user == null) { vercelMsg = "Vercel: $err"; return@launch }
        prefs.vercelToken = token; prefs.vercelTeam = team; prefs.vercelUser = user
        vercelUser = user; vercelTeam = team.trim()
        toast("Logged in to Vercel as $user")
    }

    fun vercelLogout() {
        prefs.vercelToken = ""; prefs.vercelUser = ""; prefs.vercelTeam = ""
        vercelUser = ""; vercelTeam = ""; vercelMsg = ""
    }

    /** Creates (or reuses) the Vercel project for an existing website / server and remembers the link. */
    fun linkVercel(p: Project) = viewModelScope.launch {
        if (vercelUser.isBlank()) { vercelMsg = "Log in to Vercel first (Accounts)"; return@launch }
        if (!p.repo.contains("/")) { vercelMsg = "Log in with GitHub first (Accounts)"; return@launch }
        vercelBusy = true; vercelMsg = ""
        val (lk, err) = vercel.link(p.repo)
        vercelBusy = false
        if (lk == null) { vercelMsg = "Vercel: $err"; return@launch }
        prefs.setVercelId(p.repo, lk.id); vercelRev++
        projects.value.firstOrNull { it.id == p.id }?.let { if (it.liveUrl.isBlank()) dao.save(it.copy(liveUrl = lk.url)) }
        toast("Linked to Vercel")
    }

    // ---- v53: GitHub Pages for an existing website ----
    var pagesBusy by mutableStateOf(false)
    var pagesMsg by mutableStateOf("")

    /** Turns Pages on for the project's repo, starts the deploy workflow and fills the live address when it is empty. */
    fun enablePages(p: Project) = viewModelScope.launch {
        if (!p.repo.contains("/")) { pagesMsg = "Log in with GitHub first (Accounts)"; return@launch }
        pagesBusy = true; pagesMsg = ""
        val err = gh.enablePages(p.repo)
        if (err.isNotBlank()) { pagesBusy = false; pagesMsg = "Pages: $err"; return@launch }
        val started = gh.dispatchDeploy(p.repo)
        projects.value.firstOrNull { it.id == p.id }?.let { if (it.liveUrl.isBlank()) dao.save(it.copy(liveUrl = pagesUrl(p.repo))) }
        pagesBusy = false
        pagesMsg = if (started) "Pages is on and a deploy was started." else "Pages is on. Push or re-run the deploy workflow to publish."
        toast("GitHub Pages enabled")
    }

    /** Only forgets the link here; the project on Vercel stays. */
    fun unlinkVercel(p: Project) {
        prefs.setVercelId(p.repo, ""); vercelLast.remove(p.id); vercelVer.remove(p.id); vercelRev++
    }

    fun loadEnv(p: Project) = viewModelScope.launch {
        val vid = prefs.vercelId(p.repo)
        if (vid.isBlank()) { envMsg = "Link this project to Vercel first"; return@launch }
        val (list, err) = vercel.envList(vid)
        if (list == null) { envMsg = "Vercel: $err"; return@launch }
        envMsg = ""; envVars.clear(); envVars.addAll(list)
    }

    /** Adds or replaces a variable. Takes effect on the next deployment. */
    fun saveEnv(p: Project, key: String, value: String, onSaved: () -> Unit) = viewModelScope.launch {
        val vid = prefs.vercelId(p.repo)
        val k = key.trim()
        when {
            vid.isBlank() -> { envMsg = "Link this project to Vercel first"; return@launch }
            !Regex("[A-Za-z_][A-Za-z0-9_]*").matches(k) -> { envMsg = "Name: letters, digits and _ only, not starting with a digit"; return@launch }
            value.isEmpty() -> { envMsg = "Enter a value"; return@launch }
        }
        envBusy = true; envMsg = ""
        val err = vercel.envSet(vid, k, value)
        envBusy = false
        if (err.isNotBlank()) { envMsg = "Vercel: $err"; return@launch }
        onSaved(); toast("$k saved. It applies from the next deployment")
        loadEnv(p)
    }

    fun deleteEnv(p: Project, e: Vercel.Env) = viewModelScope.launch {
        val vid = prefs.vercelId(p.repo)
        if (vid.isBlank()) return@launch
        envBusy = true; envMsg = ""
        val err = vercel.envDelete(vid, e.id)
        envBusy = false
        if (err.isNotBlank()) { envMsg = "Vercel: $err"; return@launch }
        toast("${e.key} deleted")
        loadEnv(p)
    }

    /**
     * Status of the newest production deployment of a Vercel website / server, in the words of Project.lastStatus
     * (none / running / passed / failed) plus its error text. null = not on Vercel (the GitHub run decides).
     * Asked at most every 4 s; when Vercel cannot be reached the last answer stays.
     */
    private suspend fun vercelState(p: Project): Pair<String, String>? {
        val vid = prefs.vercelId(p.repo)
        if (p.kind == ProjectType.APP || vid.isBlank()) return null
        val old = vercelLast[p.id]
        if (prefs.vercelToken.isBlank()) return old ?: ("none" to "")
        if (old != null && System.currentTimeMillis() - (vercelAt[p.id] ?: 0L) < 4_000L) return old
        vercelAt[p.id] = System.currentTimeMillis()
        val l = vercel.latest(vid)
        val dep = l.dep
        val res: Pair<String, String> = when {
            !l.ok -> old ?: ("none" to "")
            dep == null -> "none" to ""
            else -> {
                vOf(dep.msg)?.let { vercelVer[p.id] = it }
                when (val st = Vercel.statusOf(dep.state)) {
                    null -> old ?: ("none" to "")   // cancelled: not a result
                    "failed" -> "failed" to (if (old?.first == "failed" && vercelErrFor[p.id] == dep.id) old.second
                        else vercel.errorText(dep.id).also { vercelErrFor[p.id] = dep.id })
                    else -> st to ""
                }
            }
        }
        vercelLast[p.id] = res
        return res
    }

    /** Follows the Vercel deployment started by a push, like [watch] follows a CI run. [since] = when the push started (phone clock). */
    private fun watchVercel(p: Project, since: Long, gen: Int) = viewModelScope.launch {
        deployLog += "Waiting for Vercel…"
        var liveIdx = -1
        fun setLive(t: String) { if (liveIdx in deployLog.indices) deployLog[liveIdx] = t else { deployLog += t; liveIdx = deployLog.lastIndex } }
        val vid = prefs.vercelId(p.repo)
        val t0 = System.currentTimeMillis()
        repeat(135) {
            delay(4_000)
            if (gen != deployGen) return@launch
            val d = vercel.latest(vid).dep ?: return@repeat
            if (d.created < since - 30_000L) return@repeat   // still the deployment from before this push
            val secs = (System.currentTimeMillis() - t0) / 1000
            when (Vercel.statusOf(d.state)) {
                "passed" -> {
                    vercelAt.remove(p.id); refresh(p).join()
                    setLive("✓ Vercel deployed  ${secs}s")
                    toast("${p.name} deployed")
                    projects.value.firstOrNull { it.id == p.id }?.liveUrl?.takeIf { it.isNotBlank() }?.let { deployLog += "✓ Live at $it" }
                    return@launch
                }
                "failed" -> {
                    vercelAt.remove(p.id); refresh(p).join()
                    setLive("✗ Vercel build failed")
                    (errors[p.id] ?: "").lines().filter { it.isNotBlank() }.take(8).forEach { deployLog += "  $it" }
                    deployLog += "Copy the errors below and paste them to the AI."
                    toast("${p.name} build failed")
                    return@launch
                }
                "running" -> setLive("… Vercel building  ${secs}s")
                else -> {}
            }
        }
        if (gen == deployGen) deployLog += "• Still building. Check the Dashboard later."
    }
    fun toast(s: String) = Toast.makeText(getApplication(), s, Toast.LENGTH_SHORT).show()

    fun copy(text: String, quiet: Boolean = false) {
        val cm = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("apphub", text)); if (!quiet) toast("Copied")
    }

    // ---- login ----
    // With a client secret: web flow, GitHub redirects to apphub://oauth and this app comes back by itself.
    // Without: device flow (code + browser); returning to the app finishes it.
    private var loginJob: Job? = null

    fun startLogin() {
        if (loginJob?.isActive == true) loginJob?.cancel()
        if (prefs.clientSecret.isNotBlank()) startWebLogin()
        else loginJob = viewModelScope.launch { deviceLogin() }
    }

    fun cancelLogin() {
        loginJob?.cancel()
        prefs.pendingState = ""; prefs.pendingVerifier = ""; prefs.pendingDevice = ""; prefs.pendingCode = ""
        userCode = null; loginMsg = ""; loginBusy = false
    }

    fun updateClientSecret(v: String) { clientSecret = v; prefs.clientSecret = v }

    private fun openBrowser(uri: Uri) {
        try {
            getApplication<Application>().startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { loginMsg = "No browser found to open GitHub"; toast(loginMsg) }
    }

    private fun randomUrl(n: Int): String {
        val b = ByteArray(n); java.security.SecureRandom().nextBytes(b)
        return Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun challenge(v: String): String = Base64.encodeToString(
        java.security.MessageDigest.getInstance("SHA-256").digest(v.toByteArray()), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    private fun startWebLogin() {
        val verifier = randomUrl(48)
        val state = randomUrl(24)
        prefs.pendingState = state; prefs.pendingVerifier = verifier
        loginMsg = "Waiting for GitHub…"
        openBrowser(gh.webAuthUri(state, challenge(verifier)))
    }

    /** Called from MainActivity with the apphub://oauth?code=...&state=... link. */
    fun handleCallback(uri: Uri?) {
        if (uri == null || uri.scheme != "apphub" || uri.host != "oauth") return
        val expected = prefs.pendingState
        val verifier = prefs.pendingVerifier
        if (expected.isBlank()) return // already handled (rotation / duplicate intent)
        prefs.pendingState = ""; prefs.pendingVerifier = ""
        val err = uri.getQueryParameter("error")
        val code = uri.getQueryParameter("code")
        if (err != null) { loginMsg = "GitHub: ${uri.getQueryParameter("error_description") ?: err}"; toast(loginMsg); return }
        if (code.isNullOrBlank() || uri.getQueryParameter("state") != expected) { loginMsg = "Login link did not match, tap Login again"; toast(loginMsg); return }
        loginMsg = "Finishing login…"; loginBusy = true   // shown at once, before the network call starts
        loginJob = viewModelScope.launch {
            val e = gh.exchangeCode(code, verifier)
            if (e == null) finishLogin() else { loginMsg = e; loginBusy = false; toast(e) }
        }
    }

    private suspend fun deviceLogin() {
        loginMsg = "Requesting code…"
        val j = gh.deviceCode()
        if (!j.has("user_code")) {
            loginMsg = when (val e = j.optString("error")) {
                "device_flow_disabled" -> "Enable Device Flow in your GitHub OAuth app settings"
                "incorrect_client_credentials" -> "Client ID is wrong"
                "" -> "Could not reach GitHub, check internet"
                else -> "GitHub: $e"
            }
            toast(loginMsg); return
        }
        prefs.pendingDevice = j.getString("device_code")
        prefs.pendingCode = j.getString("user_code")
        prefs.pendingInterval = j.optInt("interval", 5)
        prefs.pendingExpiry = System.currentTimeMillis() + j.optInt("expires_in", 900) * 1000L
        userCode = prefs.pendingCode; copy(prefs.pendingCode)
        openBrowser(Uri.parse(j.getString("verification_uri")))
        pollDevice(false)
    }

    private suspend fun pollDevice(immediate: Boolean) {
        val dc = prefs.pendingDevice
        if (dc.isBlank()) return
        if (immediate) {
            loginMsg = "Finishing login…"; loginBusy = true
            // the code is checked at once; if it was not approved yet, go back to the normal waiting text after a moment
            viewModelScope.launch { delay(2_500); if (loginBusy && prefs.pendingDevice.isNotBlank()) { loginMsg = "Approve on GitHub, then come back here…"; loginBusy = false } }
        } else loginMsg = "Approve on GitHub, then come back here…"
        val err = gh.pollToken(dc, prefs.pendingInterval, prefs.pendingExpiry, immediate)
        userCode = null; prefs.pendingDevice = ""; prefs.pendingCode = ""
        if (err == null) finishLogin() else { loginMsg = err; loginBusy = false; toast(err) }
    }

    /** On every return to the app: check at once whether the device-flow code was approved meanwhile. */
    fun resumeLogin() {
        if (prefs.pendingDevice.isBlank()) return
        if (System.currentTimeMillis() > prefs.pendingExpiry) {
            prefs.pendingDevice = ""; prefs.pendingCode = ""; userCode = null; loginMsg = "Code expired, tap Login again"; return
        }
        loginJob?.cancel()
        userCode = prefs.pendingCode.ifBlank { null }
        loginMsg = "Finishing login…"; loginBusy = true   // shown immediately on return, the code is checked right away
        loginJob = viewModelScope.launch { pollDevice(true) }
    }

    private suspend fun finishLogin() {
        val u = gh.user()
        if (u == null) { prefs.token = ""; loginMsg = "GitHub rejected the token (could not read your user)"; loginBusy = false; toast(loginMsg); return }
        login = u; prefs.login = u; loginMsg = ""; loginBusy = false; sessionExpired = false; gh.authLost = false
        toast("Logged in as $u"); fixOwners(); refreshAll()
        if (!inForeground) comeBack(u)
    }

    /**
     * Device flow has no redirect (GitHub cannot call apphub://oauth), so when the approval is noticed while the
     * browser is still on screen the app brings itself back: first a direct launch (works where Android allows it),
     * and always a notification as the reliable fallback.
     */
    private fun comeBack(user: String) {
        val app = getApplication<Application>()
        val open = Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching { app.startActivity(open) }
        runCatching {
            if (Build.VERSION.SDK_INT >= 33 && app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel("login", "Login", NotificationManager.IMPORTANCE_HIGH))
            val pi = PendingIntent.getActivity(app, 1, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val note = android.app.Notification.Builder(app, "login").setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Logged in as $user").setContentText("Tap to return to App Hub")
                .setContentIntent(pi).setAutoCancel(true).setTimeoutAfter(120_000).build()
            nm.notify(7, note)
        }
    }

    fun logout() { cancelLogin(); prefs.token = ""; prefs.refreshToken = ""; prefs.tokenExpiry = 0L; prefs.login = ""; login = ""; sessionExpired = false }

    // ---- projects ----
    /**
     * Pulls build runs and the newest release, silently (no spinner). The version label follows the newest pushed "vNNN:" commit,
     * so it is never ahead of the latest build. Unchanged answers cost nothing (ETag), so this can run every 3 seconds.
     */
    fun refresh(p: Project) = viewModelScope.launch {
        if (!p.repo.contains("/")) return@launch
        val r = gh.runs(p.repo)
        if (r.isEmpty() && runs[p.id] != null && !isVercel(p)) return@launch   // offline or API error: keep what is shown (a Vercel project has no runs, its status comes from Vercel)
        val real = r.filter { !it.cancelled }   // a run you cancelled is not a result: it never shows as failed
        var changed = false
        if (runs[p.id] != real) { runs[p.id] = real; changed = true }
        val last = real.firstOrNull()
        val vs = vercelState(p)   // v39: a Vercel website / server takes its status from the Vercel deployment
        val status = when { vs != null -> vs.first; last == null -> "none"; last.ok == null -> "running"; last.ok == true -> "passed"; else -> "failed" }
        notifyBuild(p, last)
        // the error text is read once per failed run, not on every poll
        val err = if (vs != null) vs.second else if (status == "failed" && last != null) {
            val old = errors[p.id].orEmpty()
            if (errRun[p.id] == last.id && old.isNotBlank()) old else gh.errorOf(p.repo, last.id).also { if (it.isNotBlank()) errRun[p.id] = last.id }
        } else ""
        if (errors[p.id] != err) { errors[p.id] = err; changed = true }
        // v38: websites and servers have no APK release; a server is checked once its deploy passed; a website learns its Pages address
        val kind = (projects.value.firstOrNull { it.id == p.id } ?: p).kind
        val liveNow = projects.value.firstOrNull { it.id == p.id }?.liveUrl ?: p.liveUrl
        if (kind == ProjectType.SERVER) {
            if (status == "passed" && Health.canProbe(liveNow)) probeServer(p.id, liveNow) else health.remove(p.id)
        }
        var foundUrl = ""
        if (kind == ProjectType.WEBSITE && status == "passed" && liveNow.isBlank() && System.currentTimeMillis() - (urlTry[p.id] ?: 0L) > 600_000L) {
            urlTry[p.id] = System.currentTimeMillis()
            foundUrl = gh.pagesUrl(p.repo)
        }
        val rel = if (kind == ProjectType.APP) gh.latest(p.repo) else null
        if (rel != null) {
            if (latestRel[p.id] != rel) latestRel[p.id] = rel
            if (latest[p.id] != rel.tag) { latest[p.id] = rel.tag; changed = true }
        }
        pruneOne(p)
        // a newer build than the one on the phone: save its APK now (Wi-Fi rules apply), so Update is one tap.
        // Also for an app that is on the phone but whose build is unknown (installed outside App Hub): the saved APK settles which build it is.
        val unknownBuild = installedTag(p.id).isBlank() && isInstalled(p) == true
        if (autoDownload && rel != null && (hasUpdate(p) || unknownBuild) && cachedTag(p.id) != rel.tag && !dlProgress.containsKey(p.id)
            && !(wifiOnly && onMetered()) && System.currentTimeMillis() - (autoTry[p.id] ?: 0L) > 300_000L) {
            autoTry[p.id] = System.currentTimeMillis()
            viewModelScope.launch { downloadTracked(p, rel, announce = true) }
        }
        lock.withLock {
            val cur = projects.value.firstOrNull { it.id == p.id }
            if (cur != null) {
                // The label is the newest "vNNN:" commit that was pushed. Merge commits, cancelled runs, re-runs and manual
                // dispatches never change it, and it is never kept ahead of the latest build (that gave "v27" after a v24 push).
                val pushed = r.sortedByDescending { it.id }.firstNotNullOfOrNull { vOf(it.msg) }
                    ?: vercelVer[p.id]
                    ?: prefs.pushedVer(p.id).ifBlank { cur.version }
                if (r.isNotEmpty()) prefs.setLastRun(p.id, r.maxOf { it.id })
                if (pushedV[p.id] != pushed) { prefs.setPushedVer(p.id, pushed); pushedV[p.id] = pushed }
                val label = if (prefs.autoVer(p.id)) pushed else cur.version
                val next = cur.copy(version = label, lastStatus = status, lastError = err, liveUrl = cur.liveUrl.ifBlank { foundUrl })
                if (next != cur) { dao.save(next); changed = true }
            }
        }
        if (changed) saveSnap(p)
    }

    /** v38: checks a server endpoint in the background (not awaited, so a slow server never slows the sync). Every 15 s until healthy, then every minute. */
    private fun probeServer(id: Long, url: String) {
        val now = System.currentTimeMillis()
        if (now - (probeAt[id] ?: 0L) < (if (health[id] == true) 60_000L else 15_000L)) return
        probeAt[id] = now
        viewModelScope.launch {
            val ok = Health.probe(url)
            if (health[id] != ok) health[id] = ok
            projects.value.firstOrNull { it.id == id }?.let { ServerWatch.record(getApplication<Application>(), prefs, it, ok) }   // v42
        }
    }

    /** v27 feature 3: one notification per finished build. The first sync only remembers the newest run, and old runs are never announced. */
    private fun notifyBuild(p: Project, last: GitHub.Run?) {
        if (last == null || last.ok == null) return
        val seen = prefs.notifiedRun(p.id)
        if (seen == last.id) return
        prefs.setNotifiedRun(p.id, last.id)
        if (seen == 0L || !startedRecently(last.time)) return
        Notifier.buildDone(getApplication<Application>(), prefs, p, last.number, last.ok == true)
    }

    private fun startedRecently(t: String): Boolean = runCatching {
        val then = java.time.LocalDateTime.parse(t.replace(" ", "T")).toEpochSecond(java.time.ZoneOffset.UTC)
        System.currentTimeMillis() / 1000 - then < 3600
    }.getOrDefault(false)

    private val errRun = mutableMapOf<Long, Long>()       // app id -> run id the shown error belongs to
    private val autoTry = mutableMapOf<Long, Long>()      // app id -> last background download attempt (retry at most every 5 min)
    private val syncing = java.util.concurrent.atomic.AtomicBoolean(false)

    private fun onMetered(): Boolean = runCatching {
        (getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).isActiveNetworkMetered
    }.getOrDefault(false)

    /** Silent live sync, called every 3 s while App Hub is on screen and when a screen opens. No spinner, overlapping runs are skipped. */
    fun autoSync() {
        if (gh.authLost && !sessionExpired) sessionExpired = true
        if (!inForeground || prefs.token.isBlank() || sessionExpired) return
        if (System.currentTimeMillis() < gh.limitUntil) { updateNetNote(); return }   // API limit: wait quietly until it ends
        if (!syncing.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                projects.value.map { refresh(it) }.forEach { it.join() }
                scanZips(getApplication())
                checkInstalls()
                if (gh.authLost) sessionExpired = true
            } finally { syncing.set(false); updateNetNote() }
        }
    }

    /** v27 feature 4: the amber Dashboard note. Computed after every sync, so it clears by itself when GitHub answers again. */
    private fun updateNetNote() {
        val now = System.currentTimeMillis()
        val note = when {
            gh.limitUntil > now -> "GitHub limit reached, retrying at ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(gh.limitUntil))}"
            gh.offline -> "Offline, showing saved data"
            else -> ""
        }
        if (netNote != note) netNote = note
    }

    /** The refresh button: same sync, with a spinner because you asked for it. */
    fun refreshAll() = viewModelScope.launch {
        refreshing = true
        projects.value.map { refresh(it) }.forEach { it.join() }
        scanZips(getApplication())
        checkInstalls()
        refreshing = false
        updateNetNote()
    }

    /** Repo info, recent commits and releases, fetched inside the app (silent; unchanged answers are free). */
    fun loadDetails(p: Project) = viewModelScope.launch {
        if (!p.repo.contains("/")) return@launch
        var changed = false
        gh.info(p.repo)?.let { if (infos[p.id] != it) { infos[p.id] = it; changed = true } }
        gh.commits(p.repo).let { if (it.isNotEmpty() && commits[p.id] != it) { commits[p.id] = it; changed = true } }
        gh.releases(p.repo).let { if (it.isNotEmpty() && releases[p.id] != it) { releases[p.id] = it; changed = true } }
        if (changed) saveSnap(p)
    }

    fun openLog(p: Project, j: GitHub.Job) = viewModelScope.launch {
        logTitle = "${p.name} • ${j.name}"; logLines = emptyList(); logLoading = true
        val t = gh.jobLog(p.repo, j.id)
        val ts = Regex("^\\d{4}-\\d\\d-\\d\\dT[\\d:.]+Z ")
        logLines = if (t.isBlank()) listOf("No log available (the job may still be running).") else t.lines().map { it.replaceFirst(ts, "") }
        logLoading = false
    }

    // ---- v49: the whole build history, 100 at a time ----
    val olderRuns = mutableStateMapOf<Long, List<GitHub.Run>>()
    val olderMore = mutableStateMapOf<Long, Boolean>()
    val olderLoading = mutableStateMapOf<Long, Boolean>()
    private val olderPage = HashMap<Long, Int>()
    fun loadOlder(p: Project) = viewModelScope.launch {
        if (olderLoading[p.id] == true) return@launch
        olderLoading[p.id] = true
        val page = (olderPage[p.id] ?: 0) + 1
        val (list, more) = gh.runsPage(p.repo, page)
        if (list.isNotEmpty()) { olderPage[p.id] = page; olderRuns[p.id] = (olderRuns[p.id] ?: emptyList()) + list.filter { !it.cancelled } }
        olderMore[p.id] = more
        olderLoading[p.id] = false
    }
    /** Plain text of the loaded build history, newest first (for Copy history). */
    fun historyText(p: Project, list: List<GitHub.Run>): String = "${p.name} builds (${list.size})\n" +
        list.joinToString("\n") { r -> "#${r.number} ${when (r.ok) { true -> "passed"; false -> "failed"; else -> "running" }} ${r.time} ${r.msg}" }

    fun loadJobs(p: Project, runId: Long) = viewModelScope.launch { jobs[runId] = gh.jobs(p.repo, runId) }

    fun update(p: Project) = viewModelScope.launch {
        dao.save(p)
    }
    fun delete(p: Project) = viewModelScope.launch { prefs.setVercelId(p.repo, ""); vercelLast.remove(p.id); vercelVer.remove(p.id); health.remove(p.id); dropCache(p); ApkStore.clearPrev(getApplication<Application>(), prefs, p.id); ApkStore.clearParts(getApplication<Application>(), p.id); SnapCache.drop(snapDir, p.id); snapped -= p.id; dao.delete(p) }

    fun build(p: Project) = viewModelScope.launch {
        toast(if (gh.dispatch(p.repo)) "Build triggered on GitHub" else "Trigger failed — push the workflow first")
    }

    /** Sets the 4 signing secrets on GitHub again from the keystore stored in the app. */
    fun resendSecrets(p: Project) = viewModelScope.launch {
        val m = prefs.keys(p.repo).lines().filter { it.contains("=") }.associate { it.substringBefore("=") to it.substringAfter("=") }
        val b64 = m["KEYSTORE_BASE64"]; val pass = m["PASSWORD"]; val alias = m["ALIAS"]
        if (b64.isNullOrBlank() || pass.isNullOrBlank() || alias.isNullOrBlank()) { toast("No stored keystore for this repo"); return@launch }
        val ok = listOf("KEYSTORE_BASE64" to b64, "KEYSTORE_PASSWORD" to pass, "KEY_ALIAS" to alias, "KEY_PASSWORD" to pass)
            .all { gh.setSecret(p.repo, it.first, it.second) }
        toast(if (ok) "Signing secrets set on GitHub" else "Setting secrets failed")
    }

    fun keyBackup(p: Project) = prefs.keys(p.repo)

    // ---- deploy: zip -> GitHub commit -> CI build (no Termux) ----
    fun secretPushAnyway(ctx: Context) {
        val a = secretAsk ?: return
        secretAsk = null
        deploy(ctx, a.p, a.pack, a.note, a.src, secretsChecked = true)
    }

    /** Pushes without the files that are secrets themselves (.env, key files). Secrets found inside code files stay: they are yours to fix. */
    fun secretLeaveOut(ctx: Context) {
        val a = secretAsk ?: return
        secretAsk = null
        val drop = a.pack.findings.filter { it.wholeFile }.map { it.path }.toSet()
        val trimmed = a.pack.without(drop)
        zipPack = trimmed
        toast("Left out ${drop.size} file(s)")
        deploy(ctx, a.p, trimmed, a.note, a.src, secretsChecked = true)
    }

    fun secretCancel() { secretAsk = null }

    // v49: answers of the file type dialog
    fun typePushAnyway(ctx: Context) {
        val a = typeAsk ?: return
        typeAsk = null
        deploy(ctx, a.p, a.pack, a.note, a.src, secretsChecked = true, typesChecked = true)
    }
    fun typeLeaveOut(ctx: Context) {
        val a = typeAsk ?: return
        typeAsk = null
        val drop = TypeCheck.dropSet(a.issues, a.pack.entries)
        val trimmed = a.pack.without(drop)
        zipPack = trimmed
        toast("Left out ${drop.size} file(s)")
        deploy(ctx, a.p, trimmed, a.note, a.src, secretsChecked = true, typesChecked = true)
    }
    fun typeCancel() { typeAsk = null }

    fun pickZip(ctx: Context, uri: Uri) = viewModelScope.launch {
        val pack = withContext(Dispatchers.IO) { runCatching { Zipper.read(ctx, uri) }.getOrNull() }
        if (pack == null || pack.entries.isEmpty()) { toast("Could not read that zip"); return@launch }
        zipPack = pack
        val base = pack.name.substringBefore("_v")
        projects.value.firstOrNull { Commands.fileBase(it).equals(base, true) }?.let { selId = it.id }
    }

    fun deploy(ctx: Context, p: Project, pack: Zipper.Pack, note: String, src: File? = null, secretsChecked: Boolean = false, typesChecked: Boolean = false) = viewModelScope.launch {
        if (deploying) return@launch
        if (!p.repo.contains("/")) { toast("Log in with GitHub first (Accounts)"); return@launch }
        // v42: keys, tokens and .env files are asked about before anything leaves the phone
        if (pack.findings.isNotEmpty() && !secretsChecked) { secretAsk = SecretAsk(p, pack, note, src); return@launch }
        // v49: file type check: does the zip fit the project type, and is there junk (apk, archives, node_modules)
        if (!typesChecked) {
            val issues = TypeCheck.scan(p.kind, pack.entries)
            if (issues.isNotEmpty()) { typeAsk = TypeAsk(p, pack, note, src, issues); return@launch }
        }
        deploying = true; deployLog.clear(); deployGen++
        try {
            val files = pack.entries.map { it.path to it.bytes }.toMutableList()
            val noWorkflow = files.none { it.first.startsWith(".github/workflows/") }
            if (p.kind == ProjectType.APP) {
                if (noWorkflow && !gh.exists(p.repo, ".github/workflows/build.yml")) {
                    files.add(".github/workflows/build.yml" to ctx.assets.open("build.yml").readBytes())
                    deployLog += "• added the standard build workflow"
                }
            } else if (noWorkflow) {
                // the standard workflow builds an Android APK, so it is never added to a website or server
                deployLog += if (isVercel(p)) "• Vercel builds this push by itself, no workflow needed"
                    else "• no workflow in this zip: include .github/workflows/ with your ${p.kind.label.lowercase()} deploy, or nothing will run"
            }
            val ver = pack.version.ifBlank { Commands.bump(p.version) }
            val msg = "$ver: ${note.replace("\n", " ").trim().ifBlank { "update from App Hub" }}"
            val before = gh.runs(p.repo).firstOrNull()?.id ?: 0L
            if (prefs.notifiedRun(p.id) == 0L && before != 0L) prefs.setNotifiedRun(p.id, before)
            val pushStart = System.currentTimeMillis()
            deployLog += "Pushing ${files.size} files to ${p.repo}…"
            val res = gh.pushFiles(p.repo, files, msg) { deployLog += it }
            if (res.ok) {
                (src ?: zipFound[p.id]?.takeIf { it.name == pack.name } ?: zipLatest[p.id]?.takeIf { it.name == pack.name })
                    ?.let { prefs.setZipSeen(p.id, Downloads.key(it)); zipFound.remove(p.id) }
                if (res.committed) {
                    projects.value.firstOrNull { it.id == p.id }?.let { dao.save(it.copy(version = ver)) }
                    prefs.setPushedVer(p.id, ver); pushedV[p.id] = ver
                    deployLog += "✓ Pushed $ver (${res.added} new, ${res.changed} updated). Watching the build…"
                    if (isVercel(p)) watchVercel(p, pushStart, deployGen) else watch(p, before, deployGen)
                }
            }
        } finally { deploying = false }
    }

    /** Follows the CI run like termux/release.sh: live status, compile errors on failure, installer on success. */
    private fun watch(p: Project, before: Long, gen: Int) = viewModelScope.launch {
        deployLog += "Waiting for the CI run…"
        var liveIdx = -1
        fun setLive(t: String) { if (liveIdx in deployLog.indices) deployLog[liveIdx] = t else { deployLog += t; liveIdx = deployLog.lastIndex } }
        val t0 = System.currentTimeMillis()
        repeat(135) {
            delay(4_000)
            if (gen != deployGen) return@launch   // a newer deploy took over the log
            val l = gh.runs(p.repo, 3).firstOrNull() ?: return@repeat   // one small call per check; full refresh only when it ends
            if (l.id == before) return@repeat
            if (l.ok == null) { setLive("… Build #${l.number} running  ${(System.currentTimeMillis() - t0) / 1000}s"); return@repeat }
            refresh(p).join()
            if (l.ok == true) {
                setLive("✓ Build #${l.number} passed  ${(System.currentTimeMillis() - t0) / 1000}s")
                if (p.kind == ProjectType.APP) {
                    toast("${p.name} build passed")
                    if (autoInstall) autoInstallLatest(p, gen) else deployLog += "Tap Install this build below."
                } else {
                    toast(if (p.kind == ProjectType.WEBSITE) "${p.name} deployed" else "${p.name} build passed")
                    deployLog += if (p.kind == ProjectType.WEBSITE) "✓ Deployed" else "✓ Deploy finished. Running once the endpoint answers."
                }
            } else {
                setLive("✗ Build #${l.number} failed")
                (errors[p.id] ?: "").lines().filter { it.isNotBlank() }.take(8).forEach { deployLog += "  $it" }
                deployLog += "Copy the errors below and paste them to the AI."
                toast("${p.name} build failed")
            }
            return@launch
        }
        if (gen == deployGen) deployLog += "• Still building. Check the Dashboard later."
    }

    /** Downloads the release APK and opens the installer. If Android blocks it (app in background), the Install button still works. */
    private suspend fun autoInstallLatest(p: Project, gen: Int) {
        val rel = gh.latest(p.repo)
        if (rel == null) { deployLog += "• No APK release found yet. Tap Install this build below."; return }
        deployLog += "Downloading ${rel.tag}…"
        val idx = deployLog.lastIndex
        var shown = 0
        val ok = downloadTracked(p, rel) { pc ->
            if (gen == deployGen && pc >= shown + 10) { shown = pc; if (idx in deployLog.indices) deployLog[idx] = "Downloading ${rel.tag}… $pc%" }
        }
        if (gen != deployGen) return
        if (!ok) { deployLog += "✗ Download failed. Tap Install this build below."; return }
        latest[p.id] = rel.tag
        if (idx in deployLog.indices) deployLog[idx] = "✓ ${rel.tag} downloaded"
        deployLog += "Opening the installer…"
        installCached(getApplication<Application>(), p)
    }

    /** Setup wizard: repo -> keystore -> secrets -> save project, all in-app via OAuth. */
    fun setup(name: String, repoName: String, pkg: String, version: String, create: Boolean, keys: Boolean,
              type: ProjectType = ProjectType.APP, liveUrl: String = "", target: String = "") = viewModelScope.launch {
        log.clear()
        if (login.isBlank() && !repoName.contains("/")) { log += "✗ Log in with GitHub (Accounts) or enter owner/name"; return@launch }
        val repo = if (repoName.contains("/")) repoName else "$login/$repoName"
        if (create) {
            log += "Creating repo $repo…"
            val c = gh.createRepo(repo.substringAfter("/")).first
            log += when (c) { 201 -> "✓ repo created"; 422 -> "• repo already exists"; else -> "✗ repo failed ($c)" }
        }
        var liveAddr = if (type == ProjectType.APP) "" else cleanUrl(liveUrl)
        // v52: Deploy to (websites and servers only). A failed target never stops the project from being saved.
        if (type != ProjectType.APP && target.isNotBlank()) {
            val plain = name.trim()
            when (target) {
                "vercel" -> {
                    log += "Linking Vercel…"
                    val (lk, err) = vercel.link(repo)
                    if (lk == null) log += "✗ Vercel: $err" else {
                        prefs.setVercelId(repo, lk.id); vercelRev++
                        if (liveAddr.isBlank()) liveAddr = lk.url
                        log += "✓ Vercel project linked (${lk.url})"
                    }
                }
                "pages" -> {
                    log += "Enabling GitHub Pages…"
                    val err = gh.enablePages(repo)
                    if (err.isNotBlank()) log += "✗ Pages: $err" else {
                        if (liveAddr.isBlank()) liveAddr = pagesUrl(repo)
                        log += "✓ GitHub Pages on (source: GitHub Actions). Live at ${pagesUrl(repo)} after the first deploy"
                    }
                }
                "render" -> {
                    log += "Creating Render service…"
                    val (sv, err) = render.create(plain, repo, type == ProjectType.WEBSITE)
                    if (sv == null) log += "✗ Render: $err" else {
                        if (liveAddr.isBlank()) liveAddr = sv.url
                        log += "✓ Render service created (${if (type == ProjectType.WEBSITE) "static site" else "web service"})"
                        loadRender()
                    }
                }
                "supabase" -> {
                    log += "Creating Supabase project…"
                    val (pj, err) = supabase.create(plain)
                    if (pj == null) log += "✗ Supabase: $err" else {
                        if (liveAddr.isBlank()) liveAddr = "https://${pj.id}.supabase.co"
                        log += "✓ Supabase project created (database password not kept)"
                        loadSupabase()
                    }
                }
            }
        }
        if (keys && type == ProjectType.APP) {
            log += "Generating signing keystore…"
            val s = Signing.generate(name)
            val b64 = Base64.encodeToString(s.p12, Base64.NO_WRAP)
            prefs.saveKeys(repo, "KEYSTORE_BASE64=$b64\nPASSWORD=${s.pass}\nALIAS=${s.alias}")
            val all = listOf("KEYSTORE_BASE64" to b64, "KEYSTORE_PASSWORD" to s.pass, "KEY_ALIAS" to s.alias, "KEY_PASSWORD" to s.pass)
            val ok = all.all { gh.setSecret(repo, it.first, it.second) }
            log += if (ok) "✓ 4 secrets set on GitHub" else "✗ setting secrets failed (check repo exists / token scope)"
        }
        val proj = Project(name = name, repo = repo, packageName = if (type == ProjectType.APP) pkg else "", version = version,
            stack = type.defaultStack, type = type.name, liveUrl = liveAddr)
        val id = dao.save(proj)
        selId = id
        copy(Prompts.adopt(proj), quiet = true)   // v49: adopt prompt for Claude, copied by itself
        log += "✓ Project added. Adopt prompt copied: send it to Claude with your project."
        log += "Then open Deploy and choose your zip to push and build."
    }

    // =====================================================================================
    // v27 features
    // =====================================================================================

    // ---- feature 3: notifications switch ----
    fun updateNotify(v: Boolean) { notifyOn = v; prefs.notifyOn = v }
    fun updateServerAlerts(v: Boolean) { serverAlerts = v; prefs.serverAlerts = v; ServerWatch.schedule(getApplication<Application>(), prefs) }   // v42

    // ---- feature 6: live sync choice + storage ----
    fun updateSyncSecs(v: Int) { syncSecs = v; prefs.syncSecs = v }
    val live: Boolean get() = syncSecs > 0
    /** Delay between two silent syncs. When live sync is off the loops only wake up once a second to notice a change of the setting. */
    val syncDelayMs: Long get() = if (syncSecs > 0) syncSecs * 1000L else 1000L

    fun apkBytes(): Long = ApkStore.usedBytes(getApplication<Application>())

    /** Deletes every saved APK (latest, previous and partial). Anything downloading right now is left alone. */
    fun clearApks() = viewModelScope.launch(Dispatchers.IO) {
        val app = getApplication<Application>()
        dao.all().first().filter { !dlProgress.containsKey(it.id) }.forEach { p ->
            dropCache(p); ApkStore.clearPrev(app, prefs, p.id); ApkStore.clearParts(app, p.id)
        }
        withContext(Dispatchers.Main) { toast("Saved APKs cleared") }
    }

    // ---- feature 5: rollback ----
    fun hasPrev(p: Project) = ApkStore.hasPrev(getApplication<Application>(), prefs, p.id)
    fun prevTag(p: Project): String = prefs.prevTag(p.id)

    /** Opens the installer for the previous build. Android refuses an older versionCode over a newer one (the screen warns first). */
    fun installPrevious(ctx: Context, p: Project) {
        val app = getApplication<Application>()
        if (!ApkStore.hasPrev(app, prefs, p.id)) { toast("No previous build saved"); return }
        prefs.setInstallTry(p.id, 0L)   // so the "installer was opened" rule can never mark the newer saved APK as installed
        gh.install(ctx, ApkStore.prev(app, p.id))
    }

    // ---- feature 7: release notes fall back to the commit message of the push that started the build ----
    /** Notes of the release; when empty, the message of the build run with the same number (release tags are build-N); else the default line. */
    fun notesFor(p: Project, tag: String, body: String): String {
        if (body.isNotBlank()) return body.trim()
        val num = Commands.num(tag).toIntOrNull()
        val msg = runs[p.id]?.firstOrNull { it.number == num }?.msg?.trim().orEmpty()
        return msg.ifBlank { DEFAULT_NOTES }
    }

    // ---- feature 2: backup ----
    fun exportBackup(ctx: Context, uri: Uri) = viewModelScope.launch {
        val list = projects.value
        val txt = Backup.toJson(list) { prefs.keys(it) }
        val ok = withContext(Dispatchers.IO) {
            runCatching { ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(txt.toByteArray()) } }.isSuccess
        }
        toast(if (ok) "Backup saved (${list.size} apps)" else "Could not write the backup file")
    }

    fun readBackup(ctx: Context, uri: Uri) = viewModelScope.launch {
        val txt = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(uri)!!.use { String(it.readBytes()) } }.getOrNull() }
        val d = txt?.let { Backup.parse(it) }
        if (d == null || d.apps.isEmpty()) toast("That file is not an App Hub backup") else pendingBackup = d
    }

    /** (apps that already exist and would be overwritten, keystores that would replace stored ones) */
    fun backupClashes(d: Backup.Data): Pair<Int, Int> =
        d.apps.count { a -> projects.value.any { it.repo.equals(a.repo, true) } } to d.keys.count { prefs.keys(it.key).isNotBlank() }

    fun applyBackup(d: Backup.Data) = viewModelScope.launch {
        val have = dao.all().first()
        var added = 0; var replaced = 0
        d.apps.forEach { a ->
            val m = have.firstOrNull { it.repo.equals(a.repo, true) }
            if (m != null) { dao.save(m.copy(name = a.name, packageName = a.pkg, version = a.version, stack = a.stack, type = a.kind, liveUrl = a.liveUrl)); replaced++ }
            else { dao.save(Project(name = a.name, repo = a.repo, packageName = a.pkg, version = a.version, stack = a.stack, type = a.kind, liveUrl = a.liveUrl)); added++ }
        }
        d.keys.forEach { (repo, v) -> prefs.saveKeys(repo, v) }
        pendingBackup = null
        toast("Imported: $added new, $replaced updated")
    }
}
