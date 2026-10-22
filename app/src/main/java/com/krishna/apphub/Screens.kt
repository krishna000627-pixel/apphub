@file:OptIn(ExperimentalLayoutApi::class)
package com.krishna.apphub

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import java.io.File

// ---------- shared widgets ----------
@Composable fun Mono(t: String, mod: Modifier = Modifier, color: Color = Color.Unspecified) =
    Text(t, mod, color, fontFamily = FontFamily.Monospace, fontSize = 12.sp)

@Composable fun Pill(t: String, c: Color) =
    Box(Modifier.border(1.dp, c, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp)) { Text(t, color = c, fontSize = 11.sp) }

@Composable fun Panel(mod: Modifier = Modifier, border: Color = Line, content: @Composable ColumnScope.() -> Unit) =
    Column(mod.fillMaxWidth().background(Surf, RoundedCornerShape(12.dp)).border(1.dp, border, RoundedCornerShape(12.dp)).padding(12.dp), content = content)

@Composable fun BigButton(t: String, icon: ImageVector, onClick: () -> Unit) =
    Button(onClick, Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black)) {
        Icon(icon, null); Spacer(Modifier.width(8.dp)); Text(t, fontWeight = FontWeight.Bold)
    }

@Composable fun Stat(label: String, value: Int, c: Color, mod: Modifier) =
    Column(mod.background(Surf, RoundedCornerShape(10.dp)).border(1.dp, Line, RoundedCornerShape(10.dp)).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", color = c, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(label, fontSize = 10.sp, color = Muted)
    }

/** Compact screen header: small vertical padding, optional back arrow, optional trailing actions. */
@Composable fun Header(t: String, nav: NavHostController? = null, actions: @Composable RowScope.() -> Unit = {}) =
    Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (nav != null) IconButton({ nav.popBackStack() }, Modifier.size(40.dp)) { Icon(Icons.Default.ArrowBack, null) }
        Text(t, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Green, maxLines = 1)
        actions()
    }

@Composable fun ProjectPicker(ps: List<Project>, sel: Project?, onSel: (Project) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ open = true }, Modifier.fillMaxWidth()) {
            Text(sel?.name ?: "Select project", Modifier.weight(1f)); Text(sel?.version ?: ""); Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(open, { open = false }) {
            ps.forEach { DropdownMenuItem({ Text("${it.name}  ${it.version}") }, { onSel(it); open = false }) }
        }
    }
}

fun statusColor(s: String) = when (s) { "passed" -> Green; "failed" -> Red; "running" -> Amber; else -> Color.Gray }
fun okColor(ok: Boolean?) = when (ok) { true -> Green; false -> Red; else -> Amber }
fun mark(ok: Boolean?) = when (ok) { true -> "✓"; false -> "✕"; else -> "…" }

fun ago(t: String): String = runCatching {
    val then = java.time.LocalDateTime.parse(t.replace(" ", "T")).toEpochSecond(java.time.ZoneOffset.UTC)
    val d = System.currentTimeMillis() / 1000 - then
    when { d < 60 -> "just now"; d < 3600 -> "${d / 60}m ago"; d < 86400 -> "${d / 3600}h ago"; else -> "${d / 86400}d ago" }
}.getOrDefault(t)

fun openUrl(ctx: android.content.Context, url: String) = ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

fun goDeploy(vm: HubVM, nav: NavHostController, p: Project) {
    vm.selId = p.id
    nav.navigate("deploy") { popUpTo("dash"); launchSingleTop = true }
}

