package com.krishna.apphub

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import org.json.JSONObject

/**
 * v90: the three App Hub workflows (build.yml, pages.yml, housekeeping.yml in assets/) and their options file .github/apphub.json.
 * The workflows read the options file themselves, so a change of option is one small commit marked [skip ci] (no build starts).
 * A workflow file is App Hub's own only if its first line holds "apphub-managed"; any other file is yours and is never replaced.
 */
object Wf {
    const val MARK = "apphub-managed"
    const val CFG = ".github/apphub.json"
    class Def(val file: String, val title: String, val sub: String)
    val all = listOf(
        Def("build.yml", "Build", "Finds Android, Node or plain site itself, builds, and publishes build-N on every push"),
        Def("pages.yml", "Pages", "Publishes a website to GitHub Pages on every push"),
        Def("housekeeping.yml", "Housekeeping", "Weekly: deletes old artifacts and old releases (private repo limit)"))
    fun path(file: String) = ".github/workflows/$file"
    fun asset(ctx: Context, file: String): ByteArray = ctx.assets.open(file).readBytes()
    private fun norm(s: String) = s.replace("\r\n", "\n").trim()
    fun same(a: String, b: String) = norm(a) == norm(b)

    enum class St { MISSING, CURRENT, OUTDATED, YOURS, UNKNOWN }

    /** [remote] = the file in the repo (null = not there). The old v≤89 build.yml of App Hub (no mark) counts as App Hub's own. */
    fun status(file: String, remote: String?, bundled: String, readError: Boolean): St = when {
        readError -> St.UNKNOWN
        remote == null -> St.MISSING
        same(remote, bundled) -> St.CURRENT
        remote.lineSequence().firstOrNull()?.contains(MARK) == true -> St.OUTDATED
        file == "build.yml" && remote.contains("name: Build APK") && remote.contains("Decode keystore") -> St.OUTDATED
        else -> St.YOURS
    }

    /** The options. Defaults match what the workflows use when a key is missing. */
    class Cfg(
        val buildOnPush: Boolean = true, val java: Int = 17, val gradle: String = "8.10.2", val variant: String = "release",
        val sign: String = "auto", val days: Int = 1, val keepReleases: Int = 10, val cleanup: Boolean = true, val pages: Boolean = true) {
        /** Writes the options into [base] so keys App Hub does not know stay as they are. */
        fun toJson(base: JSONObject?): JSONObject = (base ?: JSONObject()).apply {
            put("build_on_push", buildOnPush); put("java", java); put("gradle", gradle); put("variant", variant); put("sign", sign)
            put("artifact_days", days); put("keep_releases", keepReleases); put("cleanup", cleanup); put("pages", pages)
        }
        companion object {
            fun from(j: JSONObject?): Cfg {
                val d = Cfg()
                if (j == null) return d
                return Cfg(j.optBoolean("build_on_push", d.buildOnPush), j.optInt("java", d.java), j.optString("gradle", d.gradle).ifBlank { d.gradle },
                    j.optString("variant", d.variant).ifBlank { d.variant }, j.optString("sign", d.sign).ifBlank { d.sign }, j.optInt("artifact_days", d.days),
                    j.optInt("keep_releases", d.keepReleases), j.optBoolean("cleanup", d.cleanup), j.optBoolean("pages", d.pages))
            }
        }
    }
    /** What the page knows about one repo. [raw] = the options file as it is in the repo (null = none yet). */
    class State(val files: Map<String, St>, val cfg: Cfg, val raw: JSONObject?, val others: List<String> = emptyList())
}

private fun stText(s: Wf.St) = when (s) {
    Wf.St.MISSING -> "Not added" to Muted
    Wf.St.CURRENT -> "Up to date" to Green
    Wf.St.OUTDATED -> "Update ready" to Amber
    Wf.St.YOURS -> "Yours, kept" to Cyan
    Wf.St.UNKNOWN -> "Unknown" to Muted
}

