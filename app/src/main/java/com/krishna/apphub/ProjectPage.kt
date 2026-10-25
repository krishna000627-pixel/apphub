package com.krishna.apphub

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay

/**
 * v64: the project as a hub with sub pages instead of tabs. No bottom bar on any project page (the app hides it there);
 * you move with the back arrow. Home = detail/{id}. Sub pages: builds/{id}, releases/{id} (apps), psettings/{id};
 * files/{id} and info/{id} already existed. A failed-build notification opens builds/{id} on top of the project home.
 */
/** The project with this id, or null. If it is gone (deleted) the page sends you home instead of showing an empty screen. */
@Composable fun projectOrHome(vm: HubVM, nav: NavHostController, id: Long): Project? {
    val p = vm.projects.collectAsState().value.firstOrNull { it.id == id }
    val now by rememberUpdatedState(p)
    LaunchedEffect(p == null) {
        if (p == null) {
            delay(500)   // the list may still be loading
            if (now == null) nav.navigate("dash") { popUpTo("dash"); launchSingleTop = true }
        }
    }
    return p
}

private val PadBtn = PaddingValues(horizontal = 8.dp, vertical = 10.dp)

/** 130 -> "2m 10s", 45 -> "45s", 3900 -> "1h 5m". */
fun fmtDur(sec: Int): String = when {
    sec < 60 -> "${sec}s"
    sec < 3600 -> "${sec / 60}m ${sec % 60}s"
    else -> "${sec / 3600}h ${(sec % 3600) / 60}m"
}

private fun statusWord(ok: Boolean?) = when (ok) { true -> "passed"; false -> "failed"; else -> "running" }

/** Keeps a project page fresh: files count, builds, installs, commits and releases (same loop the old project page had). */
@Composable private fun KeepFresh(vm: HubVM, p: Project) {
    val id = p.id
    LaunchedEffect(id) { if (vm.repoFiles[id] == null) vm.loadFiles(p, quiet = true) }
    LaunchedEffect(id, p.repo) { vm.refresh(p); vm.checkInstalls(); vm.loadDetails(p).join(); while (true) { delay(vm.syncDelayMs); if (vm.live) vm.loadDetails(p).join() } }
}

@Composable fun ProjectScreen(vm: HubVM, nav: NavHostController, id: Long) {
    val p = projectOrHome(vm, nav, id) ?: return
    KeepFresh(vm, p)
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ProjectHeader(vm, nav, p)
        HomeBody(vm, nav, p)
    }
}

@Composable fun BuildsScreen(vm: HubVM, nav: NavHostController, id: Long) {
    val p = projectOrHome(vm, nav, id) ?: return
    KeepFresh(vm, p)
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Header("${p.name} builds", nav)
        BuildsBody(vm, nav, p)
    }
}

@Composable fun ReleasesScreen(vm: HubVM, nav: NavHostController, id: Long) {
    val p = projectOrHome(vm, nav, id) ?: return
    KeepFresh(vm, p)
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Header("${p.name} releases", nav)
        ReleasesBody(vm, nav, p)
    }
}

@Composable fun ProjectSettingsScreen(vm: HubVM, nav: NavHostController, id: Long) {
    val p = projectOrHome(vm, nav, id) ?: return
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Header("${p.name} settings", nav)
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { AppSettings(vm, nav, p) }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable private fun ProjectHeader(vm: HubVM, nav: NavHostController, p: Project) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val st = vm.stage(p)
    val link = vm.siteLink(p).ifBlank { "https://github.com/${p.repo}" }
    Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton({ nav.popBackStack() }, Modifier.size(40.dp)) { Icon(Icons.Default.ArrowBack, null) }
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Green, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(p.repo, fontSize = 11.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Pill(st.label, st.color())
        IconButton({ vm.togglePin(p) }, Modifier.size(40.dp)) {
            Icon(if (vm.isPinned(p.id)) Icons.Default.Star else Icons.Default.StarBorder, null, tint = if (vm.isPinned(p.id)) Amber else Muted)
        }
        Box {
            IconButton({ menu = true }, Modifier.size(40.dp)) { Icon(Icons.Default.MoreVert, null, tint = Muted) }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text("Open on GitHub") }, { menu = false; openUrl(ctx, "https://github.com/${p.repo}") })
                DropdownMenuItem({ Text("Copy link") }, { menu = false; vm.copy(link) })
                DropdownMenuItem({ Text("Share") }, {
                    menu = false
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "${p.name}: $link"), null)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                })
            }
        }
    }
}

