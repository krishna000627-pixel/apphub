@file:OptIn(ExperimentalLayoutApi::class)
package com.krishna.apphub

import android.content.Intent
import android.net.Uri
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

fun ago(t: String): String = runCatching {
    val then = java.time.LocalDateTime.parse(t.replace(" ", "T")).toEpochSecond(java.time.ZoneOffset.UTC)
    val d = System.currentTimeMillis() / 1000 - then
    when { d < 60 -> "just now"; d < 3600 -> "${d / 60}m ago"; d < 86400 -> "${d / 3600}h ago"; else -> "${d / 86400}d ago" }
}.getOrDefault(t)

fun openUrl(ctx: android.content.Context, url: String) = ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

// ---------- app shell ----------
@Composable fun App(vm: HubVM) {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val tabs = listOf(
        Triple("dash", "Dashboard", Icons.Default.Dashboard), Triple("prompt", "Prompt", Icons.Default.Description),
        Triple("command", "Command", Icons.Default.Terminal), Triple("settings", "Settings", Icons.Default.Settings)
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
            composable("prompt") { PromptScreen(vm) }
            composable("command") { CommandScreen(vm) }
            composable("settings") { SettingsScreen(vm) }
            composable("add") { AddScreen(vm, nav) }
            composable("detail/{id}", listOf(navArgument("id") { type = NavType.LongType })) { Detail(vm, nav, it.arguments!!.getLong("id")) }
            composable("install/{id}", listOf(navArgument("id") { type = NavType.LongType })) { InstallScreen(vm, nav, it.arguments!!.getLong("id")) }
        }
    }
}

// ---------- 1. dashboard ----------
@Composable fun Dashboard(vm: HubVM, nav: NavHostController) {
    val ps by vm.projects.collectAsState()
    var f by remember { mutableStateOf("All") }
    var q by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.refreshAll() }
    fun hit(p: Project, k: String) = when (k) {
        "Pinned" -> vm.isPinned(p.id); "Errors" -> p.lastStatus == "failed"; "Updates" -> vm.hasUpdate(p); else -> true
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
            Stat("Updates", ps.count { vm.hasUpdate(it) }, Amber, Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Pinned", "Errors", "Updates").forEach { k ->
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
    val ctx = LocalContext.current
    val last = vm.runs[p.id]?.firstOrNull()
    val pinned = vm.isPinned(p.id)
    val upd = vm.hasUpdate(p)
    Panel(Modifier.clickable { nav.navigate("detail/${p.id}") }, border = if (p.lastStatus == "failed") Red.copy(alpha = 0.6f) else Line) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(Line, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Apps, null, tint = Cyan) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, fontWeight = FontWeight.Bold)
                Text(listOfNotNull(p.version, last?.let { ago(it.time) }).joinToString(" • "), fontSize = 12.sp, color = Muted)
            }
            IconButton({ vm.togglePin(p) }, Modifier.size(36.dp)) {
                Icon(if (pinned) Icons.Default.Star else Icons.Default.StarBorder, null, tint = if (pinned) Amber else Muted)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (p.lastStatus != "none") Pill(if (p.lastStatus == "passed") "✓ Build passed" else if (p.lastStatus == "failed") "✕ Build failed" else "… Running", statusColor(p.lastStatus))
            else Pill("No builds yet", Muted)
            if (upd) Pill("Update ready", Amber)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ vm.build(p) }, Modifier.weight(1f)) { Icon(Icons.Default.Build, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Build") }
            if (upd) Button({ nav.navigate("install/${p.id}") }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black)) {
                Icon(Icons.Default.Download, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Update")
            }
            Button({ vm.push(ctx, p) }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black)) {
                Icon(Icons.Default.Send, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Push")
            }
        }
    }
}