// ---------- app shell ----------
@Composable fun App(vm: HubVM) {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val tabs = listOf(
        Triple("dash", "Dashboard", Icons.Default.Dashboard), Triple("prompt", "Prompt", Icons.Default.Description),
        Triple("deploy", "Deploy", Icons.Default.CloudUpload), Triple("settings", "Settings", Icons.Default.Settings)
    )
    // contentWindowInsets = 0 so the status bar inset is applied exactly once (statusBarsPadding below)
    Scaffold(containerColor = Bg, contentWindowInsets = WindowInsets(0, 0, 0, 0), bottomBar = {
        NavigationBar(containerColor = Surf) {
            tabs.forEach { (r, l, i) ->
                NavigationBarItem(selected = route == r, onClick = { nav.navigate(r) { popUpTo("dash"); launchSingleTop = true } },
                    icon = { Icon(i, null) }, label = { Text(l, fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Green, selectedTextColor = Green, indicatorColor = Line))
            }
        }
    }) { pad ->
        NavHost(nav, "dash", Modifier.padding(pad).statusBarsPadding()) {
            composable("dash") { Dashboard(vm, nav) }
            composable("prompt") { PromptScreen(vm, nav) }
            composable("log") { LogScreen(vm, nav) }
            composable("deploy") { DeployScreen(vm, nav) }
            composable("settings") { SettingsScreen(vm) }
            composable("add") { AddScreen(vm, nav) }
            composable("detail/{id}", listOf(navArgument("id") { type = NavType.LongType })) { Detail(vm, nav, it.arguments!!.getLong("id")) }
            composable("install/{id}", listOf(navArgument("id") { type = NavType.LongType })) { InstallScreen(vm, nav, it.arguments!!.getLong("id")) }
        }
    }
}

// ---------- dashboard ----------
@Composable fun Dashboard(vm: HubVM, nav: NavHostController) {
    val ps by vm.projects.collectAsState()
    var f by remember { mutableStateOf("All") }
    var q by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.refreshAll() }
    fun hit(p: Project, k: String) = when (k) {
        "Pinned" -> vm.isPinned(p.id); "To push" -> vm.pending(p); "Errors" -> p.lastStatus == "failed"; "Updates" -> vm.hasUpdate(p); else -> true
    }
    val shown = ps.filter { hit(it, f) && (q.isBlank() || it.name.contains(q, true) || it.repo.contains(q, true)) }
        .sortedWith(compareByDescending<Project> { vm.isPinned(it.id) }.thenBy { it.name.lowercase() })
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Header("App Hub") {
            IconButton({ vm.refreshAll() }, Modifier.size(40.dp)) {
                if (vm.refreshing) CircularProgressIndicator(Modifier.size(18.dp), color = Green, strokeWidth = 2.dp) else Icon(Icons.Default.Refresh, null)
            }
            IconButton({ nav.navigate("add") }, Modifier.size(40.dp)) { Icon(Icons.Default.Add, null, tint = Green) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("Apps", ps.size, Cyan, Modifier.weight(1f))
            Stat("Passing", ps.count { it.lastStatus == "passed" }, Green, Modifier.weight(1f))
            Stat("Failing", ps.count { it.lastStatus == "failed" }, Red, Modifier.weight(1f))
            Stat("To push", ps.count { vm.pending(it) }, Amber, Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Pinned", "To push", "Errors", "Updates").forEach { k ->
                FilterChip(f == k, { f = k }, { Text("$k (${ps.count { hit(it, k) }})", fontSize = 12.sp) })
            }
        }
        if (ps.size > 5) OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(top = 6.dp), singleLine = true,
            placeholder = { Text("Search apps") }, leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { if (q.isNotEmpty()) IconButton({ q = "" }) { Icon(Icons.Default.Close, null) } })
        if (ps.isEmpty()) Panel(Modifier.padding(top = 12.dp)) {
            Text("No apps yet", fontWeight = FontWeight.Bold)
            Text("Add your own, or bring back the preset apps.", fontSize = 12.sp, color = Muted)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ vm.restorePresets() }, colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black)) { Text("Add presets") }
                OutlinedButton({ nav.navigate("add") }) { Text("Add app") }
            }
        } else if (shown.isEmpty()) Text("Nothing matches this filter.", Modifier.padding(24.dp), color = Muted)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp)) {
            items(shown, key = { it.id }) { p -> ProjectCard(vm, nav, p) }
        }
    }
}