/** One row of the project's section list: icon, title, one line of state, optional badge, chevron. */
@Composable fun NavRow(icon: ImageVector, tint: Color, title: String, sub: String, badge: String? = null, badgeColor: Color = Red, onClick: () -> Unit) =
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(Line, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(18.dp), tint = tint) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(sub, fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (badge != null) { Pill(badge, badgeColor); Spacer(Modifier.width(6.dp)) }
        Icon(Icons.Default.ChevronRight, null, tint = Muted)
    }

@Composable fun RowLine() = Box(Modifier.fillMaxWidth().height(1.dp).background(Line))

/** One build as a single line (Overview). */
@Composable private fun BuildLine(r: GitHub.Run, onClick: () -> Unit) {
    val st = statusWord(r.ok)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(okColor(r.ok), CircleShape))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("#${r.number}  ${r.msg.take(40)}", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Mono(ago(r.time), color = Muted)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(st, color = statusColor(st), fontSize = 12.sp)
            if (r.sec > 0) Mono(fmtDur(r.sec), color = Muted)
        }
    }
}

@Composable private fun HomeBody(vm: HubVM, nav: NavHostController, p: Project) {
    val ctx = LocalContext.current
    val id = p.id
    val runs = vm.runs[id] ?: emptyList()
    val err = vm.errors[id] ?: ""
    val upd = vm.hasUpdate(p)
    val pend = vm.pending(p)
    val dl = dlPct(vm, p)
    val st = vm.stage(p)
    val last = runs.firstOrNull()
    val isApp = p.kind == ProjectType.APP
    val onBuilds = { nav.navigate("builds/$id") }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Panel {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(p.version, fontSize = 34.sp, color = Cyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f).padding(bottom = 6.dp)) {
                        Text(p.stack, fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(p.kind.label, fontSize = 12.sp, color = Muted)
                    }
                }
                Spacer(Modifier.height(8.dp))
                val (instText, instColor) = installedLine(vm, p)
                Mono("status   : ${st.label.lowercase()}", color = st.color())
                if (isApp) Mono("installed: $instText", color = instColor) else Mono("live     : ${vm.siteLink(p).ifBlank { "-" }}")
                last?.let { Mono("last     : #${it.number} ${statusWord(it.ok)} • ${ago(it.time)}" + if (it.sec > 0) " • ${fmtDur(it.sec)}" else "", color = Muted) }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ goDeploy(vm, nav, p) }, Modifier.weight(1f), contentPadding = PadBtn,
                        colors = ButtonDefaults.buttonColors(containerColor = if (pend) Amber else Green, contentColor = Color.Black)) {
                        Icon(if (pend) Icons.Default.AddCircle else Icons.Default.CloudUpload, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                        BtnText(if (pend) "Push ${vm.waitLabel(p)}" else "Deploy")
                    }
                    if (isApp) OutlinedButton({ nav.navigate("install/$id") }, Modifier.weight(1f), contentPadding = PadBtn,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (upd || dl != null) Amber else Cyan)) {
                        if (dl != null) { CircularProgressIndicator({ dl / 100f }, Modifier.size(14.dp), color = Amber, strokeWidth = 2.dp); Spacer(Modifier.width(6.dp)) }
                        BtnText(if (dl != null) dlText(dl) else installLabel(vm, p, upd))
                    }
                    else OutlinedButton({ vm.siteLink(p).let { if (it.isBlank()) vm.toast("Set the live URL in Settings") else openUrl(ctx, it) } }, Modifier.weight(1f), contentPadding = PadBtn,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan)) {
                        Icon(Icons.Default.OpenInNew, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                        BtnText(if (p.kind == ProjectType.WEBSITE) "Open site" else "Open server")
                    }
                }
            }
        }
        // v71: no separate "Update ready" card; the Update button above and the Releases row are enough
        if (err.isNotBlank()) item {
            Panel(border = Red) {
                Text("Build failed", color = Red, fontWeight = FontWeight.Bold)
                Mono(err.take(400))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ vm.copy(err) }, Modifier.weight(1f), contentPadding = PadBtn) { BtnText("Copy log") }
                    OutlinedButton({ vm.selId = p.id; vm.task = "Fix error"; nav.navigate("aprompt/${p.id}") }, Modifier.weight(1f), contentPadding = PadBtn) { BtnText("AI prompt") }
                    last?.let { r -> if (r.ok == false) OutlinedButton({ vm.rerun(p, r.id) }, Modifier.weight(1f), contentPadding = PadBtn) { BtnText("Re-run") } }
                }
            }
        }
        item {
            val files = vm.repoFiles[id]
            val rl = vm.releases[id] ?: emptyList()
            Panel {
                NavRow(Icons.Default.Build, Cyan, "Builds",
                    if (last == null) "No builds yet" else "#${last.number} ${statusWord(last.ok)} • ${ago(last.time)}" + if (last.sec > 0) " • ${fmtDur(last.sec)}" else "",
                    if (last?.ok == false) "Failed" else null) { onBuilds() }
                RowLine()
                val wn = vm.whatsNewLog(p).firstOrNull()
                NavRow(Icons.Default.NewReleases, Green, "What's new",
                    wn?.let { "${it.ver}: ${it.text.lineSequence().firstOrNull().orEmpty().removePrefix("- ").take(50)}" } ?: "Logged from whatsnew.txt in your zip") { nav.navigate("whatsnew/$id") }
                if (isApp) {
                    RowLine()
                    NavRow(Icons.Default.Download, Cyan, "Releases", rl.firstOrNull()?.let { "${it.tag} • ${ago(it.time)}" } ?: "None yet",
                        null) { nav.navigate("releases/$id") }
                }
                RowLine()
                NavRow(Icons.Default.Folder, Amber, "Files", files?.let { "${it.size} files • ${fmtBytes(it.sumOf { f -> f.size })}" } ?: "Browse the repo") { nav.navigate("files/$id") }
                RowLine()
                NavRow(Icons.Default.Info, Cyan, "Info", "Status, versions, addresses") { nav.navigate("info/$id") }
                RowLine()
                NavRow(Icons.Default.AutoAwesome, Cyan, "Prompt", "Fix, add, improve, adopt") { vm.selId = id; vm.task = if (err.isNotBlank()) "Fix error" else "Add feature"; nav.navigate("aprompt/$id") }
                RowLine()
                NavRow(Icons.Default.Tune, Muted, "Settings", "Repo, hosting, signing, alerts") { nav.navigate("psettings/$id") }
            }
        }
        if (runs.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Latest builds", Modifier.weight(1f), color = Muted, fontSize = 12.sp)
                    TextButton({ onBuilds() }) { Text("See all", fontSize = 12.sp, color = Cyan) }
                }
            }
            items(runs.take(3), key = { it.id }) { r -> BuildLine(r) { onBuilds() } }
        }
        val cs = vm.commits[id] ?: emptyList()
        if (cs.isNotEmpty()) {
            item { Text("Recent commits", color = Muted, fontSize = 12.sp) }
            items(cs.take(5)) { c ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Mono(c.sha, color = Cyan); Spacer(Modifier.width(8.dp))
                    Column { Text(c.msg.lineSequence().first().take(70), fontSize = 13.sp); Mono("${c.author} • ${ago(c.time)}", color = Muted) }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable private fun BuildsBody(vm: HubVM, nav: NavHostController, p: Project) {
    val id = p.id
    val runs = vm.runs[id] ?: emptyList()
    val all = (runs + (vm.olderRuns[id] ?: emptyList())).distinctBy { it.id }   // the whole history, newest first
    var filter by rememberSaveable(id) { mutableStateOf("all") }
    var openRun by remember { mutableStateOf<Long?>(null) }
    val shown = when (filter) { "passed" -> all.filter { it.ok == true }; "failed" -> all.filter { it.ok == false }; else -> all }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("all" to "All", "passed" to "Passed", "failed" to "Failed").forEach { (k, l) ->
                    FilterChip(filter == k, { filter = k }, { Text(l, fontSize = 12.sp, maxLines = 1, softWrap = false) })
                }
                Spacer(Modifier.weight(1f))
                if (all.isNotEmpty()) TextButton({ vm.copy(vm.historyText(p, all)) }) { Text("Copy", fontSize = 12.sp, color = Cyan) }
            }
        }
        items(shown, key = { it.id }) { r ->
            val st = statusWord(r.ok)
            val open = openRun == r.id
            Panel(Modifier.clickable { if (open) openRun = null else { openRun = r.id; vm.loadJobs(p, r.id) } }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(okColor(r.ok), CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("#${r.number}  ${r.msg.take(40)}", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Mono(ago(r.time) + if (r.event.isNotBlank()) " • ${r.event}" else "", color = Muted)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(st, color = statusColor(st), fontSize = 12.sp)
                        if (r.sec > 0) Mono(fmtDur(r.sec), color = Muted)
                    }
                }
                if (open) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (r.ok == null) OutlinedButton({ vm.cancelRun(p, r.id) }, Modifier.weight(1f), contentPadding = PadBtn) { BtnText("Cancel build") }
                        else OutlinedButton({ vm.rerun(p, r.id) }, Modifier.weight(1f), contentPadding = PadBtn) { BtnText("Re-run") }
                    }
                    val js = vm.jobs[r.id]
                    if (js == null) Mono("loading steps…", Modifier.padding(top = 6.dp), Muted) else js.forEach { j ->
                        Mono("${mark(j.ok)} ${j.name}", Modifier.padding(top = 6.dp), okColor(j.ok))
                        j.steps.forEach { s -> Mono("   ${mark(s.ok)} ${s.name}", color = if (s.ok == false) Red else Muted) }
                        TextButton({ vm.openLog(p, j); nav.navigate("log") }) { Text("View full log", fontSize = 12.sp, color = Cyan) }
                    }
                }
            }
        }
        if (filter == "all" && (runs.size >= 15 || vm.olderRuns[id] != null) && vm.olderMore[id] != false) item {
            OutlinedButton({ vm.loadOlder(p) }, Modifier.fillMaxWidth()) { BtnText(if (vm.olderLoading[id] == true) "Loading…" else "Load older builds") }
        }
        if (all.isEmpty()) item {
            EmptyState(Icons.Default.Build, "No builds yet", "Deploy a zip and the first build starts on GitHub.") {
                Button({ goDeploy(vm, nav, p) }, colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black)) { Text("Deploy a zip") }
            }
        } else if (shown.isEmpty()) item { Text("No ${filter} builds loaded.", color = Muted, fontSize = 12.sp) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable private fun ReleasesBody(vm: HubVM, nav: NavHostController, p: Project) {
    val ctx = LocalContext.current
    val id = p.id
    val rl = vm.releases[id] ?: emptyList()
    val upd = vm.hasUpdate(p)
    var sel by remember { mutableStateOf<GitHub.Release?>(null) }
    sel?.let { r -> OldReleaseDialog(vm, p, r) { sel = null } }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(rl) { i, r ->
            val saved = i > 0 && vm.hasOld(p, r.tag)
            Panel(Modifier.clickable { if (i == 0) nav.navigate("install/$id") else if (r.sizeKb > 0) sel = r else vm.toast("No APK on this release") },
                border = if (i == 0 && upd) Amber else Line) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.tag, Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Cyan)
                    if (i == 0) { if (!upd) Pill("Latest", Green) }
                    else if (saved) Pill("Saved", Green)
                    else if (r.sizeKb > 0) Icon(Icons.Default.Download, null, Modifier.size(18.dp), tint = Muted)
                    Spacer(Modifier.width(8.dp))
                    Text(ago(r.time), fontSize = 12.sp, color = Muted)
                }
                Mono(vm.notesFor(p, r.tag, r.notes).take(300))
                if (r.sizeKb > 0) Mono("APK ${"%.1f".format(r.sizeKb / 1024.0)} MB" + if (i > 0) " • tap to download" else "", color = Muted)
                if (i == 0) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton({ nav.navigate("install/$id") }, Modifier.fillMaxWidth(), contentPadding = PadBtn,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (upd) Amber else Cyan)) { BtnText(installLabel(vm, p, upd)) }
                }
            }
        }
        if (rl.isEmpty()) item { EmptyState(Icons.Default.Download, "No releases yet", "A release with the APK appears here once a build passes.") }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