// ---------- 2. project detail ----------
@Composable fun Detail(vm: HubVM, nav: NavHostController, id: Long) {
    val p = vm.projects.collectAsState().value.firstOrNull { it.id == id } ?: return
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(1) }
    var ver by remember(p.id) { mutableStateOf(p.version) }
    var confirm by remember { mutableStateOf(false) }
    val runs = vm.runs[id] ?: emptyList()
    val err = vm.errors[id] ?: ""
    val upd = vm.hasUpdate(p)
    LaunchedEffect(id) { vm.refresh(p) }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        confirmButton = { TextButton({ vm.delete(p); nav.popBackStack() }) { Text("Remove", color = Red) } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } },
        title = { Text("Remove ${p.name}?") }, text = { Text("This only removes it from App Hub. Your repo and files stay untouched.") })
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Header(p.name, nav) {
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
                OutlinedButton({ openUrl(ctx, "https://github.com/${p.repo}") }, Modifier.weight(1f)) { Icon(Icons.Default.OpenInNew, null, Modifier.size(16.dp)); Text(" GitHub", fontSize = 13.sp) }
                Button({ nav.navigate("install/$id") }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (upd) Amber else Green, contentColor = Color.Black)) {
                    Text(if (upd) "Update ready" else "Install / Update", fontSize = 13.sp)
                }
            }
        }
        item {
            TabRow(tab, containerColor = Bg, contentColor = Green) {
                listOf("Overview", "Build History", "Settings").forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t, fontSize = 12.sp) }) }
            }
        }
        when (tab) {
            0 -> item {
                Panel {
                    Mono("package : ${p.packageName.ifBlank { "-" }}"); Mono("path    : ${p.localPath.ifBlank { "-" }}")
                    Mono("status  : ${p.lastStatus}", color = statusColor(p.lastStatus))
                    Mono("installed: ${vm.installedTag(id).ifBlank { "-" }}"); Mono("latest  : ${vm.latest[id] ?: "-"}")
                    Spacer(Modifier.height(10.dp))
                    Button({ vm.deployLatest(ctx, p) }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Color.Black)) {
                        Icon(Icons.Default.Terminal, null, Modifier.size(16.dp)); Text(" Deploy newest zip from Downloads", fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton({ vm.copy(Commands.deploy(p, Commands.bump(p.version), "update")) }, Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)); Text(" Copy deploy command for ${Commands.bump(p.version)}", fontSize = 12.sp)
                    }
                }
            }
            1 -> {
                items(runs) { r ->
                    val st = when (r.ok) { true -> "passed"; false -> "failed"; else -> "running" }
                    Row(Modifier.fillMaxWidth().clickable { openUrl(ctx, "https://github.com/${p.repo}/actions/runs/${r.id}") }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(statusColor(st), CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) { Mono("#${r.number}  ${r.msg.take(34)}"); Mono(ago(r.time), color = Color.Gray) }
                        Text(st, color = statusColor(st), fontSize = 12.sp)
                    }
                }
                if (runs.isEmpty()) item { Text("No runs yet. Push to trigger the first build.", color = Color.Gray) }
                if (err.isNotBlank()) item {
                    Panel(border = Red) {
                        Text("Build Error", color = Red, fontWeight = FontWeight.Bold); Mono(err)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ vm.copy(err) }, Modifier.weight(1f)) { Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)); Text(" Copy log", fontSize = 12.sp) }
                            OutlinedButton({ vm.selId = p.id; vm.task = "Fix error"; nav.navigate("prompt") }, Modifier.weight(1f)) { Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp), tint = Cyan); Text(" AI prompt", fontSize = 12.sp) }
                        }
                    }
                }
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
                    OutlinedButton({ vm.termuxSetup(ctx, p) }) { Text("Create repo + signing (Termux)") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton({ vm.push(ctx, p) }) { Text("Release now (push + CI + install)") }
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

// ---------- 3. prompt maker ----------
@Composable fun PromptScreen(vm: HubVM) {
    val ps by vm.projects.collectAsState()
    val p = vm.sel()
    var stack by remember(p?.id) { mutableStateOf(p?.stack ?: "") }
    var deliver by remember { mutableStateOf(true) }
    val text = if (p == null) "" else Prompts.make(vm.task, p, stack, vm.errors[p.id] ?: p.lastError, deliver)
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Header("Prompt Maker")
        Text("1. Select Project", color = Color.Gray); ProjectPicker(ps, p) { vm.selId = it.id }
        Text("2. What do you want to do?", color = Color.Gray)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Prompts.tasks.forEach { FilterChip(vm.task == it, { vm.task = it }, { Text(it) }) } }
        Text("3. Tech Stack", color = Color.Gray)
        OutlinedTextField(stack, { stack = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(deliver, { deliver = it }); Text("  Add zip + version bump + deploy command rules", fontSize = 12.sp) }
        Text("4. Generated Prompt", color = Color.Gray)
        Panel { Mono(text.ifBlank { "Add a project first." }) }
        BigButton("Copy prompt", Icons.Default.ContentCopy) { if (text.isNotBlank()) vm.copy(text) }
        Spacer(Modifier.height(12.dp))
    }
}