@Composable fun ProjectCard(vm: HubVM, nav: NavHostController, p: Project) {
    val last = vm.runs[p.id]?.firstOrNull()
    val pinned = vm.isPinned(p.id)
    val upd = vm.hasUpdate(p)
    val pend = vm.pending(p)
    Panel(Modifier.clickable { nav.navigate("detail/${p.id}") }, border = if (p.lastStatus == "failed") Red.copy(alpha = 0.6f) else Line) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(Line, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Apps, null, tint = Cyan) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, fontWeight = FontWeight.Bold)
                Text(listOfNotNull(p.version, last?.let { ago(it.time) }).joinToString(" • "), fontSize = 12.sp, color = Muted)
            }
            if (pend) IconButton({ goDeploy(vm, nav, p) }, Modifier.size(36.dp)) { Icon(Icons.Default.AddCircle, null, tint = Amber) }
            IconButton({ vm.togglePin(p) }, Modifier.size(36.dp)) {
                Icon(if (pinned) Icons.Default.Star else Icons.Default.StarBorder, null, tint = if (pinned) Amber else Muted)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (p.lastStatus != "none") Pill(if (p.lastStatus == "passed") "✓ Build passed" else if (p.lastStatus == "failed") "✕ Build failed" else "… Running", statusColor(p.lastStatus))
            else Pill("No builds yet", Muted)
            if (pend) Pill("+ Push ${p.version}", Amber)
            if (upd) Pill("Update ready", Amber)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ vm.build(p) }, Modifier.weight(1f)) { Icon(Icons.Default.Build, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Build") }
            if (upd) Button({ nav.navigate("install/${p.id}") }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black)) {
                Icon(Icons.Default.Download, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Update")
            }
            Button({ goDeploy(vm, nav, p) }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black)) {
                Icon(Icons.Default.CloudUpload, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Deploy")
            }
        }
    }
}

