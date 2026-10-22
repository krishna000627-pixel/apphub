package com.krishna.apphub

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
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
    private val dao = Room.databaseBuilder(app, Db::class.java, "hub").build().dao()
    private val lock = Mutex()

    val projects = dao.all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val runs = mutableStateMapOf<Long, List<GitHub.Run>>()
    val errors = mutableStateMapOf<Long, String>()
    val log = mutableStateListOf<String>()
    val latest = mutableStateMapOf<Long, String>()
    val pins = mutableStateMapOf<Long, Boolean>()
    val installed = mutableStateMapOf<Long, String>()
    val pushedV = mutableStateMapOf<Long, String>()
    val autoV = mutableStateMapOf<Long, Boolean>()
    val infos = mutableStateMapOf<Long, GitHub.Info>()
    val commits = mutableStateMapOf<Long, List<GitHub.Commit>>()
    val releases = mutableStateMapOf<Long, List<GitHub.Release>>()
    val jobs = mutableStateMapOf<Long, List<GitHub.Job>>()
    val cached = mutableStateMapOf<Long, String>()    // release tag of the APK waiting in the cache, per app
    val zipFound = mutableStateMapOf<Long, File>()    // newest unpushed zip per app
    val zipLatest = mutableStateMapOf<Long, File>()   // newest zip per app, pushed or not
    val zipDismissed = mutableStateListOf<String>()   // "ask me later" for this session
    var refreshing by mutableStateOf(false)

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
    var userCode by mutableStateOf<String?>(null)
    var selId by mutableLongStateOf(0L)
    var task by mutableStateOf("Fix error")

    // new-app prompt maker (kept here so it survives switching tabs)
    var ideaCat by mutableStateOf("Productivity")
    var ideaName by mutableStateOf("")
    var ideaPitch by mutableStateOf("")
    var ideaNotes by mutableStateOf("")
    var draftName by mutableStateOf("")

    // full build-log viewer
    var logTitle by mutableStateOf("")
    var logLines by mutableStateOf<List<String>>(emptyList())
    var logLoading by mutableStateOf(false)

    // ---- local snapshot: last known GitHub data, so the UI is filled instantly and only syncs when stale ----
    private val snapDir get() = File(getApplication<Application>().cacheDir, "snap")
    private val snapped = mutableSetOf<Long>()
    private val staleMs = 10 * 60_000L
    private fun fresh(at: Long) = System.currentTimeMillis() - at < staleMs

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
        val l = latest[p.id] ?: return false
        val i = installedTag(p.id)
        return i.isNotBlank() && i != l
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
    suspend fun fetchApk(p: Project, r: GitHub.Rel, onProgress: (Long, Long) -> Unit): Boolean {
        val ok = ApkStore.fetch(getApplication<Application>(), gh, prefs, p, r, onProgress)
        if (ok) cached[p.id] = r.tag
        return ok
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
        apkFile(p).delete(); File(apkFile(p).path + ".part").delete()
        prefs.setCachedTag(p.id, ""); prefs.setInstallTry(p.id, 0L); cached.remove(p.id)
    }

    /** Drops the saved APK only when a newer release exists or the file is gone. An installed one stays, so Reinstall needs no download. */
    private fun pruneOne(p: Project) {
        val tag = cachedTag(p.id); if (tag.isBlank()) return
        val newer = latest[p.id]?.let { it != tag } == true
        if (newer || !apkFile(p).exists()) dropCache(p)
    }

    /** Confirms installs that finished (package updated after the installer was opened), then clears used or stale APKs. */
    private val checking = java.util.concurrent.atomic.AtomicBoolean(false)
    private val detecting = mutableSetOf<Long>()   // apps whose package name was already looked up online this session

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
                    val ver = runCatching { pm.getPackageInfo(p.packageName, 0).versionName ?: "?" }.getOrNull()
                    onPhone[p.id] = ver ?: ""
                    if (ver == null && installedTag(p.id).isNotBlank()) markInstalled(p.id, "")
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
            val keep = list.filter { cachedTag(it.id).isNotBlank() }.map { apkFile(it).name }.toSet()
            apkDir.listFiles()?.filter { it.name !in keep && !it.name.endsWith(".part") }?.forEach { it.delete() }
        } finally { checking.set(false) }
    }

    /** True when the version label is ahead of what was last pushed (an update waiting to be pushed). */
    fun pending(p: Project): Boolean {
        if (zipFound.containsKey(p.id)) return true
        val pv = pushedV[p.id] ?: prefs.pushedVer(p.id)
        return pv.isNotBlank() && n(p.version) > n(pv)
    }

    /** What the + icon is waiting to push: the new zip's version, or the bumped label. */
    fun waitLabel(p: Project): String = zipFound[p.id]?.let { Zipper.versionOf(it.name).ifBlank { it.name } } ?: p.version

    fun zipKey(id: Long, f: File) = "$id|${Downloads.key(f)}"

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
                    val waiting = prefs.zipSeen(p.id) != Downloads.key(f) && (zv.isBlank() || n(zv) > n(pushed))
                    if (waiting) zipFound[p.id] = f else zipFound.remove(p.id)
                }
            }
        }
    }

    /** Shows what a push would change (new / changed files) without pushing anything. */
    fun preview(p: Project, pack: Zipper.Pack) = viewModelScope.launch {
        if (deploying) return@launch
        if (!p.repo.contains("/")) { toast("Log in with GitHub first (Settings)"); return@launch }
        deploying = true; deployLog.clear(); deployGen++
        try {
            deployLog += "Comparing ${pack.entries.size} files with ${p.repo}…"
            gh.pushFiles(p.repo, pack.entries.map { it.path to it.bytes }, "preview", dryRun = true) { deployLog += it }
        } finally { deploying = false }
    }

    /** Adds a commit that restores the previous version of the code (history is kept). */
    fun undoLastPush(p: Project) = viewModelScope.launch {
        if (!p.repo.contains("/")) { toast("Log in with GitHub first (Settings)"); return@launch }
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
    fun toast(s: String) = Toast.makeText(getApplication(), s, Toast.LENGTH_SHORT).show()

    fun copy(text: String) {
        val cm = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("apphub", text)); toast("Copied")
    }

    // ---- login ----
    fun startLogin() = viewModelScope.launch {
        val j = gh.deviceCode()
        if (!j.has("user_code")) { toast("Set a valid Client ID in Settings"); return@launch }
        userCode = j.getString("user_code"); copy(userCode!!)
        getApplication<Application>().startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(j.getString("verification_uri"))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val ok = gh.pollToken(j.getString("device_code"), j.optInt("interval", 5))
        userCode = null
        if (ok) { login = gh.user() ?: ""; prefs.login = login; toast("Logged in as $login"); fixOwners(); refreshAll(true) } else toast("Login failed")
    }

    fun logout() { prefs.token = ""; prefs.login = ""; login = "" }

    // ---- projects ----
    /**
     * Pulls build runs and keeps the version label in step automatically, however the build was triggered
     * (app push, GitHub website, re-run, Build button): a pushed "vNNN:" message sets the version, anything else bumps it by one.
     */
    fun refresh(p: Project, force: Boolean = true) = viewModelScope.launch {
        if (!p.repo.contains("/")) return@launch
        // fresh local snapshot and no build in progress: nothing to fetch
        if (!force && p.id in snapped && p.lastStatus != "running" && fresh(prefs.syncedAt(p.id))) return@launch
        val r = gh.runs(p.repo)
        if (r.isEmpty() && runs[p.id] != null) return@launch   // offline or API error: keep what is cached
        runs[p.id] = r
        val last = r.firstOrNull()
        val status = when { last == null -> "none"; last.ok == null -> "running"; last.ok == true -> "passed"; else -> "failed" }
        val err = if (status == "failed") gh.errorOf(p.repo, last!!.id) else ""
        errors[p.id] = err
        val rel = gh.latest(p.repo)
        rel?.let { latest[p.id] = it.tag }
        pruneOne(p)
        // a newer release than the installed one: save its APK in the app now, so Update is one tap
        if (autoDownload && rel != null && hasUpdate(p) && cachedTag(p.id) != rel.tag && downloading.add(p.id)) {
            viewModelScope.launch { try { fetchApk(p, rel) { _, _ -> } } finally { downloading.remove(p.id) } }
        }
        lock.withLock {
            val cur = projects.value.firstOrNull { it.id == p.id }
            if (cur != null) {
                val seen = prefs.lastRun(p.id)
                var pushed = prefs.pushedVer(p.id).ifBlank { cur.version }
                if (seen > 0L) {
                    r.filter { it.id > seen }.sortedBy { it.id }.forEach { run ->
                        val v = vOf(run.msg)
                        pushed = if (v != null && run.event != "workflow_dispatch") v else Commands.bump(pushed)
                    }
                } else if (last != null) {
                    val v = vOf(last.msg)
                    if (v != null && last.event != "workflow_dispatch") pushed = v
                }
                if (r.isNotEmpty()) prefs.setLastRun(p.id, r.maxOf { it.id })
                prefs.setPushedVer(p.id, pushed); pushedV[p.id] = pushed
                val label = if (!prefs.autoVer(p.id) || n(cur.version) > n(pushed)) cur.version else pushed
                dao.save(cur.copy(version = label, lastStatus = status, lastError = err))
            }
        }
        prefs.setSyncedAt(p.id, System.currentTimeMillis()); saveSnap(p)
    }

    fun refreshAll(force: Boolean = false) = viewModelScope.launch {
        refreshing = true
        projects.value.map { refresh(it, force) }.forEach { it.join() }
        scanZips(getApplication())
        checkInstalls()
        refreshing = false
    }

    /** Repo info, recent commits and releases, fetched inside the app. */
    fun loadDetails(p: Project, force: Boolean = true) = viewModelScope.launch {
        if (!p.repo.contains("/")) return@launch
        if (!force && p.id in snapped && infos[p.id] != null && fresh(prefs.detailsAt(p.id))) return@launch
        gh.info(p.repo)?.let { infos[p.id] = it }
        gh.commits(p.repo).let { if (it.isNotEmpty()) commits[p.id] = it }
        gh.releases(p.repo).let { if (it.isNotEmpty()) releases[p.id] = it }
        prefs.setDetailsAt(p.id, System.currentTimeMillis()); saveSnap(p)
    }

    fun openLog(p: Project, j: GitHub.Job) = viewModelScope.launch {
        logTitle = "${p.name} • ${j.name}"; logLines = emptyList(); logLoading = true
        val t = gh.jobLog(p.repo, j.id)
        val ts = Regex("^\\d{4}-\\d\\d-\\d\\dT[\\d:.]+Z ")
        logLines = if (t.isBlank()) listOf("No log available (the job may still be running).") else t.lines().map { it.replaceFirst(ts, "") }
        logLoading = false
    }

    fun loadJobs(p: Project, runId: Long) = viewModelScope.launch { jobs[runId] = gh.jobs(p.repo, runId) }

    fun update(p: Project) = viewModelScope.launch { dao.save(p) }
    fun delete(p: Project) = viewModelScope.launch { dropCache(p); SnapCache.drop(snapDir, p.id); snapped -= p.id; dao.delete(p) }

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
    fun pickZip(ctx: Context, uri: Uri) = viewModelScope.launch {
        val pack = withContext(Dispatchers.IO) { runCatching { Zipper.read(ctx, uri) }.getOrNull() }
        if (pack == null || pack.entries.isEmpty()) { toast("Could not read that zip"); return@launch }
        zipPack = pack
        val base = pack.name.substringBefore("_v")
        projects.value.firstOrNull { Commands.fileBase(it).equals(base, true) }?.let { selId = it.id }
    }

    fun deploy(ctx: Context, p: Project, pack: Zipper.Pack, note: String, src: File? = null) = viewModelScope.launch {
        if (deploying) return@launch
        if (!p.repo.contains("/")) { toast("Log in with GitHub first (Settings)"); return@launch }
        deploying = true; deployLog.clear(); deployGen++
        try {
            val files = pack.entries.map { it.path to it.bytes }.toMutableList()
            if (files.none { it.first.startsWith(".github/workflows/") } && !gh.exists(p.repo, ".github/workflows/build.yml")) {
                files.add(".github/workflows/build.yml" to ctx.assets.open("build.yml").readBytes())
                deployLog += "• added the standard build workflow"
            }
            val ver = pack.version.ifBlank { Commands.bump(p.version) }
            val msg = "$ver: ${note.replace("\n", " ").trim().ifBlank { "update from App Hub" }}"
            val before = gh.runs(p.repo).firstOrNull()?.id ?: 0L
            deployLog += "Pushing ${files.size} files to ${p.repo}…"
            val res = gh.pushFiles(p.repo, files, msg) { deployLog += it }
            if (res.ok) {
                (src ?: zipFound[p.id]?.takeIf { it.name == pack.name } ?: zipLatest[p.id]?.takeIf { it.name == pack.name })
                    ?.let { prefs.setZipSeen(p.id, Downloads.key(it)); zipFound.remove(p.id) }
                if (res.committed) {
                    projects.value.firstOrNull { it.id == p.id }?.let { dao.save(it.copy(version = ver)) }
                    deployLog += "✓ Pushed $ver (${res.added} new, ${res.changed} updated). Watching the build…"
                    watch(p, before, deployGen)
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
                toast("${p.name} build passed")
                if (autoInstall) autoInstallLatest(p, gen) else deployLog += "Tap Install this build below."
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
        var last = 0
        val ok = fetchApk(p, rel) { d, t ->
            if (t > 0 && gen == deployGen) {
                val pc = (d * 100 / t).toInt()
                if (pc >= last + 10) { last = pc; if (idx in deployLog.indices) deployLog[idx] = "Downloading ${rel.tag}… $pc%" }
            }
        }
        if (gen != deployGen) return
        if (!ok) { deployLog += "✗ Download failed. Tap Install this build below."; return }
        latest[p.id] = rel.tag
        if (idx in deployLog.indices) deployLog[idx] = "✓ ${rel.tag} downloaded"
        deployLog += "Opening the installer…"
        installCached(getApplication<Application>(), p)
    }

    /** Setup wizard: repo -> keystore -> secrets -> save project, all in-app via OAuth. */
    fun setup(name: String, repoName: String, pkg: String, version: String, create: Boolean, keys: Boolean) = viewModelScope.launch {
        log.clear()
        if (login.isBlank() && !repoName.contains("/")) { log += "✗ Log in with GitHub (Settings) or enter owner/name"; return@launch }
        val repo = if (repoName.contains("/")) repoName else "$login/$repoName"
        if (create) {
            log += "Creating repo $repo…"
            val c = gh.createRepo(repo.substringAfter("/")).first
            log += when (c) { 201 -> "✓ repo created"; 422 -> "• repo already exists"; else -> "✗ repo failed ($c)" }
        }
        if (keys) {
            log += "Generating signing keystore…"
            val s = Signing.generate(name)
            val b64 = Base64.encodeToString(s.p12, Base64.NO_WRAP)
            prefs.saveKeys(repo, "KEYSTORE_BASE64=$b64\nPASSWORD=${s.pass}\nALIAS=${s.alias}")
            val all = listOf("KEYSTORE_BASE64" to b64, "KEYSTORE_PASSWORD" to s.pass, "KEY_ALIAS" to s.alias, "KEY_PASSWORD" to s.pass)
            val ok = all.all { gh.setSecret(repo, it.first, it.second) }
            log += if (ok) "✓ 4 secrets set on GitHub" else "✗ setting secrets failed (check repo exists / token scope)"
        }
        val id = dao.save(Project(name = name, repo = repo, packageName = pkg, version = version))
        selId = id
        log += "✓ Project added. Open Deploy and choose your zip to push and build."
    }
}