// ---------- 4. command maker ----------
@Composable fun CommandScreen(vm: HubVM) {
    val ps by vm.projects.collectAsState()
    val p = vm.sel()
    val ctx = LocalContext.current
    var ver by remember(p?.id) { mutableStateOf(p?.let { Commands.bump(it.version) } ?: "") }
    var msg by remember(p?.id) { mutableStateOf("") }
    var su by remember { mutableStateOf(false) }; var b by remember { mutableStateOf(false) }; var pu by remember { mutableStateOf(true) }
    var r by remember { mutableStateOf(false) }; var z by remember { mutableStateOf(false) }
    val deploy = if (p == null) "" else Commands.deploy(p, ver, msg.ifBlank { "update" })
    val script = if (p == null) "" else Commands.script(p, ver, su, b, pu, r, z)
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Header("Command Maker")
        Text("Project", color = Color.Gray); ProjectPicker(ps, p) { vm.selId = it.id }
        OutlinedTextField(ver, { ver = it }, label = { Text("Version") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Panel {
            Text("Deploy from zip", fontWeight = FontWeight.Bold)
            Text("Unzips Downloads/Name_vN.zip over the project, then runs release.sh.", fontSize = 12.sp, color = Muted)
            OutlinedTextField(msg, { msg = it }, label = { Text("Release message") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
            Mono(deploy.ifBlank { "Add a project first." })
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton({ if (deploy.isNotBlank()) vm.copy(deploy) }, Modifier.weight(1f)) { Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)); Text(" Copy") }
                Button({ if (p != null && deploy.isNotBlank()) { if (!Commands.runInTermux(ctx, deploy)) vm.toast("Termux not available — see Settings") else vm.update(p.copy(version = ver)) } },
                    Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Color.Black)) { Icon(Icons.Default.Terminal, null, Modifier.size(16.dp)); Text(" Run") }
            }
        }
        Button({ if (p != null) vm.deployLatest(ctx, p) else vm.toast("Add a project first") }, Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black)) {
            Icon(Icons.Default.Terminal, null, Modifier.size(16.dp)); Text(" Deploy newest zip from Downloads", fontSize = 12.sp)
        }
        Text("Or build a script", color = Color.Gray)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(su, { su = !su }, { Text("Setup repo+sign") }); FilterChip(b, { b = !b }, { Text("Build") })
            FilterChip(pu, { pu = !pu }, { Text("Push+Release") }); FilterChip(r, { r = !r }, { Text("Tag") }); FilterChip(z, { z = !z }, { Text("Zip") })
        }
        Panel { Mono(script.ifBlank { "Add a project first." }) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton({ if (script.isNotBlank()) vm.copy(script) }, Modifier.weight(1f)) { Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)); Text(" Copy") }
            Button({ if (p != null) { vm.runScript(ctx, p, ver, su, b, pu, r, z); vm.update(p.copy(version = ver)) } }, Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Color.Black)) { Icon(Icons.Default.Terminal, null, Modifier.size(16.dp)); Text(" Run in Termux") }
        }
        Spacer(Modifier.height(12.dp))
    }
}