/** Project > Settings > Workflows: each workflow with its state, and the options the workflows read. */
@Composable fun WorkflowsScreen(vm: HubVM, nav: NavHostController, id: Long) {
    val p = projectOrHome(vm, nav, id) ?: return
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val st = vm.wf[p.id]
    LaunchedEffect(p.id) { vm.wfMsg = ""; vm.loadWf(p) }
    var ask by remember { mutableStateOf<Wf.Def?>(null) }
    var buildOnPush by remember(st) { mutableStateOf(st?.cfg?.buildOnPush ?: true) }
    var cleanup by remember(st) { mutableStateOf(st?.cfg?.cleanup ?: true) }
    var pages by remember(st) { mutableStateOf(st?.cfg?.pages ?: true) }
    var sign by remember(st) { mutableStateOf(st?.cfg?.sign ?: "auto") }
    var variant by remember(st) { mutableStateOf(st?.cfg?.variant ?: "release") }
    var java by remember(st) { mutableStateOf((st?.cfg?.java ?: 17).toString()) }
    var gradle by remember(st) { mutableStateOf(st?.cfg?.gradle ?: "8.10.2") }
    var days by remember(st) { mutableStateOf((st?.cfg?.days ?: 1).toString()) }
    var keep by remember(st) { mutableStateOf((st?.cfg?.keepReleases ?: 10).toString()) }
    val cur = st?.cfg ?: Wf.Cfg()
    val now = Wf.Cfg(buildOnPush, java.toIntOrNull()?.coerceIn(8, 25) ?: cur.java, gradle.trim().ifBlank { cur.gradle }, variant, sign,
        days.toIntOrNull()?.coerceIn(1, 90) ?: cur.days, keep.toIntOrNull()?.coerceIn(2, 200) ?: cur.keepReleases, cleanup, pages)
    val dirty = st != null && now.toJson(null).toString() != cur.toJson(null).toString()

    ask?.let { d ->
        val s = st?.files?.get(d.file)
        HubDialog(if (s == Wf.St.MISSING) "Add ${d.file}?" else "Update ${d.file}?", { ask = null }, listOf(
            dPrimary(if (s == Wf.St.MISSING) (if (d.file != "housekeeping.yml" && !st?.others.isNullOrEmpty()) "Add anyway" else "Add") else "Update") { ask = null; vm.applyWf(ctx, p, d.file) }, dCancel { ask = null }),
            icon = Icons.Default.AccountTree, tone = Cyan, sub = p.repo) {
            DialogText("One commit puts ${Wf.path(d.file)} in the repo. It is marked [skip ci], so no build starts.")
            if (s == Wf.St.OUTDATED) DialogNote("The file now there is replaced by App Hub's newer one.")
            if (d.file == "pages.yml") DialogNote("Needs GitHub Pages on with the source GitHub Actions (Settings > Hosting).")
            if (d.file != "housekeeping.yml" && s == Wf.St.MISSING && !st?.others.isNullOrEmpty())   // v91
                DialogNote("This repo already has ${st!!.others.joinToString(", ")}. If one of them also builds or deploys, both run on every push.", Amber)
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Header("Workflows", nav) { IconButton({ vm.loadWf(p) }, Modifier.size(40.dp)) { Icon(Icons.Default.Refresh, null, tint = Muted) } }
        Panel {
            Text(p.repo, fontWeight = FontWeight.Bold, color = Cyan)
            Text("App Hub's workflows adapt to the project, and read their options from .github/apphub.json in the repo. A workflow file of your own is never replaced.",
                Modifier.padding(top = 4.dp), fontSize = 11.sp, color = Muted)
            if (vm.wfBusy) Text("Working…", Modifier.padding(top = 4.dp), fontSize = 12.sp, color = Cyan)
            else if (vm.wfMsg.isNotBlank()) Text(vm.wfMsg, Modifier.padding(top = 4.dp), fontSize = 12.sp, color = Amber)
        }
        Wf.all.forEach { d ->
            val s = st?.files?.get(d.file)
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(d.title, fontWeight = FontWeight.Bold)
                        Text(d.sub, fontSize = 11.sp, color = Muted)
                    }
                    if (s != null) stText(s).let { Pill(it.first, it.second) } else Text("…", color = Muted)
                }
                if (s == Wf.St.MISSING || s == Wf.St.OUTDATED) OutlinedButton({ ask = d }, Modifier.fillMaxWidth().padding(top = 6.dp), enabled = !vm.wfBusy) {
                    BtnText(if (s == Wf.St.MISSING) "Add to repo" else "Update")
                }
            }
        }
        Panel {
            SectionTitle(Icons.Default.Tune, "Options")
            SettingRow(Icons.Default.PlayArrow, "Build on every push", "Off = builds start only from the Build button", Cyan) { HubSwitch(buildOnPush) { buildOnPush = it } }
            SettingRow(Icons.Default.Public, "Publish to Pages", "Off = pages.yml does nothing", Cyan) { HubSwitch(pages) { pages = it } }
            SettingRow(Icons.Default.DeleteSweep, "Weekly cleanup", "Old artifacts and old releases", Cyan) { HubSwitch(cleanup) { cleanup = it } }
            Spacer(Modifier.height(6.dp))
            Text("Signing", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("auto" to "Auto (keystore if set)", "debug" to "Debug key").forEach { (k, t) ->
                    FilterChip(sign == k, { sign = k }, { Text(t, fontSize = 12.sp) })
                }
            }
            Text("APK type", Modifier.padding(top = 6.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("release", "debug").forEach { k -> FilterChip(variant == k, { variant = k }, { Text(k, fontSize = 12.sp) }) }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HubField("Java", java, { java = it.filter(Char::isDigit).take(2) }, "17", Modifier.weight(1f), singleLine = true)
                HubField("Gradle", gradle, { gradle = it.take(12) }, "8.10.2", Modifier.weight(1f), singleLine = true)
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HubField("Artifact days", days, { days = it.filter(Char::isDigit).take(2) }, "1", Modifier.weight(1f), singleLine = true)
                HubField("Keep releases", keep, { keep = it.filter(Char::isDigit).take(3) }, "10", Modifier.weight(1f), singleLine = true)
            }
            Text("Android projects only: signing, APK type, Java, Gradle. Keep releases counts build-N releases and never goes below 2.",
                Modifier.padding(top = 6.dp), fontSize = 11.sp, color = Muted)
        }
        BigButton(if (dirty) "Save options" else "Options saved", Icons.Default.Save) { if (dirty && !vm.wfBusy) vm.saveWfCfg(p, now) }
        Spacer(Modifier.height(12.dp))
    }
}