/** v69: an older release: download it, install it, share the file, or delete the saved copy. */
@Composable private fun OldReleaseDialog(vm: HubVM, p: Project, r: GitHub.Release, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val key = "${p.id}:${r.tag}"
    val prog = vm.oldDl[key]
    val err = vm.oldMsg[key]
    val saved = vm.hasOld(p, r.tag)
    val onPhone = vm.installedTag(p.id) == r.tag
    AlertDialog(onDismissRequest = onClose,
        title = { Text("${p.name} ${r.tag}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Mono("${ago(r.time)} • ${"%.1f".format(r.sizeKb / 1024.0)} MB" + if (onPhone) " • on this phone" else "", color = Muted)
                Mono(vm.notesFor(p, r.tag, r.notes).take(400))
                if (prog != null) {
                    LinearProgressIndicator({ prog }, Modifier.fillMaxWidth(), color = Green)
                    Mono("Downloading ${(prog * 100).toInt()}%", color = Cyan)
                }
                if (err != null && prog == null) Text(err, color = Red, fontSize = 12.sp)
                if (saved && prog == null) Text("Saved on this phone.", color = Green, fontSize = 12.sp)
                if (vm.isInstalled(p) == true && !onPhone) Text("Android will not install an older build over a newer one. Uninstall the app first (its data is lost), or just keep the file.", fontSize = 11.sp, color = Muted)
                if (saved && prog == null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ vm.shareOld(ctx, p, r.tag) }, Modifier.weight(1f), contentPadding = PadBtn) { BtnText("Share file") }
                    OutlinedButton({ vm.deleteOld(p, r.tag) }, Modifier.weight(1f), contentPadding = PadBtn) { BtnText("Delete") }
                }
            }
        },
        confirmButton = {
            TextButton({ if (saved) vm.installOld(ctx, p, r.tag) else vm.downloadOld(ctx, p, r.tag, true) }, enabled = prog == null) {
                Text(if (prog != null) "Downloading…" else if (saved) "Install" else "Download and install", color = Green)
            }
        },
        dismissButton = {
            Row {
                if (!saved && prog == null) TextButton({ vm.downloadOld(ctx, p, r.tag, false) }) { Text("Download only") }
                TextButton(onClose) { Text("Close") }
            }
        })
}