// ---------- 5. install / update ----------
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
                r == null -> Text("No APK release found. Run Command Maker with Release ticked to publish one.", color = Amber)
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
            Text("From GitHub → Developer settings → OAuth Apps (Device Flow enabled).", fontSize = 12.sp, color = Color.Gray)
            OutlinedTextField(cid, { cid = it.trim() }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button({ vm.prefs.clientId = cid; vm.toast("Saved") }) { Text("Save") }
        }
        Panel {
            Text("Apps", fontWeight = FontWeight.Bold)
            Text("QuestBlock, BgBrowser and App Hub are added on first launch. Bring back any you removed.", fontSize = 12.sp, color = Muted)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ vm.restorePresets() }) { Icon(Icons.Default.Restore, null, Modifier.size(16.dp)); Text(" Restore presets", fontSize = 12.sp) }
                OutlinedButton({ ctx.packageManager.getLaunchIntentForPackage("com.termux")?.let { ctx.startActivity(it) } ?: vm.toast("Termux not found") }) { Text("Open Termux", fontSize = 12.sp) }
            }
        }
        Panel {
            Text("Termux setup", fontWeight = FontWeight.Bold)
            Mono("1. Install Termux from F-Droid/GitHub\n2. In Termux run:\n   mkdir -p ~/.termux\n   echo 'allow-external-apps=true' >> ~/.termux/termux.properties\n   termux-reload-settings\n   pkg install git openjdk-17 zip\n   termux-setup-storage\n3. Android Settings → Apps → App Hub → Permissions → enable 'Run commands in Termux'")
        }
        Text("App Hub $appVer", Modifier.fillMaxWidth().padding(vertical = 8.dp), color = Muted, fontSize = 12.sp)
    }
}

// ---------- add project wizard ----------
@Composable fun AddScreen(vm: HubVM, nav: NavHostController) {
    var name by remember { mutableStateOf("") }; var repo by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("/data/data/com.termux/files/home/") }
    var pkg by remember { mutableStateOf("") }; var ver by remember { mutableStateOf("v1") }
    var create by remember { mutableStateOf(true) }; var keys by remember { mutableStateOf(true) }; var tx by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Header("Setup project", nav)
        Text("Quick fill", color = Color.Gray)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Presets.all.forEach { pr ->
                AssistChip({ name = pr.name; repo = pr.repo; path = pr.path; pkg = pr.pkg; ver = pr.version; create = false; keys = false }, { Text(pr.name) })
            }
        }
        OutlinedTextField(name, { name = it; if (repo.isBlank() || repo == name.dropLast(1)) repo = it }, label = { Text("App name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(repo, { repo = it }, label = { Text("Repo name (or owner/name)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(path, { path = it }, label = { Text("Local path (Termux)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(pkg, { pkg = it }, label = { Text("Package name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(ver, { ver = it }, label = { Text("Version") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(create, { create = it }); Text("  Create GitHub repo (private)") }
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(keys, { keys = it }); Text("  Generate signing keys + set secrets") }
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(tx, { tx = it }); Text("  Do it via Termux (gh + keytool)") }
        BigButton("Run setup", Icons.Default.PlayArrow) { if (name.isNotBlank() && repo.isNotBlank()) vm.setup(ctx, name.trim(), repo.trim(), path.trim(), pkg.trim(), ver.trim(), create, keys, tx) else vm.toast("Name and repo required") }
        if (vm.log.isNotEmpty()) Panel { vm.log.forEach { Mono(it) } }
        Spacer(Modifier.height(16.dp))
    }
}
