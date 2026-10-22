package com.krishna.apphub

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
    val infos = mutableStateMapOf<Long, GitHub.Info>()
    val commits = mutableStateMapOf<Long, List<GitHub.Commit>>()
    val releases = mutableStateMapOf<Long, List<GitHub.Release>>()
    val jobs = mutableStateMapOf<Long, List<GitHub.Job>>()
    var refreshing by mutableStateOf(false)

    var zipPack by mutableStateOf<Zipper.Pack?>(null)
    var deploying by mutableStateOf(false)
    val deployLog = mutableStateListOf<String>()

    var login by mutableStateOf(prefs.login)
    var userCode by mutableStateOf<String?>(null)
    var selId by mutableLongStateOf(0L)
    var task by mutableStateOf("Fix error")

    init {
        viewModelScope.launch {
            if (!prefs.seeded) { seedMissing(); prefs.seeded = true }
            fixOwners()
        }
    }

    private fun n(v: String) = Commands.num(v).toIntOrNull() ?: 0
    private fun vOf(m: String) = Regex("^v\\d+").find(m)?.value

    fun isPinned(id: Long) = pins[id] ?: prefs.pinned(id)
    fun togglePin(p: Project) { val v = !isPinned(p.id); pins[p.id] = v; prefs.setPinned(p.id, v) }
    fun installedTag(id: Long) = installed[id] ?: prefs.installed(id)
    fun markInstalled(id: Long, tag: String) { installed[id] = tag; prefs.setInstalled(id, tag) }
    fun hasUpdate(p: Project): Boolean {
        val l = latest[p.id] ?: return false
        val i = installedTag(p.id)
        return i.isNotBlank() && i != l
    }

    /** True when the version label is ahead of what was last pushed (an update waiting to be pushed). */
    fun pending(p: Project): Boolean {
        val pv = pushedV[p.id] ?: prefs.pushedVer(p.id)
        return pv.isNotBlank() && n(p.version) > n(pv)
    }

    private suspend fun seedMissing(): Int {
        val have = dao.all().first()
        var c = 0
        Presets.all.forEach { pr ->
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
        if (ok) { login = gh.user() ?: ""; prefs.login = login; toast("Logged in as $login"); fixOwners(); refreshAll() } else toast("Login failed")
    }

    fun logout() { prefs.token = ""; prefs.login = ""; login = "" }

    // ---- projects ----
    /**
     * Pulls build runs and keeps the version label in step automatically, however the build was triggered
     * (app push, GitHub website, re-run, Build button): a pushed "vNNN:" message sets the version, anything else bumps it by one.
     */
    fun refresh(p: Project) = viewModelScope.launch {
        if (!p.repo.contains("/")) return@launch
        val r = gh.runs(p.repo); runs[p.id] = r
        val last = r.firstOrNull()
        val status = when { last == null -> "none"; last.ok == null -> "running"; last.ok == true -> "passed"; else -> "failed" }
        val err = if (status == "failed") gh.errorOf(p.repo, last!!.id) else ""
        errors[p.id] = err
        gh.latest(p.repo)?.let { latest[p.id] = it.tag }
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
                val label = if (n(cur.version) > n(pushed)) cur.version else pushed
                dao.save(cur.copy(version = label, lastStatus = status, lastError = err))
            }
        }
    }

    fun refreshAll() = viewModelScope.launch {
        refreshing = true
        projects.value.map { refresh(it) }.forEach { it.join() }
        refreshing = false
    }

    /** Repo info, recent commits and releases, fetched inside the app. */
    fun loadDetails(p: Project) = viewModelScope.launch {
        if (!p.repo.contains("/")) return@launch
        gh.info(p.repo)?.let { infos[p.id] = it }
        commits[p.id] = gh.commits(p.repo)
        releases[p.id] = gh.releases(p.repo)
    }

    fun loadJobs(p: Project, runId: Long) = viewModelScope.launch { jobs[runId] = gh.jobs(p.repo, runId) }

    fun update(p: Project) = viewModelScope.launch { dao.save(p) }
    fun delete(p: Project) = viewModelScope.launch { dao.delete(p) }

    fun build(p: Project) = viewModelScope.launch {
        toast(if (gh.dispatch(p.repo)) "Build triggered on GitHub" else "Trigger failed — push the workflow first")
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

    fun deploy(ctx: Context, p: Project, pack: Zipper.Pack, note: String) = viewModelScope.launch {
        if (deploying) return@launch
        if (!p.repo.contains("/")) { toast("Log in with GitHub first (Settings)"); return@launch }
        deploying = true; deployLog.clear()
        try {
            val files = pack.entries.map { it.path to it.bytes }.toMutableList()
            if (files.none { it.first.startsWith(".github/workflows/") } && !gh.exists(p.repo, ".github/workflows/build.yml")) {
                files.add(".github/workflows/build.yml" to ctx.assets.open("build.yml").readBytes())
                deployLog += "• added the standard build workflow"
            }
            val ver = pack.version.ifBlank { Commands.bump(p.version) }
            val msg = "$ver: update from App Hub"
            val before = gh.runs(p.repo).firstOrNull()?.id ?: 0L
            deployLog += "Pushing ${files.size} files to ${p.repo}…"
            val ok = gh.pushFiles(p.repo, files, msg) { deployLog += it }
            if (ok) {
                projects.value.firstOrNull { it.id == p.id }?.let { dao.save(it.copy(version = ver)) }
                deployLog += "✓ Pushed $ver. Watching the build…"
                watch(p, before)
            }
        } finally { deploying = false }
    }

    /** Polls until the new build finishes, then tells you. */
    private fun watch(p: Project, before: Long) = viewModelScope.launch {
        repeat(48) {
            delay(10_000)
            refresh(p).join()
            val l = runs[p.id]?.firstOrNull()
            if (l != null && l.id != before && l.ok != null) {
                toast(if (l.ok == true) "${p.name} build passed — tap Install" else "${p.name} build failed")
                return@launch
            }
        }
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