/** v68: every what's new text App Hub read from a zip (whatsnew.txt), newest first. For apps it shows whether it is also on the GitHub release. */
@Composable fun WhatsNewScreen(vm: HubVM, nav: NavHostController, id: Long) {
    val p = projectOrHome(vm, nav, id) ?: return
    KeepFresh(vm, p)
    val log = vm.whatsNewLog(p)
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Header("${p.name} what's new", nav)
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(log, key = { it.ver }) { w ->
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(w.ver, Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Cyan)
                        if (p.kind == ProjectType.APP) Pill(if (w.written) "On release" else "Waiting", if (w.written) Green else Amber)
                        Spacer(Modifier.width(8.dp))
                        Text(agoMs(w.time), fontSize = 12.sp, color = Muted)
                    }
                    Spacer(Modifier.height(4.dp))
                    Mono(w.text)
                    TextButton({ vm.copy(w.text) }) { Text("Copy", fontSize = 12.sp, color = Cyan) }
                }
            }
            if (log.isEmpty()) item {
                EmptyState(Icons.Default.NewReleases, "Nothing logged yet",
                    "Ask for the zip with the app prompt (zip and version rules on). It carries whatsnew.txt. App Hub reads it, keeps it here, and never pushes it.") {
                    Button({ vm.selId = id; nav.navigate("aprompt/$id") }, colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black)) { Text("Open prompt") }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