// ---------- project detail (everything fetched in-app) ----------
@Composable fun Detail(vm: HubVM, nav: NavHostController, id: Long) {
    val p = vm.projects.collectAsState().value.firstOrNull { it.id == id } ?: return
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var ver by remember(p.id) { mutableStateOf(p.version) }
    var confirm by remember { mutableStateOf(false) }
    var openRun by remember { mutableStateOf<Long?>(null) }
    val runs = vm.runs[id] ?: emptyList()
    val err = vm.errors[id] ?: ""
    val upd = vm.hasUpdate(p)
    val pend = vm.pending(p)
    LaunchedEffect(id) { vm.refresh(p); vm.loadDetails(p) }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        confirmButton = { TextButton({ vm.delete(p); nav.popBackStack() }) { Text("Remove", color = Red) } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } },
        title = { Text("Remove ${p.name}?") }, text = { Text("This only removes it from App Hub. Your repo and files stay untouched.") })
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Header(p.name, nav) {
                IconButton({ openUrl(ctx, "https://github.com/${p.repo}") }, Modifier.size(40.dp)) { Icon(Icons.Default.OpenInNew, null, tint = Muted) }
                IconButton({ vm.togglePin(p) }, Modifier.size(40.dp)) {
                    Icon(if (vm.isPinned(id)) Icons.Default.Star else Icons.Default.StarBorder, null, tint = if (vm.isPinned(id)) Amber else Muted)
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(p.version, fontSize = 36.sp, color = Cyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.padding(bottom = 6.dp)) { Text(p.stack, fontSize = 12.sp, color = Muted); Text(p.repo, fontSize = 12.sp, color = Muted) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ goDeploy(vm, nav, p) }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (pend) Amber else Green, contentColor = Color.Black)) {
                    Icon(if (pend) Icons.Default.AddCircle else Icons.Default.CloudUpload, null, Modifier.size(16.dp)); Text(if (pend) " Push ${p.version}" else " Deploy zip", fontSize = 13.sp)
                }
                OutlinedButton({ nav.navigate("install/$id") }, Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = if (upd) Amber else Cyan)) {
                    Text(if (upd) "Update ready" else "Install / Update", fontSize = 13.sp)
                }
            }
        }
        item {
            TabRow(tab, containerColor = Bg, contentColor = Green) {
                listOf("Overview", "Builds", "Releases", "Settings").forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t, fontSize = 11.sp, maxLines = 1) }) }
            }
        }
        when (tab) {
            0 -> {
                item {
                    Panel {
                        val i = vm.infos[id]
                        if (i == null) Text("Loading repo info…", color = Muted) else {
                            if (i.desc.isNotBlank()) { Text(i.desc, fontSize = 13.sp); Spacer(Modifier.height(6.dp)) }
                            Mono("language : ${i.lang.ifBlank { "-" }}"); Mono("branch   : ${i.branch}")
                            Mono("size     : ${"%.1f".format(i.sizeKb / 1024.0)} MB"); Mono("issues   : ${i.issues}   stars: ${i.stars}")
                            Mono("access   : ${if (i.priv) "private" else "public"}"); Mono("pushed   : ${ago(i.pushed)}")
                        }
                        Spacer(Modifier.height(6.dp))
                        Mono("status   : ${p.lastStatus}", color = statusColor(p.lastStatus))
                        Mono("installed: ${vm.installedTag(id).ifBlank { "-" }}"); Mono("latest   : ${vm.latest[id] ?: "-"}")
                    }
                }
                item { Text("Recent commits", color = Muted, fontSize = 12.sp) }
                items(vm.commits[id] ?: emptyList()) { c ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Mono(c.sha, color = Cyan); Spacer(Modifier.width(8.dp))
                        Column { Text(c.msg.lineSequence().first().take(70), fontSize = 13.sp); Mono("${c.author} • ${ago(c.time)}", color = Muted) }
                    }
                }
            }
            1 -> {
                items(runs) { r ->
                    val st = when (r.ok) { true -> "passed"; false -> "failed"; else -> "running" }
                    Column(Modifier.fillMaxWidth().clickable {
                        if (openRun == r.id) openRun = null else { openRun = r.id; vm.loadJobs(p, r.id) }
                    }.padding(vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(okColor(r.ok), CircleShape))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) { Mono("#${r.number}  ${r.msg.take(34)}"); Mono(ago(r.time), color = Color.Gray) }
                            Text(st, color = statusColor(st), fontSize = 12.sp)
                        }
                        if (openRun == r.id) {
                            val js = vm.jobs[r.id]
                            if (js == null) Mono("  loading steps…", color = Muted) else js.forEach { j ->
                                Mono("  ${mark(j.ok)} ${j.name}", color = okColor(j.ok))
                                j.steps.forEach { s -> Mono("     ${mark(s.ok)} ${s.name}", color = if (s.ok == false) Red else Muted) }
                                TextButton({ vm.openLog(p, j); nav.navigate("log") }) { Text("View full log", fontSize = 12.sp, color = Cyan) }
                            }
                        }
                    }
                }
                if (runs.isEmpty()) item { Text("No builds yet. Deploy a zip to trigger the first one.", color = Color.Gray) }
                if (err.isNotBlank()) item {
                    Panel(border = Red) {
                        Text("Build Error", color = Red, fontWeight = FontWeight.Bold); Mono(err)
                        Text("Tap the build above, then View full log.", fontSize = 11.sp, color = Muted)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ vm.copy(err) }, Modifier.weight(1f)) { Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)); Text(" Copy log", fontSize = 12.sp) }
                            OutlinedButton({ vm.selId = p.id; vm.task = "Fix error"; nav.navigate("prompt") }, Modifier.weight(1f)) { Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp), tint = Cyan); Text(" AI prompt", fontSize = 12.sp) }
                        }
                    }
                }
            }
            2 -> {
                val rl = vm.releases[id] ?: emptyList()
                items(rl) { r ->
                    Panel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.tag, Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Cyan)
                            Text(ago(r.time), fontSize = 12.sp, color = Muted)
                        }
                        if (r.notes.isNotBlank()) Mono(r.notes.take(300))
                        if (r.sizeKb > 0) Mono("APK ${"%.1f".format(r.sizeKb / 1024.0)} MB", color = Muted)
                    }
                }
                if (rl.isEmpty()) item { Text("No releases yet.", color = Color.Gray) }
            }
            else -> item {
                Panel {
                    OutlinedTextField(ver, { ver = it }, label = { Text("Version") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ vm.update(p.copy(version = ver)); vm.toast("Saved") }) { Text("Save version") }
                        OutlinedButton({ ver = Commands.bump(ver) }) { Text("Bump → ${Commands.bump(ver)}") }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton({ vm.keyBackup(p).let { k -> if (k.isBlank()) vm.toast("No keystore stored") else vm.copy(k) } }) { Text("Copy keystore backup") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton({ confirm = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Red)) { Text("Remove from App Hub") }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ---------- deploy (replaces the Termux command page) ----------
@Composable fun DeployScreen(vm: HubVM, nav: NavHostController) {
    val ps by vm.projects.collectAsState()
    val p = vm.sel()
    val ctx = LocalContext.current
    var note by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.pickZip(ctx, uri) }
    val pack = vm.zipPack
    val last = p?.let { vm.runs[it.id]?.firstOrNull() }
    LaunchedEffect(p?.id) { p?.let { vm.refresh(it) } }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Header("Deploy")
        Text("Pick your zip and App Hub pushes it to GitHub and builds it. Nothing else needed.", fontSize = 12.sp, color = Muted)
        ProjectPicker(ps, p) { vm.selId = it.id }
        Panel {
            if (pack == null) {
                Text("Choose your Name_vN.zip", fontWeight = FontWeight.Bold)
                Text("Pick it from Downloads. Build folders and keystores inside the zip are skipped.", fontSize = 12.sp, color = Muted)
            } else {
                Text(pack.name, fontWeight = FontWeight.Bold, color = Cyan)
                Mono("version : ${pack.version.ifBlank { "-" }}")
                Mono("files   : ${pack.entries.size}  (skipped ${pack.skipped})")
                Mono("size    : ${"%.1f".format(pack.bytes / 1048576.0)} MB")
                if (p != null && !pack.name.startsWith(Commands.fileBase(p), true)) Text("⚠ This zip name doesn't match ${p.name}", color = Amber, fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton({ pick.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }) {
                Icon(Icons.Default.FolderOpen, null, Modifier.size(16.dp)); Text(if (pack == null) " Choose zip" else " Change zip")
            }
        }
        OutlinedTextField(note, { note = it }, label = { Text("Release note (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (pack != null && p != null) Mono("commit : ${pack.version.ifBlank { Commands.bump(p.version) }}: ${note.ifBlank { "update from App Hub" }}", color = Muted)
        BigButton(if (vm.deploying) "Working…" else "Push + build", Icons.Default.CloudUpload) {
            if (pack != null && p != null) vm.deploy(ctx, p, pack, note) else vm.toast("Pick a project and a zip first")
        }
        if (vm.deployLog.isNotEmpty()) Panel { vm.deployLog.forEach { Mono(it) } }
        if (p != null && last != null) Panel(border = okColor(last.ok)) {
            Text("Latest build #${last.number}", fontWeight = FontWeight.Bold)
            Mono("${mark(last.ok)} ${when (last.ok) { true -> "passed"; false -> "failed"; else -> "running…" }}  ${ago(last.time)}", color = okColor(last.ok))
            if (last.ok == true) {
                Spacer(Modifier.height(8.dp))
                Button({ nav.navigate("install/${p.id}") }, colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Color.Black)) {
                    Icon(Icons.Default.Download, null, Modifier.size(16.dp)); Text(" Install this build")
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

// ---------- prompt maker ----------
@Composable fun PromptScreen(vm: HubVM, nav: NavHostController) {
    val ps by vm.projects.collectAsState()
    val p = vm.sel()
    var stack by remember(p?.id) { mutableStateOf(p?.stack ?: "") }
    var deliver by remember { mutableStateOf(true) }
    val isNew = vm.task == "New app"
    val text = if (p == null || isNew) "" else Prompts.make(vm.task, p, stack, vm.errors[p.id] ?: p.lastError, deliver)
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Header("Prompt Maker")
        if (!isNew) { Text("1. Select Project", color = Color.Gray); ProjectPicker(ps, p) { vm.selId = it.id } }
        Text(if (isNew) "What do you want to do?" else "2. What do you want to do?", color = Color.Gray)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Prompts.tasks.forEach { FilterChip(vm.task == it, { vm.task = it }, { Text(it) }) } }
        if (isNew) NewAppSection(vm, nav) else {
            Text("3. Tech Stack", color = Color.Gray)
            OutlinedTextField(stack, { stack = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(deliver, { deliver = it }); Text("  Add zip name + version bump rules", fontSize = 12.sp) }
            Text("4. Generated Prompt", color = Color.Gray)
            Panel { Mono(text.ifBlank { "Add a project first." }) }
            BigButton("Copy prompt", Icons.Default.ContentCopy) { if (text.isNotBlank()) vm.copy(text) }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** New app: ideas first, then the build prompt. */
@Composable fun NewAppSection(vm: HubVM, nav: NavHostController) {
    val ideas = Ideas.list[vm.ideaCat] ?: emptyList()
    Text("1. Pick a direction", color = Color.Gray)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Ideas.cats.forEach { c -> FilterChip(vm.ideaCat == c, { vm.ideaCat = c }, { Text(c) }) } }
    Text("2. Ideas first. Tap one, or ask the AI for more.", color = Color.Gray)
    ideas.forEach { (n, d) ->
        val on = vm.ideaName == n
        Panel(Modifier.clickable { vm.ideaName = n; vm.ideaPitch = d }, border = if (on) Green else Line) {
            Text(n, fontWeight = FontWeight.Bold, color = if (on) Green else Color.Unspecified)
            Text(d, fontSize = 12.sp, color = Muted)
        }
    }
    OutlinedTextField(vm.ideaNotes, { vm.ideaNotes = it }, label = { Text("Your interests / must-haves (optional)") }, modifier = Modifier.fillMaxWidth())
    OutlinedButton({ vm.copy(Prompts.ideasPrompt(vm.ideaCat, vm.ideaNotes)) }, Modifier.fillMaxWidth()) {
        Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp), tint = Cyan); Text(" Copy ideas prompt (AI suggests, you pick)", fontSize = 12.sp)
    }
    Text("3. Your pick", color = Color.Gray)
    OutlinedTextField(vm.ideaName, { vm.ideaName = it }, label = { Text("App name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(vm.ideaPitch, { vm.ideaPitch = it }, label = { Text("One-line pitch") }, modifier = Modifier.fillMaxWidth())
    Text("4. Build prompt", color = Color.Gray)
    val text = Prompts.buildPrompt(vm.ideaName, vm.ideaPitch)
    Panel { Mono(if (vm.ideaName.isBlank()) "Pick or type an app name first." else text) }
    BigButton("Copy build prompt", Icons.Default.ContentCopy) { if (vm.ideaName.isNotBlank()) vm.copy(text) else vm.toast("Pick an idea first") }
    OutlinedButton({ if (vm.ideaName.isNotBlank()) { vm.draftName = vm.ideaName; nav.navigate("add") } else vm.toast("Pick an idea first") }, Modifier.fillMaxWidth()) {
        Text("Set up this app in App Hub")
    }
}

// ---------- full build log ----------
@Composable fun LogScreen(vm: HubVM, nav: NavHostController) {
    var q by remember { mutableStateOf("") }
    var tail by remember { mutableStateOf(false) }
    var errOnly by remember { mutableStateOf(false) }
    fun isErr(l: String) = l.startsWith("e: ") || l.contains("error", true) || l.contains("FAILED") || l.contains("What went wrong")
    val shown = remember(vm.logLines, q, tail, errOnly) {
        val base = if (tail) vm.logLines.takeLast(200) else vm.logLines
        base.filter { (!errOnly || isErr(it)) && (q.isBlank() || it.contains(q, true)) }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Header(vm.logTitle, nav) {
            IconButton({ vm.copy(shown.joinToString("\n")) }, Modifier.size(40.dp)) { Icon(Icons.Default.ContentCopy, null) }
        }
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search log") },
            leadingIcon = { Icon(Icons.Default.Search, null) })
        Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(tail, { tail = !tail }, { Text("Last 200", fontSize = 12.sp) })
            FilterChip(errOnly, { errOnly = !errOnly }, { Text("Errors only", fontSize = 12.sp) })
            Mono("${shown.size} lines", color = Muted)
        }
        if (vm.logLoading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Green)
        LazyColumn(Modifier.weight(1f)) { items(shown) { l -> Mono(l, color = if (isErr(l)) Red else Color.Unspecified) } }
    }
}

// ---------- install / update ----------
@Composable fun InstallScreen(vm: HubVM, nav: NavHostController, id: Long) {
    val p = vm.projects.collectAsState().value.firstOrNull { it.id == id } ?: return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var rel by remember { mutableStateOf<GitHub.Rel?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var prog by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(id) { val x = vm.gh.latest(p.repo); rel = x; if (x != null) vm.latest[id] = x.tag; loaded = true }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Header(p.name, nav)
        Panel {
            val r = rel
            val inst = vm.installedTag(id)
            when {
                !loaded -> Text("Checking GitHub releases…")
                r == null -> Text("No APK release found yet. Deploy a zip and wait for the build to pass.", color = Amber)
                else -> {
                    Text(if (inst == r.tag) "Up to date" else if (inst.isBlank()) "Latest build available" else "Update available", color = Cyan, fontWeight = FontWeight.Bold)
                    Text("${inst.ifBlank { "not installed here" }} → ${r.tag}", fontSize = 18.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
        if (prog > 0f) { LinearProgressIndicator({ prog }, Modifier.fillMaxWidth(), color = Green); Mono("${(prog * 100).toInt()}%") }
        rel?.let { r ->
            if (r.body.isNotBlank()) Panel { Text("What's new", fontWeight = FontWeight.Bold); Mono(r.body) }
            BigButton(if (vm.installedTag(id) == r.tag) "Reinstall" else "Download & Install", Icons.Default.Download) {
                scope.launch {
                    val f = File(ctx.cacheDir, "apks").apply { mkdirs() }.resolve("${p.name}.apk")
                    val ok = runCatching { vm.gh.download(r.assetUrl, f) { d, t -> if (t > 0) prog = d.toFloat() / t } }.isSuccess
                    if (!ok) { vm.toast("Download failed"); return@launch }
                    vm.markInstalled(id, r.tag); vm.gh.install(ctx, f)
                }
            }
        }
    }
}

// ---------- settings ----------
@Composable fun SettingsScreen(vm: HubVM) {
    var cid by remember { mutableStateOf(vm.prefs.clientId) }
    val ctx = LocalContext.current
    val appVer = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?" }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Header("Settings")
        Panel {
            Text("GitHub", fontWeight = FontWeight.Bold)
            Text(if (vm.login.isBlank()) "Not logged in" else "Logged in as ${vm.login}", color = if (vm.login.isBlank()) Amber else Green)
            vm.userCode?.let { Spacer(Modifier.height(8.dp)); Text("Enter this code on GitHub:"); Text(it, fontSize = 30.sp, color = Cyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(8.dp))
            if (vm.login.isBlank()) BigButton("Login with GitHub", Icons.Default.Login) { vm.startLogin() }
            else OutlinedButton({ vm.logout() }) { Text("Log out") }
        }
        Panel {
            Text("OAuth Client ID", fontWeight = FontWeight.Bold)
            Text("Preset. Change only if you use a different GitHub OAuth App (Device Flow on).", fontSize = 12.sp, color = Color.Gray)
            OutlinedTextField(cid, { cid = it.trim() }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button({ vm.prefs.clientId = cid; vm.toast("Saved") }) { Text("Save") }
        }
        Panel {
            Text("Apps", fontWeight = FontWeight.Bold)
            Text("QuestBlock, BgBrowser and App Hub are added on first launch. Bring back any you removed.", fontSize = 12.sp, color = Muted)
            Spacer(Modifier.height(8.dp))
            OutlinedButton({ vm.restorePresets() }) { Icon(Icons.Default.Restore, null, Modifier.size(16.dp)); Text(" Restore presets", fontSize = 12.sp) }
        }
        Panel {
            Text("How it works", fontWeight = FontWeight.Bold)
            Text("Deploy pushes your zip to GitHub as one commit. GitHub builds and signs the APK. Install downloads it. The version label follows every build automatically, however it was started.", fontSize = 12.sp, color = Muted)
        }
        Text("App Hub $appVer", Modifier.fillMaxWidth().padding(vertical = 8.dp), color = Muted, fontSize = 12.sp)
    }
}

// ---------- add project ----------
@Composable fun AddScreen(vm: HubVM, nav: NavHostController) {
    val slug = vm.draftName.lowercase().filter { it.isLetterOrDigit() }
    var name by remember { mutableStateOf(vm.draftName) }; var repo by remember { mutableStateOf(slug) }
    var pkg by remember { mutableStateOf(if (slug.isBlank()) "" else "com.krishna.$slug") }; var ver by remember { mutableStateOf("v1") }
    LaunchedEffect(Unit) { vm.draftName = "" }
    var create by remember { mutableStateOf(true) }; var keys by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Header("Setup project", nav)
        Text("Quick fill", color = Color.Gray)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Presets.all.forEach { pr ->
                AssistChip({ name = pr.name; repo = pr.repo; pkg = pr.pkg; ver = pr.version; create = false; keys = false }, { Text(pr.name) })
            }
        }
        OutlinedTextField(name, { name = it; if (repo.isBlank() || repo == name.dropLast(1)) repo = it }, label = { Text("App name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(repo, { repo = it }, label = { Text("Repo name (or owner/name)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(pkg, { pkg = it }, label = { Text("Package name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(ver, { ver = it }, label = { Text("Version") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(create, { create = it }); Text("  Create GitHub repo (private)") }
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(keys, { keys = it }); Text("  Generate signing keys + set secrets") }
        BigButton("Create project", Icons.Default.PlayArrow) {
            if (name.isNotBlank() && repo.isNotBlank()) vm.setup(name.trim(), repo.trim(), pkg.trim(), ver.trim(), create, keys) else vm.toast("Name and repo required")
        }
        if (vm.log.isNotEmpty()) {
            Panel { vm.log.forEach { Mono(it) } }
            OutlinedButton({ nav.navigate("deploy") { popUpTo("dash"); launchSingleTop = true } }) { Text("Go to Deploy") }
        }
        Spacer(Modifier.height(16.dp))
    }
}
