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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HubVM(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    val gh = GitHub(prefs)
    private val dao = Room.databaseBuilder(app, Db::class.java, "hub").build().dao()

    val projects = dao.all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val runs = mutableStateMapOf<Long, List<GitHub.Run>>()
    val errors = mutableStateMapOf<Long, String>()
    val log = mutableStateListOf<String>()
    val latest = mutableStateMapOf<Long, String>()
    val pins = mutableStateMapOf<Long, Boolean>()
    val installed = mutableStateMapOf<Long, String>()
    var refreshing by mutableStateOf(false)

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

    fun isPinned(id: Long) = pins[id] ?: prefs.pinned(id)
    fun togglePin(p: Project) { val v = !isPinned(p.id); pins[p.id] = v; prefs.setPinned(p.id, v) }
    fun installedTag(id: Long) = installed[id] ?: prefs.installed(id)
    fun markInstalled(id: Long, tag: String) { installed[id] = tag; prefs.setInstalled(id, tag) }
    fun hasUpdate(p: Project): Boolean {
        val l = latest[p.id] ?: return false
        val i = installedTag(p.id)
        return i.isNotBlank() && i != l
    }

    /** Adds the preset apps that are not in the list yet. */
    private suspend fun seedMissing(): Int {
        val have = dao.all().first()
        var n = 0
        Presets.all.forEach { pr ->
            if (have.none { it.name.equals(pr.name, true) || it.repo.substringAfter("/").equals(pr.repo, true) }) {
                dao.save(Project(name = pr.name, repo = if (login.isBlank()) pr.repo else "$login/${pr.repo}",
                    packageName = pr.pkg, localPath = pr.path, version = pr.version, stack = pr.stack))
                n++
            }
        }
        return n
    }

    /** Projects saved without an owner get "login/" once you are logged in. */
    private suspend fun fixOwners() {
        if (login.isBlank()) return
        dao.all().first().filter { !it.repo.contains("/") }.forEach { dao.save(it.copy(repo = "$login/${it.repo}")) }
    }

    fun restorePresets() = viewModelScope.launch {
        val n = seedMissing(); fixOwners()
        toast(if (n > 0) "Added $n preset app(s)" else "All preset apps are already added")
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
    fun refresh(p: Project) = viewModelScope.launch {
        if (!p.repo.contains("/")) return@launch
        val r = gh.runs(p.repo); runs[p.id] = r
        val last = r.firstOrNull()
        val status = when { last == null -> "none"; last.ok == null -> "running"; last.ok == true -> "passed"; else -> "failed" }
        val err = if (status == "failed") gh.errorOf(p.repo, last!!.id) else ""
        errors[p.id] = err
        gh.latest(p.repo)?.let { latest[p.id] = it.tag }
        // keep the version label in step with the last pushed "vNNN: ..." commit (only ever upwards)
        val pushed = last?.msg?.let { Regex("^v\\d+").find(it)?.value }
        val cur = projects.value.firstOrNull { it.id == p.id } ?: return@launch
        val newer = pushed != null && (Commands.num(pushed).toIntOrNull() ?: 0) > (Commands.num(cur.version).toIntOrNull() ?: 0)
        dao.save(cur.copy(version = if (newer) pushed!! else cur.version, lastStatus = status, lastError = err))
    }

    fun refreshAll() = viewModelScope.launch {
        refreshing = true
        projects.value.map { refresh(it) }.forEach { it.join() }
        refreshing = false
    }
    fun update(p: Project) = viewModelScope.launch { dao.save(p) }
    fun delete(p: Project) = viewModelScope.launch { dao.delete(p) }

    fun build(p: Project) = viewModelScope.launch {
        toast(if (gh.dispatch(p.repo)) "Build triggered on GitHub" else "Trigger failed — push the workflow first")
    }

    fun push(ctx: Context, p: Project) = runScript(ctx, p, p.version, false, false, true, false, false)
    fun termuxSetup(ctx: Context, p: Project) = runScript(ctx, p, p.version, true, false, false, false, false)

    fun runScript(ctx: Context, p: Project, v: String, su: Boolean, b: Boolean, pu: Boolean, r: Boolean, z: Boolean) {
        val ok = Commands.runInTermux(ctx, Commands.script(p, v, su, b, pu, r, z, true, prefs.token, Commands.files(ctx)))
        if (!ok) toast("Termux not available — enable RUN_COMMAND (see Settings)")
    }

    /** New app from a zip in Downloads: add project, unzip, create repo + keys, push, build, install (all in Termux). */
    fun setupFromZip(ctx: Context, name: String, repoName: String, path: String, pkg: String, version: String) = viewModelScope.launch {
        log.clear()
        if (login.isBlank() && !repoName.contains("/")) { log += "✗ Log in with GitHub (Settings) or enter owner/name"; return@launch }
        val repo = if (repoName.contains("/")) repoName else "$login/$repoName"
        val home = "/data/data/com.termux/files/home/"
        val dir = if (path.trimEnd('/') == home.trimEnd('/')) home + name.filter { it.isLetterOrDigit() } else path
        val p = Project(name = name, repo = repo, packageName = pkg, localPath = dir, version = version)
        dao.save(p)
        val ok = Commands.runInTermux(ctx, Commands.setupFromZip(p, prefs.token, Commands.files(ctx)))
        log += if (ok) "✓ Project added. Termux is unzipping ${Commands.fileBase(p)}_v*.zip, creating repo + keys, pushing and building…"
               else "✗ Termux not available — enable RUN_COMMAND (see Settings)"
    }

    fun deployLatest(ctx: Context, p: Project) {
        if (!Commands.runInTermux(ctx, Commands.deployLatest(p))) toast("Termux not available — enable RUN_COMMAND (see Settings)")
    }

    fun keyBackup(p: Project) = prefs.keys(p.repo)

    /** Setup wizard: repo -> keystore -> secrets -> save project (in-app via OAuth, or via Termux gh). */
    fun setup(ctx: Context, name: String, repoName: String, path: String, pkg: String, version: String,
              create: Boolean, keys: Boolean, viaTermux: Boolean) = viewModelScope.launch {
        log.clear()
        if (login.isBlank() && !repoName.contains("/")) { log += "✗ Log in with GitHub (Settings) or enter owner/name"; return@launch }
        val repo = if (repoName.contains("/")) repoName else "$login/$repoName"
        if (viaTermux) {
            val p = Project(name = name, repo = repo, packageName = pkg, localPath = path, version = version)
            dao.save(p)
            termuxSetup(ctx, p)
            log += "✓ Project added. Termux is creating repo, signing key and secrets…"
            return@launch
        }
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
        dao.save(Project(name = name, repo = repo, packageName = pkg, localPath = path, version = version))
        log += "✓ Project added. Tap Push to release."
    }
}
