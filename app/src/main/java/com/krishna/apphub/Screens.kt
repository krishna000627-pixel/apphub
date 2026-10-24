@file:OptIn(ExperimentalLayoutApi::class)
package com.krishna.apphub

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File

// ---------- shared widgets ----------
@Composable fun Mono(t: String, mod: Modifier = Modifier, color: Color = Color.Unspecified) =
    Text(t, mod, color, fontFamily = FontFamily.Monospace, fontSize = 12.sp)

/** v38: App / Website / Server chips, used when adding a project and in its settings. */
@Composable fun TypePicker(type: ProjectType, onType: (ProjectType) -> Unit) =
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ProjectType.values().forEach { t ->
            FilterChip(type == t, { onType(t) }, { Text(t.label) }, leadingIcon = { Icon(t.icon(), null, Modifier.size(16.dp)) })
        }
    }

@Composable fun Pill(t: String, c: Color) =
    Box(Modifier.widthIn(max = 180.dp).border(1.dp, c, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp)) { Text(t, color = c, fontSize = 11.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }

@Composable fun Panel(mod: Modifier = Modifier, border: Color = Line, content: @Composable ColumnScope.() -> Unit) =
    Column(mod.fillMaxWidth().background(Surf, RoundedCornerShape(12.dp)).border(1.dp, border, RoundedCornerShape(12.dp)).padding(12.dp), content = content)

/** Single-line button label (never wraps letter by letter when buttons share a row). */
@Composable fun BtnText(t: String, size: Int = 13) = Text(t, fontSize = size.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
private val BtnPad = PaddingValues(horizontal = 8.dp, vertical = 8.dp)

/** v28: 0..100 while this app's APK is downloading, else null. The progress lives in the ViewModel, so every screen shows it. */
fun dlPct(vm: HubVM, p: Project): Int? = vm.dlProgress[p.id]?.let { (it * 100).toInt().coerceIn(0, 100) }
fun dlText(pct: Int) = "$pct%"
/** v28: "Install" while the app is not on the phone, otherwise "Update". */
fun installLabel(vm: HubVM, p: Project, upd: Boolean): String = when {
    vm.isInstalled(p) == false -> "Install"
    vm.isInstalled(p) == true -> if (upd) "Update" else "Latest"
    else -> if (upd) "Update" else "Install"
}

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

/** Project chooser: a bottom sheet with a clean list instead of a small floating menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ProjectPicker(ps: List<Project>, sel: Project?, onSel: (Project) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton({ open = true }, Modifier.fillMaxWidth()) {
        Text(sel?.name ?: "Select project", Modifier.weight(1f)); Text(sel?.version ?: ""); Icon(Icons.Default.ArrowDropDown, null)
    }
    if (open) ModalBottomSheet({ open = false }, containerColor = Surf) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text("Choose project", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
            ps.forEach { p ->
                val on = p.id == sel?.id
                Row(Modifier.fillMaxWidth().clickable { onSel(p); open = false }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, Modifier.weight(1f), color = if (on) Green else Color.Unspecified, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                    Text(p.version, color = Muted, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    if (on) { Spacer(Modifier.width(8.dp)); Icon(Icons.Default.Check, null, tint = Green, modifier = Modifier.size(18.dp)) }
                }
            }
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

fun agoMs(ms: Long): String {
    val d = (System.currentTimeMillis() - ms) / 1000
    return when { d < 60 -> "just now"; d < 3600 -> "${d / 60}m ago"; d < 86400 -> "${d / 3600}h ago"; else -> "${d / 86400}d ago" }
}

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
    val ctx = LocalContext.current
    val ps by vm.projects.collectAsState()
    LaunchedEffect(ps.size) { vm.scanZips(ctx) }
    // everything stays live: silent sync every 3 s while App Hub is on screen (no spinners)
    // (v27: the interval is the "Live sync" choice in Settings: 3 s, 10 s or off)
    LaunchedEffect(Unit) { while (true) { if (vm.live) vm.autoSync(); delay(vm.syncDelayMs) } }
    // v27: a tap on a build / download notification opens that app's page
    LaunchedEffect(vm.openRoute) { vm.openRoute?.let { r -> nav.navigate(r) { launchSingleTop = true }; vm.openRoute = null } }
    val waiting = vm.zipFound.entries.firstOrNull { e -> vm.zipKey(e.key, e.value) !in vm.zipDismissed }
    val waitProj = waiting?.let { w -> ps.firstOrNull { it.id == w.key } }
    if (waiting != null && waitProj != null) {
        val f = waiting.value
        val key = vm.zipKey(waiting.key, f)
        AlertDialog(onDismissRequest = { vm.zipDismissed.add(key) },
            title = { Text("Push ${waitProj.name}?") },
            text = {
                Column {
                    Text(f.name, fontWeight = FontWeight.Bold, color = Cyan)
                    Mono("${Zipper.versionOf(f.name).ifBlank { "-" }} • ${"%.1f".format(f.length() / 1048576.0)} MB • ${agoMs(f.lastModified())}", color = Muted)
                    Spacer(Modifier.height(8.dp))
                    Text("Newest zip in Downloads for this app. Only new and changed files are uploaded.", fontSize = 13.sp)
                }
            },
            confirmButton = { TextButton({ vm.zipDismissed.add(key); vm.deployFile(ctx, waitProj, f); goDeploy(vm, nav, waitProj) }) { Text("Push now", color = Green) } },
            dismissButton = { TextButton({ vm.skipZip(waitProj, f) }) { Text("Skip this zip") } })
    }
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
            composable("settings") { SettingsScreen(vm, nav) }
            composable("about") { AboutScreen(vm, nav) }
            composable("file/{id}?path={path}", listOf(navArgument("id") { type = NavType.LongType }, navArgument("path") { type = NavType.StringType; defaultValue = "" })) {
                FileViewerScreen(vm, nav, it.arguments!!.getLong("id"), it.arguments?.getString("path").orEmpty())
            }
            composable("files/{id}", listOf(navArgument("id") { type = NavType.LongType })) { FilesScreen(vm, nav, it.arguments!!.getLong("id")) }
            composable("info/{id}", listOf(navArgument("id") { type = NavType.LongType })) { InfoScreen(vm, nav, it.arguments!!.getLong("id")) }
            composable("accounts") { AccountsScreen(vm, nav) }   // v43
            composable("render") { RenderScreen(vm, nav) }   // v45
            composable("github") { GitHubScreen(vm, nav) }   // v46
            composable("vercel") { VercelScreen(vm, nav) }
            composable("supabase") { SupabaseScreen(vm, nav) }
            composable("add") { AddScreen(vm, nav) }
            composable("env/{id}", listOf(navArgument("id") { type = NavType.LongType })) { EnvScreen(vm, nav, it.arguments!!.getLong("id")) }   // v42
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
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.autoSync() }   // fresh data at once, silently
    val ctx = LocalContext.current
    fun hit(p: Project, k: String) = when (k) {
        "Pinned" -> vm.isPinned(p.id); "To push" -> vm.pending(p); "Errors" -> p.lastStatus == "failed"; "Updates" -> vm.hasUpdate(p)
        else -> ProjectType.values().firstOrNull { it.plural == k }?.let { t -> p.kind == t } ?: true
    }
    val shown = ps.filter { hit(it, if (searching) "All" else f) && (q.isBlank() || it.name.contains(q, true) || it.repo.contains(q, true)) }
        .sortedWith(compareByDescending<Project> { vm.isPinned(it.id) }.thenBy { it.name.lowercase() })
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // v47: tap the name to refresh, search sits where the refresh icon was (opens in the header, no box above the list)
        BackHandler(searching) { searching = false; q = "" }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp).height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            if (searching) {
                SearchField(q, { q = it }, { searching = false }, Modifier.weight(1f))
            } else {
                Row(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { vm.refreshAll() }.padding(vertical = 8.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("App Hub", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Green, maxLines = 1)
                    if (vm.refreshing) { Spacer(Modifier.width(10.dp)); CircularProgressIndicator(Modifier.size(14.dp), color = Green, strokeWidth = 2.dp) }
                }
                IconButton({ searching = true }, Modifier.size(40.dp)) { Icon(Icons.Default.Search, null) }
                IconButton({ nav.navigate("add") }, Modifier.size(40.dp)) { Icon(Icons.Default.Add, null, tint = Green) }
            }
        }
        if (!searching) Column {   // v49: while searching only the header and the list stay
        if (vm.sessionExpired) Panel(Modifier.padding(bottom = 6.dp), border = Red.copy(alpha = 0.7f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("GitHub session expired", fontWeight = FontWeight.Bold, color = Red, fontSize = 13.sp)
                    Text("Live updates are paused until you log in again.", fontSize = 12.sp, color = Muted)
                }
                Button({ nav.navigate("accounts") { launchSingleTop = true } }, contentPadding = BtnPad, colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Color.White)) { BtnText("Log in") }
            }
        }
        // v27 feature 4: small amber note while GitHub is unreachable or the API limit is reached; it clears by itself
        if (vm.netNote.isNotBlank()) Row(Modifier.fillMaxWidth().padding(bottom = 6.dp).background(Amber.copy(alpha = 0.12f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, tint = Amber, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text(vm.netNote, fontSize = 12.sp, color = Amber)
        }
        // v27 feature 1: shown at once when you come back from GitHub
        if (vm.loginBusy) Text("Finishing login…", Modifier.padding(bottom = 6.dp), fontSize = 12.sp, color = Cyan)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("Apps", ps.size, Cyan, Modifier.weight(1f))
            Stat("Passing", ps.count { it.lastStatus == "passed" }, Green, Modifier.weight(1f))
            Stat("Failing", ps.count { it.lastStatus == "failed" }, Red, Modifier.weight(1f))
            Stat("To push", ps.count { vm.pending(it) }, Amber, Modifier.weight(1f))
        }
        // follow-up: a saved APK that was not installed (ignored or skipped installer)
        ps.filter { vm.waitingInstall(it) }.forEach { w ->
            Panel(Modifier.padding(top = 6.dp), border = Amber.copy(alpha = 0.6f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${w.name} ${vm.cachedTag(w.id)} is ready to install", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(if (vm.installSkipped(w)) "The installer was skipped. Tap Install to continue." else "Downloaded, not installed yet.", fontSize = 12.sp, color = Muted)
                    }
                    Button({ vm.installCached(ctx, w) }, contentPadding = BtnPad, colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black)) { BtnText("Install") }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        // v38: Websites / Servers chips appear once such a project exists
        val chips = listOf("All", "Pinned", "To push", "Errors", "Updates") +
            ProjectType.values().filter { t -> t != ProjectType.APP && ps.any { it.kind == t } }.map { it.plural }
        LaunchedEffect(chips) { if (f !in chips) f = "All" }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.forEach { k ->
                FilterChip(f == k, { f = k }, { Text("$k (${ps.count { hit(it, k) }})", fontSize = 12.sp) })
            }
        }
        }
        if (ps.isEmpty()) EmptyState(Icons.Default.Apps, "No apps yet", "Add your own app, or bring back the preset apps.", Modifier.padding(top = 12.dp)) {
            Button({ vm.restorePresets() }, colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black)) { Text("Add presets") }
            OutlinedButton({ nav.navigate("add") }) { Text("Add app") }
        } else if (shown.isEmpty()) {
            val (ic, t, d) = when {
                q.isNotBlank() -> Triple(Icons.Default.Search, "No match", "No app matches \"$q\".")
                f == "Pinned" -> Triple(Icons.Default.Star, "Nothing pinned", "Tap the star on an app to keep it at the top.")
                f == "To push" -> Triple(Icons.Default.CloudUpload, "Nothing to push", "A new zip in Downloads shows up here.")
                f == "Errors" -> Triple(Icons.Default.CheckCircle, "No failing builds", "All your builds are fine.")
                else -> Triple(Icons.Default.Download, "Everything is up to date", "New builds show up here as soon as they are ready.")
            }
            EmptyState(ic, t, d, Modifier.padding(top = 12.dp))
        }
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
    val dl = dlPct(vm, p)
    Panel(Modifier.clickable { nav.navigate("detail/${p.id}") }, border = if (p.lastStatus == "failed") Red.copy(alpha = 0.6f) else Line) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(Line, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Icon(p.kind.icon(), null, tint = Cyan) }
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val st = vm.buildStage(p)   // v38: Building / Passed (app), Deployed (website), Running (server)
            Pill(st.pill(), st.color())
            if (pend) Pill("+ Push ${vm.waitLabel(p)}", Amber)
            if (p.kind == ProjectType.APP) when (vm.isInstalled(p)) {
                true -> Pill("● ${vm.onPhone[p.id]?.ifBlank { null } ?: "On phone"}", Green)
                false -> Pill("○ Not installed", Muted)
                null -> if (p.packageName.isBlank()) Pill("? No package", Muted)
            } else {
                Pill(p.kind.label, Muted)
                vm.siteName(p)?.let { Pill("↗ $it", Cyan) }
            }
            // v35: download / update pill goes last, after the status and installed pills
            if (dl != null) Pill("Updating", Cyan)
            else if (upd) Pill(if (vm.isInstalled(p) == false) "Install" else if (vm.waitingInstall(p)) "Updated" else "Update", Amber)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ vm.build(p) }, Modifier.weight(1f), contentPadding = BtnPad) { Icon(Icons.Default.Build, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); BtnText("Build") }
            Button({ goDeploy(vm, nav, p) }, Modifier.weight(1f), contentPadding = BtnPad, colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black)) {
                Icon(Icons.Default.CloudUpload, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); BtnText("Deploy")
            }
            if (upd || dl != null) Button({ nav.navigate("install/${p.id}") }, Modifier.weight(1f), contentPadding = BtnPad, colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black)) {
                if (dl != null) CircularProgressIndicator({ dl / 100f }, Modifier.size(14.dp), color = Color.Black, strokeWidth = 2.dp)
                else Icon(Icons.Default.Download, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp)); BtnText(if (dl != null) "$dl%" else if (vm.isInstalled(p) == false) "Install" else "Update")
            }
        }
    }
}

// ---------- project detail (everything fetched in-app) ----------
@Composable fun Detail(vm: HubVM, nav: NavHostController, id: Long) {
    val p = vm.projects.collectAsState().value.firstOrNull { it.id == id } ?: return
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var openRun by remember { mutableStateOf<Long?>(null) }
    val runs = vm.runs[id] ?: emptyList()
    val err = vm.errors[id] ?: ""
    val upd = vm.hasUpdate(p)
    val pend = vm.pending(p)
    val dl = dlPct(vm, p)
    LaunchedEffect(id) { if (vm.repoFiles[id] == null) vm.loadFiles(p, quiet = true) }   // file count for the Files box
    LaunchedEffect(id, p.repo) { vm.refresh(p); vm.checkInstalls(); vm.loadDetails(p).join(); while (true) { delay(vm.syncDelayMs); if (vm.live) vm.loadDetails(p).join() } }
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
                Button({ goDeploy(vm, nav, p) }, Modifier.weight(1f), contentPadding = BtnPad, colors = ButtonDefaults.buttonColors(containerColor = if (pend) Amber else Green, contentColor = Color.Black)) {
                    Icon(if (pend) Icons.Default.AddCircle else Icons.Default.CloudUpload, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); BtnText(if (pend) "Push ${vm.waitLabel(p)}" else "Deploy")
                }
                if (p.kind == ProjectType.APP) OutlinedButton({ nav.navigate("install/$id") }, Modifier.weight(1f), contentPadding = BtnPad, colors = ButtonDefaults.outlinedButtonColors(contentColor = if (upd || dl != null) Amber else Cyan)) {
                    if (dl != null) { CircularProgressIndicator({ dl / 100f }, Modifier.size(14.dp), color = Amber, strokeWidth = 2.dp); Spacer(Modifier.width(6.dp)) }
                    BtnText(if (dl != null) dlText(dl) else installLabel(vm, p, upd))
                }
                else OutlinedButton({ vm.siteLink(p).let { if (it.isBlank()) vm.toast("Set the live URL in Settings") else openUrl(ctx, it) } }, Modifier.weight(1f), contentPadding = BtnPad, colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan)) {
                    Icon(Icons.Default.OpenInNew, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); BtnText(if (p.kind == ProjectType.WEBSITE) "Open site" else "Open server")
                }
            }
        }
        item {
            HubTabs(listOf("Info", "Builds", "Releases", "Settings"), tab) { tab = it }
        }
        when (tab) {
            0 -> {
                item {
                    // v34: short overview; tap it for everything else (InfoScreen)
                    val (instText, instColor) = installedLine(vm, p)
                    Panel(Modifier.clickable { nav.navigate("info/$id") }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                val st = vm.stage(p)
                                Mono("status   : ${st.label.lowercase()}", color = st.color())
                                if (p.kind == ProjectType.APP) Mono("installed: $instText", color = instColor)
                                else Mono("live     : ${vm.siteLink(p).ifBlank { "-" }}")
                                Mono("version  : ${p.version}")
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = Muted)
                        }
                    }
                }
                item {
                    // v31: the repo's files, one tap away; common files open straight on their own page
                    val all = vm.repoFiles[id]
                    val key = all?.let { l -> listOf("README.md", "HANDOFF.md", "app/src/main/AndroidManifest.xml", "app/build.gradle.kts", "build.gradle.kts", ".github/workflows/build.yml").filter { k -> l.any { it.path == k } } } ?: emptyList()
                    Panel(Modifier.clickable { nav.navigate("files/$id") }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).background(Line, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Folder, null, tint = Amber) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Files", fontWeight = FontWeight.Bold)
                                Text(if (all == null) "Browse" else "${all.size} files • ${fmtBytes(all.sumOf { it.size })}", fontSize = 12.sp, color = Muted)
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = Muted)
                        }
                        if (key.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                key.forEach { k ->
                                    AssistChip({ nav.navigate("file/$id?path=${Uri.encode(k)}") }, { Text(k.substringAfterLast('/'), fontSize = 11.sp, maxLines = 1) },
                                        leadingIcon = { Icon(fileIcon(k), null, Modifier.size(14.dp)) })
                                }
                            }
                        }
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
                val all = (runs + (vm.olderRuns[id] ?: emptyList())).distinctBy { it.id }   // v49: the whole history, newest first
                if (all.isNotEmpty()) item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Mono("${all.size} builds", Modifier.weight(1f), Muted)
                        TextButton({ vm.copy(vm.historyText(p, all)) }) { Text("Copy history", fontSize = 12.sp, color = Cyan) }
                    }
                }
                items(all) { r ->
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
                if ((runs.size >= 15 || vm.olderRuns[id] != null) && vm.olderMore[id] != false) item {
                    OutlinedButton({ vm.loadOlder(p) }, Modifier.fillMaxWidth()) { BtnText(if (vm.olderLoading[id] == true) "Loading…" else "Load older builds") }
                }
                if (runs.isEmpty()) item {
                    EmptyState(Icons.Default.Build, "No builds yet", "Deploy a zip and the first build starts on GitHub.") {
                        Button({ goDeploy(vm, nav, p) }, colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black)) { Text("Deploy a zip") }
                    }
                }
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
                        Mono(vm.notesFor(p, r.tag, r.notes).take(300))
                        if (r.sizeKb > 0) Mono("APK ${"%.1f".format(r.sizeKb / 1024.0)} MB", color = Muted)
                    }
                }
                if (rl.isEmpty()) item { EmptyState(Icons.Default.Download, "No releases yet", "A release with the APK appears here once a build passes.") }
            }
            else -> item { AppSettings(vm, nav, p) }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ---------- per-app settings ----------
@Composable fun SectionTitle(icon: ImageVector, t: String) =
    Row(Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = Green); Spacer(Modifier.width(8.dp))
        Text(t.uppercase(), fontSize = 11.sp, color = Green, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }

/** Clear on/off look: green track + dark thumb when on, outlined track + grey thumb when off. */
/** v44: a setup switch shown like a project row: icon, title, who is connected, and the switch. The whole row is tappable. */
@Composable fun ConnRow(icon: ImageVector, title: String, status: String, statusColor: Color, on: Boolean, onTap: () -> Unit) {
    Panel(Modifier.clickable { onTap() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Cyan, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(status, fontSize = 12.sp, color = statusColor)
            }
            HubSwitch(on, { onTap() })
        }
    }
}

@Composable fun HubSwitch(checked: Boolean, onChange: (Boolean) -> Unit) = Switch(checked, onChange,
    colors = SwitchDefaults.colors(
        checkedThumbColor = Color.Black, checkedTrackColor = Green, checkedBorderColor = Green,
        uncheckedThumbColor = Muted, uncheckedTrackColor = Color.Transparent, uncheckedBorderColor = Muted
    ))

@Composable fun SettingRow(icon: ImageVector, title: String, sub: String, tint: Color = Cyan, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) =
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(Line, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(18.dp), tint = tint) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(title, fontSize = 14.sp); Text(sub, fontSize = 11.sp, color = Muted) }
        trailing()
    }

@Composable fun AppSettings(vm: HubVM, nav: NavHostController, p: Project) {
    val ctx = LocalContext.current
    var name by remember(p.id) { mutableStateOf(p.name) }
    var repo by remember(p.id) { mutableStateOf(p.repo) }
    var pkg by remember(p.id) { mutableStateOf(p.packageName) }
    var type by remember(p.id) { mutableStateOf(p.kind) }
    var url by remember(p.id) { mutableStateOf(p.liveUrl) }
    var stack by remember(p.id) { mutableStateOf(p.stack) }
    var ver by remember(p.id, p.version) { mutableStateOf(p.version) }
    var confirm by remember { mutableStateOf(false) }
    var confirmUndo by remember { mutableStateOf(false) }
    val dirty = name != p.name || repo != p.repo || pkg != p.packageName || stack != p.stack || ver != p.version || type != p.kind || (type != ProjectType.APP && cleanUrl(url) != p.liveUrl)
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        confirmButton = { TextButton({ vm.delete(p); nav.popBackStack() }) { Text("Remove", color = Red) } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } },
        title = { Text("Remove ${p.name}?") }, text = { Text("This only removes it from App Hub. Your repo and files stay untouched.") })
    if (confirmUndo) AlertDialog(onDismissRequest = { confirmUndo = false },
        confirmButton = { TextButton({ confirmUndo = false; vm.undoLastPush(p) }) { Text("Undo push", color = Amber) } },
        dismissButton = { TextButton({ confirmUndo = false }) { Text("Cancel") } },
        title = { Text("Undo the last push?") },
        text = { Text("Adds a new commit that puts the previous code back and starts a build. History is kept. Android will not install an older versionCode over a newer one, so bump the version on your next push.") })
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Panel {
            SectionTitle(Icons.Default.Info, "${type.label} details")
            TypePicker(type) { type = it }
            OutlinedTextField(name, { name = it }, label = { Text("App name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(repo, { repo = it }, label = { Text("GitHub repo (owner/name)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            if (type == ProjectType.APP) OutlinedTextField(pkg, { pkg = it }, label = { Text("Package name") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            else {
                OutlinedTextField(url, { url = it }, label = { Text(if (type == ProjectType.WEBSITE) "Live URL" else "Endpoint URL") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    placeholder = { Text(if (type == ProjectType.WEBSITE) "found automatically for GitHub Pages" else "https://api.example.com/health") })
                Text(if (type == ProjectType.WEBSITE) "Shown as Deployed once the build passes." else "Shown as Running once this https address answers after a passed deploy. Without one, a passed deploy counts as Running.",
                    fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
            }
            OutlinedTextField(stack, { stack = it }, label = { Text("Tech stack") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        }
        HostingPanels(vm, nav, p)   // v62: Repository, Hosting, GitHub Pages and Vercel panels live in HostingPage.kt
        Panel {
            SectionTitle(Icons.Default.Tag, "Version")
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(ver, { ver = it }, Modifier.weight(1f), label = { Text("Version") }, singleLine = true, enabled = !vm.isAuto(p.id))
                Spacer(Modifier.width(8.dp))
                OutlinedButton({ ver = Commands.bump(ver) }, enabled = !vm.isAuto(p.id)) { Text("+1 → ${Commands.bump(ver)}", fontSize = 12.sp) }
            }
            if (vm.isAuto(p.id)) Text("Follows the latest pushed build, so it can never run ahead of it. Turn the switch off to type a version by hand.", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
            Mono("last pushed: ${vm.pushedV[p.id] ?: "-"}", Modifier.padding(top = 4.dp), Muted)
            SettingRow(Icons.Default.Sync, "Follow builds automatically", "Label updates on every new build, however it was started") {
                HubSwitch(vm.isAuto(p.id), { vm.setAuto(p.id, it) })
            }
        }
        Button({
            val r = repo.trim().let { if (it.contains("/") || vm.login.isBlank()) it else "${vm.login}/$it" }
            vm.update(p.copy(name = name.trim().ifBlank { p.name }, repo = r, packageName = if (type == ProjectType.APP) pkg.trim() else p.packageName, stack = stack.trim(), version = ver.trim().ifBlank { p.version },
                type = type.name, liveUrl = if (type == ProjectType.APP) p.liveUrl else cleanUrl(url)))
            vm.toast("Saved")
        }, Modifier.fillMaxWidth().height(46.dp), enabled = dirty, colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black)) {
            Icon(Icons.Default.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (dirty) "Save changes" else "Saved", fontWeight = FontWeight.Bold)
        }
        Panel {
            SectionTitle(Icons.Default.Build, "Build and signing")
            SettingRow(Icons.Default.PlayArrow, "Trigger a build", "Runs the GitHub workflow on the latest code", Green, { vm.build(p) })
            SettingRow(Icons.Default.Undo, "Undo last push", "Puts the previous code back as a new commit", Amber, { confirmUndo = true })
            SettingRow(Icons.Default.Sync, "Re-send signing secrets", "Sets the 4 GitHub secrets again from the stored keystore", Cyan, { vm.resendSecrets(p) })
            SettingRow(Icons.Default.Key, "Copy keystore backup", "Keep this safe. Updates must use the same key.", Amber, {
                vm.keyBackup(p).let { k -> if (k.isBlank()) vm.toast("No keystore stored") else vm.copy(k) }
            })
        }
        Panel {
            SectionTitle(Icons.Default.Tune, "Shortcuts")
            SettingRow(Icons.Default.Star, "Pin to top", "Pinned apps sort first on the dashboard", Amber) {
                HubSwitch(vm.isPinned(p.id), { vm.togglePin(p) })
            }
            SettingRow(Icons.Default.Restore, "Reset installed marker", "Now: ${vm.installedTag(p.id).ifBlank { "none" }}. Clears the update check.", Cyan, { vm.clearInstalled(p.id) })
            SettingRow(Icons.Default.AutoAwesome, "Copy adopt prompt", "Send it to Claude with the project", Cyan, { vm.copy(Prompts.adopt(p)) })
            SettingRow(Icons.Default.OpenInNew, "Open on GitHub", p.repo, Muted, { openUrl(ctx, "https://github.com/${p.repo}") })
        }
        Panel(border = Red.copy(alpha = 0.6f)) {
            SectionTitle(Icons.Default.Warning, "Danger zone")
            SettingRow(Icons.Default.Delete, "Remove from App Hub", "Your repo and files stay untouched", Red, { confirm = true })
        }
    }
}

// ---------- deploy (replaces the Termux command page) ----------
@Composable fun DeployScreen(vm: HubVM, nav: NavHostController) {
    val ps by vm.projects.collectAsState()
    val p = vm.sel()
    val ctx = LocalContext.current
    var note by remember { mutableStateOf("") }
    LaunchedEffect(p?.id) { vm.scanZips(ctx) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.pickZip(ctx, uri) }
    val pack = vm.zipPack
    var target by remember { mutableStateOf<Project?>(null) }   // set when the zip name does not match the selected project
    var older by remember { mutableStateOf<Project?>(null) }    // v28: set when the zip is not newer than what is on GitHub
    fun start(tp: Project, pk: Zipper.Pack, n: String) { if (vm.isOlder(tp, pk)) older = tp else vm.deploy(ctx, tp, pk, n) }
    // a chosen zip selects the project it is named after
    LaunchedEffect(pack?.name) { pack?.let { pk -> vm.projectFor(pk.name)?.let { if (it.id != vm.selId) vm.selId = it.id } } }
    val last = p?.let { vm.runs[it.id]?.firstOrNull() }
    LaunchedEffect(p?.id) { p?.let { vm.refresh(it) } }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Header("Deploy")
        Text("Pick your zip and App Hub pushes it to GitHub and builds it. Nothing else needed.", fontSize = 12.sp, color = Muted)
        ProjectPicker(ps, p) { vm.selId = it.id }
        val lf = p?.let { vm.zipFound[it.id] }   // newest zip not pushed yet (empty once pushed or skipped)
        if (lf != null) Panel {
            Text("New zip in Downloads", fontWeight = FontWeight.Bold)
            Text(lf.name, color = Cyan, fontWeight = FontWeight.Bold)
            Mono("${"%.1f".format(lf.length() / 1048576.0)} MB • ${agoMs(lf.lastModified())}", color = Muted)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ vm.useFile(lf) }, colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Color.Black)) { Text("Use this zip") }
                OutlinedButton({ if (p != null) vm.deployFile(ctx, p, lf) }) { Text("Push now") }
            }
        }
        Panel {
            if (pack == null) {
                Text(if (lf != null) "Or choose another zip" else "Choose a zip", fontWeight = FontWeight.Bold)
                Text("Pick it from Downloads. Build folders and keystores inside the zip are skipped.", fontSize = 12.sp, color = Muted)
            } else {
                Text(pack.name, fontWeight = FontWeight.Bold, color = Cyan)
                Mono("version : ${pack.version.ifBlank { "-" }}")
                Mono("files   : ${pack.entries.size}  (skipped ${pack.skipped})")
                Mono("size    : ${"%.1f".format(pack.bytes / 1048576.0)} MB")
                if (p != null && !vm.nameMatches(p, pack.name)) Text("⚠ This zip name doesn't match ${p.name}", color = Amber, fontSize = 12.sp)
                if (pack.findings.isNotEmpty()) Text("⚠ ${pack.findings.size} possible secret(s) in this zip. You are asked before the push.", color = Amber, fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton({ pick.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }) {
                Icon(Icons.Default.FolderOpen, null, Modifier.size(16.dp)); Text(if (pack == null) " Pick zip" else " Change")
            }
        }
        OutlinedTextField(note, { note = it }, label = { Text("Note") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (pack != null && p != null) Mono("commit : ${pack.version.ifBlank { Commands.bump(p.version) }}: ${note.ifBlank { "update from App Hub" }}", color = Muted)
        Row(verticalAlignment = Alignment.CenterVertically) { HubSwitch(vm.autoInstall, { vm.updateAutoInstall(it) }); Text("  Auto-install", fontSize = 12.sp) }
        if (pack != null && p != null) OutlinedButton({ vm.preview(p, pack) }, Modifier.fillMaxWidth(), enabled = !vm.deploying) {
            Icon(Icons.Default.Compare, null, Modifier.size(16.dp)); Text(" Preview", fontSize = 13.sp)
        }
        BigButton(if (vm.deploying) "Working…" else "Push + build", Icons.Default.CloudUpload) {
            if (pack != null && p != null) { if (vm.nameMatches(p, pack.name)) start(p, pack, note) else target = p }
            else vm.toast("Pick a project and a zip first")
        }
        target?.let { tp ->
            val named = pack?.let { vm.projectFor(it.name) }
            ZipMismatchDialog(pack?.name ?: "", named, tp,
                onNamed = { target = null; named?.let { n -> vm.selId = n.id; if (pack != null) start(n, pack, note) } },
                onSelected = { target = null; if (pack != null) start(tp, pack, note) },
                onCancel = { target = null })
        }
        vm.typeAsk?.let { a -> TypeDialog(a, onAnyway = { vm.typePushAnyway(ctx) }, onLeave = { vm.typeLeaveOut(ctx) }, onCancel = { vm.typeCancel() }) }
        vm.secretAsk?.let { a -> SecretDialog(a, onAnyway = { vm.secretPushAnyway(ctx) }, onLeave = { vm.secretLeaveOut(ctx) }, onCancel = { vm.secretCancel() }) }
        older?.let { op ->
            if (pack != null) OldVersionDialog(pack.name, pack.version, vm.pushedVersion(op), op,
                onPush = { older = null; vm.deploy(ctx, op, pack, note) }, onCancel = { older = null })
        }
        if (vm.deployLog.isNotEmpty()) Panel {
            vm.deployLog.forEach { l ->
                val t = l.trimStart()
                Mono(l, color = when {
                    t.startsWith("✗") -> Red
                    t.startsWith("✓") || t.startsWith("+ ") -> Green
                    t.startsWith("~ ") -> Amber
                    else -> Color.Unspecified
                })
            }
        }
        if (p != null && last != null) Panel(border = okColor(last.ok)) {
            Text("Latest build #${last.number}", fontWeight = FontWeight.Bold)
            Mono("${mark(last.ok)} ${when (last.ok) { true -> "passed"; false -> "failed"; else -> "running…" }}  ${ago(last.time)}", color = okColor(last.ok))
            if (last.ok == false) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({
                        val e = vm.errors[p.id].orEmpty().ifBlank { p.lastError }
                        if (e.isNotBlank()) vm.copy(e) else vm.toast("No error text yet")
                    }) { Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)); Text(" Copy errors", fontSize = 12.sp) }
                    OutlinedButton({ vm.selId = p.id; vm.task = "Fix error"; nav.navigate("prompt") }) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp), tint = Cyan); Text(" AI prompt", fontSize = 12.sp)
                    }
                }
            }
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
    var deliver by remember { mutableStateOf(vm.prefs.promptDeliver) }   // v40: remembered
    val selKind = p?.kind ?: ProjectType.APP
    val tasks = Prompts.tasksFor(selKind)
    val task = if (vm.task in tasks) vm.task else tasks.first()   // v41: a task of another project type falls back to the first one
    val err = if (p == null) "" else (vm.errors[p.id] ?: p.lastError)
    val text = if (p == null) "" else Prompts.make(task, p, stack, err, deliver, vm.promptNote, vm.isVercel(p))
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Header("Prompt Maker")
        Text("1. Select Project", color = Color.Gray); ProjectPicker(ps, p) { vm.selId = it.id }
        Text("2. What do you want to do?", color = Color.Gray)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { tasks.forEach { FilterChip(task == it, { vm.task = it }, { Text(it) }) } }
        run {
            Text("3. Tech Stack", color = Color.Gray)
            OutlinedTextField(stack, { stack = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) { HubSwitch(deliver, { deliver = it; vm.prefs.promptDeliver = it }); Text("  Add zip name + version bump rules", fontSize = 12.sp) }
            Text("4. What do you want changed?", color = Color.Gray)
            OutlinedTextField(vm.promptNote, { vm.promptNote = it }, Modifier.fillMaxWidth(), minLines = 4, label = { Text(Prompts.hint(task, selKind)) })
            if (task == "Fix error") Mono(if (err.isBlank()) "No build error on record for ${p?.name ?: "this app"}." else "Build error included (${err.lines().count { it.isNotBlank() }} lines)", color = Muted)
            if (p != null) Mono("$task • ${p.name} ${p.version}${if (deliver) " • delivery rules" else ""}", color = Muted)
            BigButton("Copy prompt", Icons.Default.ContentCopy) {
                when {
                    text.isBlank() -> vm.toast("Add a project first")
                    vm.promptNote.isBlank() && task != "Fix error" -> vm.toast("Write what you want changed first")
                    else -> vm.copy(text)
                }
            }
            Text("The full prompt is not shown here. Copy it and check it in Claude.", fontSize = 11.sp, color = Muted)
        }
        Spacer(Modifier.height(12.dp))
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
private fun openApp(ctx: android.content.Context, vm: HubVM, p: Project) {
    val i = ctx.packageManager.getLaunchIntentForPackage(p.packageName)
    if (i == null) vm.toast("${p.name} can't be opened") else ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable fun InstallScreen(vm: HubVM, nav: NavHostController, id: Long) {
    val p = vm.projects.collectAsState().value.firstOrNull { it.id == id } ?: return
    val ctx = LocalContext.current
    var checked by remember { mutableStateOf(false) }
    // live: the app-wide 3 s sync keeps the release current; this makes the first answer immediate
    LaunchedEffect(id) { vm.refresh(p).join(); checked = true }
    LaunchedEffect(id, p.repo) { vm.loadDetails(p).join(); while (true) { delay(vm.syncDelayMs); if (vm.live) vm.loadDetails(p).join() } }
    var confirmPrev by remember { mutableStateOf(false) }   // v27 feature 5: reinstall the previous build
    val rel = vm.latestRel[id]
    val inst = vm.installedTag(id)
    val phone = vm.isInstalled(p)
    val prog = vm.dlProgress[id]          // null = nothing downloading; lives in the ViewModel, so it survives leaving this page
    val err = vm.dlError[id]
    val ready = rel != null && vm.cachedTag(id) == rel.tag && vm.hasCached(p)
    val partial = rel?.let { ApkStore.partBytes(ctx, id, it.tag) } ?: 0L
    val current = rel != null && inst == rel.tag && phone != false
    val behind = vm.hasUpdate(p)
    val (title, tone) = when {
        rel == null -> (if (checked) "No APK release yet" else "Checking releases…") to Muted
        current -> "Up to date" to Green
        phone == false -> "Not installed" to Cyan
        behind || (inst.isNotBlank() && inst != rel.tag) -> "Update available" to Amber
        else -> "Latest build available" to Cyan
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Header(p.name, nav)
        Panel(border = tone.copy(alpha = 0.7f)) {
            Text(title, color = tone, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            if (rel != null) {
                val from = when { phone == false -> "not installed"; inst.isNotBlank() -> inst; phone == true -> "installed"; else -> "unknown" }
                Spacer(Modifier.height(4.dp))
                Text("$from → ${rel.tag}", fontSize = 20.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Mono(listOfNotNull("released ${ago(rel.time)}", if (rel.sizeKb > 0) "${"%.1f".format(rel.sizeKb / 1024.0)} MB" else null,
                    if (phone == true) "on phone ${vm.onPhone[id] ?: ""}".trim() else null).joinToString(" • "), color = Muted)
            } else if (checked) Text("Deploy a zip and wait for the build to pass.", fontSize = 12.sp, color = Muted)
        }
        when {
            prog != null -> Panel(border = Cyan) {
                Text("Downloading ${(prog * 100).toInt()}%", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator({ prog }, Modifier.fillMaxWidth(), color = Green)
                rel?.let { if (it.sizeKb > 0) Mono("${"%.1f".format(it.sizeKb * prog / 1024.0)} / ${"%.1f".format(it.sizeKb / 1024.0)} MB", Modifier.padding(top = 4.dp), Muted) }
                Text("Keeps going if you leave this page.", fontSize = 11.sp, color = Muted)
            }
            ready && !current -> Panel(border = Amber.copy(alpha = 0.7f)) { Text("Downloaded and waiting for install", color = Amber, fontSize = 13.sp) }
            err != null && !ready -> Panel(border = Red.copy(alpha = 0.7f)) {
                Text(err, color = Red, fontSize = 13.sp)
                if (partial > 0) Mono("${"%.1f".format(partial / 1048576.0)} MB already saved", Modifier.padding(top = 2.dp), Muted)
            }
            partial > 0 && !ready -> Panel {
                Text("Download paused", fontWeight = FontWeight.Bold)
                Mono("${"%.1f".format(partial / 1048576.0)} MB already saved. Resume continues from there.", color = Muted)
            }
        }
        rel?.let { r ->
            val label = when {
                prog != null -> "Downloading… ${(prog * 100).toInt()}%"
                ready && current -> "Reinstall"
                ready -> if (phone == false || (inst.isBlank() && phone != true)) "Install" else "Install update"
                partial > 0 -> "Resume download"
                current -> "Download & reinstall"
                else -> "Download & install"
            }
            Button({ if (ready) vm.installCached(ctx, p) else vm.download(ctx, p, r, true) }, Modifier.fillMaxWidth().height(48.dp), enabled = prog == null,
                colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black)) {
                Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text(label, fontWeight = FontWeight.Bold)
            }
            if (phone == true && p.packageName.isNotBlank()) OutlinedButton({ openApp(ctx, vm, p) }, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.OpenInNew, null, Modifier.size(16.dp)); Text(" Open ${p.name}")
            }
            Panel { Text("What's new in ${r.tag}", fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Mono(vm.notesFor(p, r.tag, r.body)) }
        }
        // v27 feature 5: the build that was current before the latest one (one extra file per app)
        if (vm.hasPrev(p)) OutlinedButton({ confirmPrev = true }, Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Restore, null, Modifier.size(16.dp)); Text(" Previous build (${vm.prevTag(p)})")
        }
        if (confirmPrev) AlertDialog(onDismissRequest = { confirmPrev = false },
            title = { Text("Install the previous build?") },
            text = { Text("This reinstalls ${vm.prevTag(p)}. Android will not install an older versionCode over a newer one, so if the newer build is already on your phone the install is refused. Then uninstall ${p.name} first (its data is lost).") },
            confirmButton = { TextButton({ confirmPrev = false; vm.installPrevious(ctx, p) }) { Text("Install previous", color = Amber) } },
            dismissButton = { TextButton({ confirmPrev = false }) { Text("Cancel") } })
        val rl = vm.releases[id].orEmpty()
        if (rl.isNotEmpty()) Panel {
            Text("Recent builds", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            rl.take(5).forEach { x ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Mono(x.tag, Modifier.weight(1f), if (x.tag == inst) Green else Cyan)
                    if (x.tag == inst) Mono("installed  ", color = Green)
                    Mono(ago(x.time), color = Muted)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

// ---------- settings ----------
/** v43: every login on one page: GitHub, Vercel, Supabase, Render. Settings only shows who is connected. */
@Composable fun AccountsScreen(vm: HubVM, nav: NavHostController) {
    val ctx = LocalContext.current
    var vTok by remember { mutableStateOf("") }; var vTeam by remember { mutableStateOf("") }   // Vercel login fields
    var sTok by remember { mutableStateOf("") }; var rKey by remember { mutableStateOf("") }   // Supabase token, Render key
    LaunchedEffect(Unit) { vm.loadSupabase(); vm.loadRender() }   // silent: the lists fill in by themselves
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Header("Accounts", nav)
        Panel {
            Text("GitHub", fontWeight = FontWeight.Bold)
            Text(if (vm.login.isBlank()) "Not logged in" else if (vm.sessionExpired) "Session expired for ${vm.login}" else "Logged in as ${vm.login}",
                color = if (vm.login.isBlank() || vm.sessionExpired) Amber else Green)
            vm.userCode?.let { Spacer(Modifier.height(8.dp)); Text("Enter this code on GitHub:"); Text(it, fontSize = 30.sp, color = Cyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) }
            if (vm.loginMsg.isNotBlank() && (vm.login.isBlank() || vm.sessionExpired)) { Spacer(Modifier.height(6.dp)); Text(vm.loginMsg, fontSize = 12.sp, color = if (vm.loginBusy) Cyan else Amber) }
            Spacer(Modifier.height(8.dp))
            if (vm.login.isBlank() || vm.sessionExpired) {
                BigButton(if (vm.login.isBlank()) "Login with GitHub" else "Log in again", Icons.Default.Login) { vm.startLogin() }
                if (vm.loginMsg.isNotBlank()) TextButton({ vm.cancelLogin() }) { Text("Cancel") }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(vm.clientSecret, { vm.updateClientSecret(it) }, label = { Text("Client secret (optional)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Text("With a secret, GitHub sends you straight back to App Hub (set the OAuth app Callback URL to apphub://oauth) and the web login is used by default. Without it, a code is used and you switch back yourself.", fontSize = 11.sp, color = Muted)
            } else {
                BigButton("Open GitHub", Icons.Default.Code) { nav.navigate("github") { launchSingleTop = true } }
                OutlinedButton({ vm.logout() }) { Text("Log out") }
            }
        }
        // ---- v39: Vercel login (token) for websites and servers ----
        Panel {
            Text("Vercel", fontWeight = FontWeight.Bold)
            if (vm.vercelUser.isBlank()) {
                Text("Not logged in", color = Amber)
                Text("Websites and servers can deploy on Vercel from their GitHub repo. Create a token at vercel.com/account/tokens and paste it here.", fontSize = 12.sp, color = Muted)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(vTok, { vTok = it }, label = { Text("Vercel token") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(vTeam, { vTeam = it }, label = { Text("Team ID (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                if (vm.vercelMsg.isNotBlank()) Text(vm.vercelMsg, Modifier.padding(top = 6.dp), fontSize = 12.sp, color = Amber)
                Spacer(Modifier.height(8.dp))
                BigButton(if (vm.vercelBusy) "Checking…" else "Log in to Vercel", Icons.Default.Login) { if (!vm.vercelBusy) vm.vercelLogin(vTok, vTeam) }
                TextButton({ openUrl(ctx, "https://vercel.com/account/tokens") }) { Text("Open token page") }
            } else {
                Text("Logged in as ${vm.vercelUser}", color = Green)
                if (vm.vercelTeam.isNotBlank()) Text("Team ${vm.vercelTeam}", fontSize = 12.sp, color = Muted)
                Spacer(Modifier.height(8.dp))
                BigButton("Open Vercel", Icons.Default.Public) { nav.navigate("vercel") { launchSingleTop = true } }
                OutlinedButton({ vm.vercelLogout() }) { Text("Log out") }
            }
        }
        // ---- v43: Supabase ----
        Panel {
            Text("Supabase", fontWeight = FontWeight.Bold)
            if (!vm.supabaseOn) {
                Text("Not connected", color = Amber)
                Text("Create an access token at supabase.com/dashboard/account/tokens and paste it here.", fontSize = 12.sp, color = Muted)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(sTok, { sTok = it }, label = { Text("Supabase access token") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                if (vm.supabaseMsg.isNotBlank()) Text(vm.supabaseMsg, Modifier.padding(top = 6.dp), fontSize = 12.sp, color = Amber)
                Spacer(Modifier.height(8.dp))
                BigButton(if (vm.supabaseBusy) "Checking…" else "Connect Supabase", Icons.Default.Login) { if (!vm.supabaseBusy) vm.supabaseLogin(sTok) { sTok = "" } }
                TextButton({ openUrl(ctx, "https://supabase.com/dashboard/account/tokens") }) { Text("Open token page") }
            } else {
                Text("Connected", color = Green)
                if (vm.supabaseMsg.isNotBlank()) Text(vm.supabaseMsg, fontSize = 12.sp, color = Amber)
                Text("${vm.supabaseProjects.size} project(s)", fontSize = 12.sp, color = Muted)
                Spacer(Modifier.height(8.dp))
                BigButton("Open Supabase", Icons.Default.Storage) { nav.navigate("supabase") { launchSingleTop = true } }
                OutlinedButton({ vm.supabaseLogout() }) { Text("Disconnect") }
            }
        }
        // ---- v43: Render (v45: the service list lives on its own page) ----
        Panel {
            Text("Render", fontWeight = FontWeight.Bold)
            if (vm.renderUser.isBlank()) {
                Text("Not connected", color = Amber)
                Text("Create an API key at dashboard.render.com/u/settings#api-keys and paste it here.", fontSize = 12.sp, color = Muted)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(rKey, { rKey = it }, label = { Text("Render API key") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                if (vm.renderMsg.isNotBlank()) Text(vm.renderMsg, Modifier.padding(top = 6.dp), fontSize = 12.sp, color = Amber)
                Spacer(Modifier.height(8.dp))
                BigButton(if (vm.renderBusy) "Checking…" else "Connect Render", Icons.Default.Login) { if (!vm.renderBusy) vm.renderLogin(rKey) { rKey = "" } }
                TextButton({ openUrl(ctx, "https://dashboard.render.com/u/settings#api-keys") }) { Text("Open API keys page") }
            } else {
                Text("Connected as ${vm.renderUser}", color = Green)
                Text("${vm.renderServices.size} service(s)", fontSize = 12.sp, color = Muted)
                Spacer(Modifier.height(8.dp))
                BigButton("Open Render", Icons.Default.Cloud) { nav.navigate("render") { launchSingleTop = true } }
                OutlinedButton({ vm.renderLogout() }) { Text("Disconnect") }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable fun SettingsScreen(vm: HubVM, nav: NavHostController) {
    val ctx = LocalContext.current
    val info = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }.getOrNull() }
    val appVer = info?.versionName ?: "?"
    // v27 feature 6: space used by saved APKs, refreshed in the background
    var storage by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { storage = withContext(Dispatchers.IO) { vm.apkBytes() }; delay(3_000) } }
    var confirmClear by remember { mutableStateOf(false) }
    // v27 feature 2: backup
    var warnExport by remember { mutableStateOf(false) }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) vm.exportBackup(ctx, uri) }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.readBackup(ctx, uri) }
    // v27 feature 3: notification permission, asked when the switch is turned on
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false },
        title = { Text("Clear saved APKs?") },
        text = { Text("Deletes the saved latest and previous builds of every app (${fmtBytes(storage)}). You can download them again, but the Previous build option is gone.") },
        confirmButton = { TextButton({ confirmClear = false; vm.clearApks() }) { Text("Clear", color = Red) } },
        dismissButton = { TextButton({ confirmClear = false }) { Text("Cancel") } })
    if (warnExport) AlertDialog(onDismissRequest = { warnExport = false },
        title = { Text("Export backup") },
        text = { Text("The file holds your app list and the signing keystores with their passwords. Anyone who has it can sign apps as you. Keep it private and never share it.") },
        confirmButton = { TextButton({
            warnExport = false
            exportFile.launch("apphub-backup-${java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())}.json")
        }) { Text("Export", color = Amber) } },
        dismissButton = { TextButton({ warnExport = false }) { Text("Cancel") } })
    vm.pendingBackup?.let { d ->
        val (clash, keyClash) = vm.backupClashes(d)
        AlertDialog(onDismissRequest = { vm.pendingBackup = null },
            title = { Text("Import backup?") },
            text = {
                Text("This backup has ${d.apps.size} app(s) and ${d.keys.size} keystore(s).\n\n" +
                    (if (clash > 0) "$clash app(s) already exist here and will be overwritten (name, package, version, stack).\n" else "") +
                    (if (keyClash > 0) "$keyClash stored keystore(s) will be replaced.\n" else "") +
                    (if (clash == 0 && keyClash == 0) "Nothing here is overwritten, the apps are added." else "\nThis cannot be undone."))
            },
            confirmButton = { TextButton({ vm.applyBackup(d) }) { Text("Import", color = if (clash > 0 || keyClash > 0) Red else Green) } },
            dismissButton = { TextButton({ vm.pendingBackup = null }) { Text("Cancel") } })
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Header("Settings")
        // v43: logins moved to their own page (Accounts)
        Panel {
            Text("Accounts", fontWeight = FontWeight.Bold)
            Mono("GitHub    ${vm.login.ifBlank { "not logged in" }}", color = if (vm.login.isBlank() || vm.sessionExpired) Amber else Green)
            Mono("Vercel    ${vm.vercelUser.ifBlank { "not connected" }}", color = if (vm.vercelUser.isBlank()) Muted else Green)
            Mono("Supabase  ${if (vm.supabaseOn) "connected" else "not connected"}", color = if (vm.supabaseOn) Green else Muted)
            Mono("Render    ${vm.renderUser.ifBlank { "not connected" }}", color = if (vm.renderUser.isBlank()) Muted else Green)
            Spacer(Modifier.height(8.dp))
            BigButton("Open accounts", Icons.Default.Login) { nav.navigate("accounts") { launchSingleTop = true } }
        }
        // ---- v27 feature 3: notifications ----
        Panel {
            Text("Notifications", fontWeight = FontWeight.Bold)
            Text("One when a build finishes (passed or failed, with the app name), one when an update APK has finished downloading, and one when a server stops answering or is back.", fontSize = 12.sp, color = Muted)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HubSwitch(vm.notifyOn, { on ->
                    vm.updateNotify(on)
                    if (on && android.os.Build.VERSION.SDK_INT >= 33 &&
                        ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                        askNotif.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                })
                Text("  Notify me", fontSize = 13.sp)
            }
            // v42
            Row(verticalAlignment = Alignment.CenterVertically) {
                HubSwitch(vm.serverAlerts, { vm.updateServerAlerts(it) })
                Text("  Server down / up alerts", fontSize = 13.sp)
            }
            Text("Checks every server address about every 15 minutes, even when App Hub is closed (Android picks the exact time). Needs Notify me on.", fontSize = 11.sp, color = Muted)
        }
        // ---- v27 feature 6: live sync ----
        Panel {
            Text("Live sync", fontWeight = FontWeight.Bold)
            Text("How often App Hub refreshes builds, releases and installs while it is open. Off still refreshes once when you open the app or tap Refresh.", fontSize = 12.sp, color = Muted)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3 to "Every 3 s", 10 to "Every 10 s", 0 to "Off").forEach { (v, l) ->
                    FilterChip(vm.syncSecs == v, { vm.updateSyncSecs(v) }, { Text(l, fontSize = 12.sp) })
                }
            }
        }
        Panel {
            Text("Background download", fontWeight = FontWeight.Bold)
            Text("Saves the newest APK of your apps about once an hour, even when App Hub is closed. Installing is still your tap.", fontSize = 12.sp, color = Muted)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { HubSwitch(vm.autoDownload, { vm.updateAutoDownload(it) }); Text("  Auto-download new builds", fontSize = 13.sp) }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { HubSwitch(vm.wifiOnly, { vm.updateWifiOnly(it) }); Text("  Wi-Fi only", fontSize = 13.sp) }
        }
        // ---- v27 feature 6: storage ----
        Panel {
            Text("Storage", fontWeight = FontWeight.Bold)
            Text("Saved APKs use ${fmtBytes(storage)}", fontSize = 13.sp, color = Cyan)
            Text("The latest build, the previous build (for rollback) and unfinished downloads of every app.", fontSize = 12.sp, color = Muted)
            Spacer(Modifier.height(8.dp))
            OutlinedButton({ confirmClear = true }, enabled = storage > 0) { Icon(Icons.Default.DeleteSweep, null, Modifier.size(16.dp)); Text(" Clear saved APKs", fontSize = 12.sp) }
        }
        // ---- v27 feature 2: backup ----
        Panel {
            Text("Backup", fontWeight = FontWeight.Bold)
            Text("Saves your app list (name, repo, package, version, stack) and the signing keystores to a JSON file you choose. The file contains signing keys.", fontSize = 12.sp, color = Muted)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ warnExport = true }, Modifier.weight(1f), contentPadding = BtnPad) { Icon(Icons.Default.Save, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); BtnText("Export backup") }
                OutlinedButton({ importFile.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }, Modifier.weight(1f), contentPadding = BtnPad) {
                    Icon(Icons.Default.FolderOpen, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); BtnText("Import backup")
                }
            }
        }
        Panel {
            Text("Apps", fontWeight = FontWeight.Bold)
            Text("QuestBlock, BgBrowser, App Hub and Videowall are added automatically. Bring back any you removed.", fontSize = 12.sp, color = Muted)
            Spacer(Modifier.height(8.dp))
            OutlinedButton({ vm.restorePresets() }) { Icon(Icons.Default.Restore, null, Modifier.size(16.dp)); Text(" Restore presets", fontSize = 12.sp) }
        }
        // v28: About, What's new, Crash log and Help moved to their own page so Settings stays short
        Panel {
            SettingRow(Icons.Default.Info, "About App Hub", "v$appVer", onClick = { nav.navigate("about") }) {
                Icon(Icons.Default.ChevronRight, null, tint = Muted)
            }
        }
        Text("App Hub $appVer", Modifier.fillMaxWidth().padding(vertical = 8.dp), color = Muted, fontSize = 12.sp)
    }
}

fun fmtBytes(b: Long): String = if (b < 1_048_576L) "${b / 1024} KB" else "%.1f MB".format(b / 1_048_576.0)

// ---------- add project ----------
@Composable fun AddScreen(vm: HubVM, nav: NavHostController) {
    // Nothing is filled in for you. Only an app name you carried over from the idea picker is kept.
    var name by remember { mutableStateOf(vm.draftName) }; var repo by remember { mutableStateOf("") }
    var pkg by remember { mutableStateOf("") }; var ver by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ProjectType.APP) }; var url by remember { mutableStateOf("") }   // v38: what kind of project (v39: may come from the Prompt Maker)
    var host by remember { mutableStateOf("") }; var db by remember { mutableStateOf(false) }   // v55: server: Host on (vercel | render) and Database (Supabase)
    var target by remember { mutableStateOf("") }   // v52: Deploy to: "", vercel, render or supabase (kept only while its account is connected)
    LaunchedEffect(Unit) { vm.draftName = "" }
    // v40: the three switches keep the position you left them in (saved on tap); Vercel stays off until you turn it on
    var create by remember { mutableStateOf(vm.prefs.setupCreate) }; var keys by remember { mutableStateOf(vm.prefs.setupKeys) }
    // v44: a switch only counts while its account is connected (the saved position is kept for when you log in)
    val githubUsable = vm.login.isNotBlank() && !vm.sessionExpired
    val createUsable = create && githubUsable
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Header("Setup project", nav)
        // v52: "Deploy to" replaces "Import from" (existing repos are still added from Accounts > Open ...)
        val toAccounts = { nav.navigate("accounts") { launchSingleTop = true }; Unit }
        val targets = listOf(Triple("GitHub Pages", "pages", githubUsable),
            Triple("Vercel", "vercel", vm.vercelUser.isNotBlank()), Triple("Render", "render", vm.renderUser.isNotBlank()), Triple("Supabase", "supabase", vm.supabaseOn))
        if (type != ProjectType.SERVER) Text("Deploy to", fontSize = 12.sp, color = Muted)
        if (type == ProjectType.SERVER) {
            // v55: a server is created with its repo: Host on Vercel or Render, and a Supabase database
            val hosts = listOf(Triple("Vercel", "vercel", vm.vercelUser.isNotBlank()), Triple("Render", "render", vm.renderUser.isNotBlank()))
            val hostOk = hosts.any { it.second == host && it.third }
            Text("Host on", fontSize = 12.sp, color = Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                hosts.forEach { (label, key, on) ->
                    val sel = host == key && on
                    FilterChip(sel, { if (!on) toAccounts() else host = if (host == key) "" else key },
                        { Text(if (on) label else "$label (log in)") },
                        leadingIcon = if (sel) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null)
                }
            }
            Text(when (if (hostOk) host else "") {
                "vercel" -> "Creates the Vercel project from the repo and fills in the endpoint address."
                "render" -> "Creates a free Render Node web service from the repo (npm install, npm start) and fills in the endpoint address. Render's GitHub app must be able to see the repo."
                else -> "Pick one to host it, or none to only push to GitHub."
            }, fontSize = 12.sp, color = Muted)
            Text("Database", fontSize = 12.sp, color = Muted)
            val dbSel = db && vm.supabaseOn
            FilterChip(dbSel, { if (!vm.supabaseOn) toAccounts() else db = !db },
                { Text(if (vm.supabaseOn) "Supabase" else "Supabase (log in)") },
                leadingIcon = if (dbSel) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null)
            Text(if (dbSel) "Creates a Supabase project in your first organization. If the server is hosted here too, SUPABASE_URL is set on it. The database password is random and not kept; add the keys from the Supabase dashboard." else "Optional. Creates a Supabase project for this server.",
                fontSize = 12.sp, color = Muted)
        } else if (type == ProjectType.APP) {
            FilterChip(true, {}, { Text("GitHub Releases") }, enabled = false, leadingIcon = { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) })
            Text("Apps build on GitHub Actions and the signed APK is released on GitHub. Vercel, Render and Supabase are for websites and servers.", fontSize = 12.sp, color = Muted)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                targets.forEach { (label, key, on) ->
                    val sel = target == key && on
                    FilterChip(sel, { if (!on) toAccounts() else target = if (target == key) "" else key },
                        { Text(if (on) label else "$label (log in)") },
                        leadingIcon = if (sel) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null)
                }
            }
            Text(when (if (targets.any { it.second == target && it.third }) target else "") {
                "pages" -> "Turns GitHub Pages on for the repo (source: GitHub Actions) so the deploy workflow in your zip can publish. A private repo needs a GitHub plan that allows Pages."
                "vercel" -> "Creates the Vercel project from the repo and fills in its address."
                "render" -> "Creates a free Render service from the repo: a static site for a website, a Node web service for a server. Render's GitHub app must be able to see the repo."
                "supabase" -> "Creates a Supabase project in your first organization and fills in its API address. The database password is random and not kept."
                else -> "Pick one to create it with the project, or none to only push to GitHub."
            }, fontSize = 12.sp, color = Muted)
        }
        TypePicker(type) { type = it }
        OutlinedTextField(name, { name = it }, label = { Text("${type.label} name") }, placeholder = { Text(when (type) { ProjectType.APP -> "e.g. My App"; ProjectType.WEBSITE -> "e.g. My Site"; ProjectType.SERVER -> "e.g. My API" }) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(repo, { repo = it }, label = { Text("Repo name (or owner/name)") }, placeholder = { Text("e.g. myapp") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (type == ProjectType.APP) OutlinedTextField(pkg, { pkg = it }, label = { Text("Package name (optional)") }, placeholder = { Text("found automatically if left empty") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        else OutlinedTextField(url, { url = it }, label = { Text(if (type == ProjectType.WEBSITE) "Live URL (optional)" else "Endpoint URL (optional)") },
            placeholder = { Text(if (type == ProjectType.WEBSITE) "found automatically for GitHub Pages" else "https://api.example.com/health") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(ver, { ver = it }, label = { Text("Version (optional)") }, placeholder = { Text("v1") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        // v61: back to the old way: the new repo is always private. Change it later in the app's Settings > Repository.
        ConnRow(Icons.Default.Code, "Create GitHub repo (private)",
            if (githubUsable) "Logged in as ${vm.login}" else if (vm.login.isNotBlank()) "Session expired. Tap to log in again" else "Not logged in. Tap to log in",
            if (githubUsable) Green else Amber, createUsable) {
            if (githubUsable) { create = !create; vm.prefs.setupCreate = create } else toAccounts()
        }
        if (type == ProjectType.APP) Row(verticalAlignment = Alignment.CenterVertically) { HubSwitch(keys, { keys = it; vm.prefs.setupKeys = it }); Text("  Generate signing keys + set secrets") }
        else Text(if (type == ProjectType.WEBSITE) "Your zip needs its own .github/workflows deploy. The status shows Deployed when that run passes."
            else "Your zip needs its own .github/workflows deploy. The status shows Running once the endpoint answers.", fontSize = 12.sp, color = Muted)
        BigButton("Create project", Icons.Default.PlayArrow) {
            if (name.isNotBlank() && repo.isNotBlank()) vm.setup(name.trim(), repo.trim(), pkg.trim(), ver.trim().ifBlank { "v1" }, createUsable, keys && type == ProjectType.APP, type, url,
                when (type) {
                    ProjectType.WEBSITE -> if (targets.none { it.second == target && it.third }) "" else target
                    ProjectType.SERVER -> if ((host == "vercel" && vm.vercelUser.isNotBlank()) || (host == "render" && vm.renderUser.isNotBlank())) host else ""
                    else -> ""
                }, type == ProjectType.SERVER && db && vm.supabaseOn, true) else vm.toast("Name and repo required")
        }
        if (vm.log.isNotEmpty()) {
            Panel { vm.log.forEach { Mono(it) } }
            OutlinedButton({ nav.navigate("deploy") { popUpTo("dash"); launchSingleTop = true } }) { Text("Go to Deploy") }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ---------- v27 polish: friendly empty state ----------
@Composable fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) =
    Column(modifier.fillMaxWidth().padding(vertical = 20.dp, horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).background(Line, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Cyan, modifier = Modifier.size(28.dp)) }
        Spacer(Modifier.height(10.dp))
        Text(title, fontWeight = FontWeight.Bold)
        Text(text, Modifier.padding(top = 2.dp), fontSize = 12.sp, color = Muted, textAlign = TextAlign.Center)
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }

// ---------- v27: "Zip name does not match" dialog ----------
/** Warning icon, the zip name, a side-by-side "named after / selected" comparison and three full-width buttons (no more wrapping labels). */
@Composable fun ZipMismatchDialog(zip: String, named: Project?, selected: Project, onNamed: () -> Unit, onSelected: () -> Unit, onCancel: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).background(Surf, shape).border(1.dp, Amber.copy(alpha = 0.6f), shape).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).background(Amber.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Warning, null, tint = Amber, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Wrong app?", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("The zip name does not match the selected app.", fontSize = 12.sp, color = Muted)
                }
            }
            Spacer(Modifier.height(14.dp))
            Mono(zip, Modifier.fillMaxWidth().background(Bg, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 8.dp), Cyan)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MismatchCard("Zip is named after", named?.name ?: "no app", named?.repo ?: "no matching app or repo", if (named != null) Green else Muted, Modifier.weight(1f))
                Icon(Icons.Default.SwapHoriz, null, tint = Muted, modifier = Modifier.padding(horizontal = 6.dp).size(18.dp))
                MismatchCard("Selected now", selected.name, selected.repo, Amber, Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            if (named != null) {
                Button(onNamed, Modifier.fillMaxWidth().height(46.dp), colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black)) {
                    Icon(Icons.Default.CloudUpload, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text("Push to ${named.name}", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onSelected, Modifier.fillMaxWidth().height(46.dp), border = BorderStroke(1.dp, Amber.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber)) {
                    Text("Push to ${selected.name}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Button(onSelected, Modifier.fillMaxWidth().height(46.dp), colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black)) {
                    Text("Push to ${selected.name}", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            TextButton(onCancel, Modifier.fillMaxWidth()) { Text("Cancel", color = Muted) }
        }
    }
}

/** v28: the zip is the same as or older than the version already on GitHub. */
/** v42: asked before a push when the zip holds something that looks like a secret. Back or tapping outside cancels. */
@Composable fun SecretDialog(a: SecretAsk, onAnyway: () -> Unit, onLeave: () -> Unit, onCancel: () -> Unit) {
    val whole = a.pack.findings.any { it.wholeFile }
    AlertDialog(onDismissRequest = onCancel,
        title = { Text("Possible secrets in this zip") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Once pushed, a secret stays in the repo history, even if the repo is private.", fontSize = 12.sp, color = Muted)
                a.pack.findings.take(6).forEach { Mono("${it.path}\n  ${it.why}", color = Amber) }
                if (a.pack.findings.size > 6) Text("+ ${a.pack.findings.size - 6} more", fontSize = 12.sp, color = Muted)
                if (whole) Text("Leave out & push skips the whole-file secrets. Secrets inside code files stay, remove them in the code.", fontSize = 11.sp, color = Muted)
            }
        },
        confirmButton = { TextButton(if (whole) onLeave else onAnyway) { Text(if (whole) "Leave out & push" else "Push anyway") } },
        dismissButton = { Row { TextButton(onCancel) { Text("Cancel") }; if (whole) TextButton(onAnyway) { Text("Push anyway") } } })
}

/** v49: file type check: the zip does not fit the project type, or has files that should not be pushed. */
@Composable fun TypeDialog(a: TypeAsk, onAnyway: () -> Unit, onLeave: () -> Unit, onCancel: () -> Unit) {
    val drop = TypeCheck.dropSet(a.issues, a.pack.entries).size
    AlertDialog(onDismissRequest = onCancel,
        title = { Text("Check these files") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${a.p.name} is a ${a.p.kind.label}.", fontSize = 12.sp, color = Muted)
                a.issues.take(6).forEach { Mono(if (it.path.isBlank()) it.why else "${it.path}\n  ${it.why}", color = Amber) }
                if (a.issues.size > 6) Text("+ ${a.issues.size - 6} more", fontSize = 12.sp, color = Muted)
                if (drop > 0) Text("Leave out & push skips $drop file(s).", fontSize = 11.sp, color = Muted)
            }
        },
        confirmButton = { TextButton(if (drop > 0) onLeave else onAnyway) { Text(if (drop > 0) "Leave out & push" else "Push anyway") } },
        dismissButton = { Row { TextButton(onCancel) { Text("Cancel") }; if (drop > 0) TextButton(onAnyway) { Text("Push anyway") } } })
}

/** v42: environment variables of a Vercel project. Names are listed, values are write-only and never stored in App Hub. */
@Composable fun EnvScreen(vm: HubVM, nav: NavHostController, id: Long) {
    val ps by vm.projects.collectAsState()
    val p = ps.firstOrNull { it.id == id }
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var del by remember { mutableStateOf<Vercel.Env?>(null) }
    LaunchedEffect(id) { vm.envVars.clear(); vm.envMsg = ""; ps.firstOrNull { it.id == id }?.let { vm.loadEnv(it) } }
    if (p == null) return
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Header("Environment variables")
        Text("${p.name} on Vercel. Values are encrypted by Vercel and never shown or kept in App Hub. A change applies from the next deployment.", fontSize = 12.sp, color = Muted)
        Panel {
            Text("Add or replace", fontWeight = FontWeight.Bold)
            OutlinedTextField(key, { key = it }, label = { Text("Name (for example DATABASE_URL)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value, { value = it }, label = { Text("Value") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            Text("An existing name gets the new value.", fontSize = 11.sp, color = Muted)
            if (vm.envMsg.isNotBlank()) Text(vm.envMsg, Modifier.padding(top = 4.dp), fontSize = 12.sp, color = Amber)
            Spacer(Modifier.height(8.dp))
            BigButton(if (vm.envBusy) "Working…" else "Save variable", Icons.Default.Save) {
                if (!vm.envBusy) vm.saveEnv(p, key, value) { key = ""; value = "" }
            }
        }
        Panel {
            Text("On Vercel (${vm.envVars.size})", fontWeight = FontWeight.Bold)
            if (vm.envVars.isEmpty()) Text("No variables yet.", fontSize = 12.sp, color = Muted)
            vm.envVars.forEach { e ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Mono(e.key, color = Cyan)
                        Mono(e.targets.joinToString(" • ").ifBlank { "-" }, color = Muted)
                    }
                    IconButton({ del = e }) { Icon(Icons.Default.Delete, "Delete ${e.key}", tint = Red) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    del?.let { e ->
        AlertDialog(onDismissRequest = { del = null },
            title = { Text("Delete ${e.key}?") },
            text = { Text("It is removed from Vercel for production, preview and development. Running deployments keep what they have until the next deployment.") },
            confirmButton = { TextButton({ del = null; vm.deleteEnv(p, e) }) { Text("Delete", color = Red) } },
            dismissButton = { TextButton({ del = null }) { Text("Cancel") } })
    }
}

@Composable fun OldVersionDialog(zip: String, zipVer: String, pushedVer: String, p: Project, onPush: () -> Unit, onCancel: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val same = zipVer.filter { it.isDigit() } == pushedVer.filter { it.isDigit() }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).background(Surf, shape).border(1.dp, Amber.copy(alpha = 0.6f), shape).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).background(Amber.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.History, null, tint = Amber, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(if (same) "Already pushed?" else "Older version?", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(if (same) "GitHub already has this version." else "This zip is older than what is on GitHub.", fontSize = 12.sp, color = Muted)
                }
            }
            Spacer(Modifier.height(14.dp))
            Mono(zip, Modifier.fillMaxWidth().background(Bg, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 8.dp), Cyan)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MismatchCard("Zip version", zipVer.ifBlank { "-" }, p.name, Amber, Modifier.weight(1f))
                Icon(Icons.Default.SwapHoriz, null, tint = Muted, modifier = Modifier.padding(horizontal = 6.dp).size(18.dp))
                MismatchCard("On GitHub now", pushedVer.ifBlank { "-" }, p.repo, Green, Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            Button(onCancel, Modifier.fillMaxWidth().height(46.dp), colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black)) {
                Text("Cancel", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onPush, Modifier.fillMaxWidth().height(46.dp), border = BorderStroke(1.dp, Amber.copy(alpha = 0.7f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber)) {
                Icon(Icons.Default.CloudUpload, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text("Push ${zipVer.ifBlank { "it" }}", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable private fun MismatchCard(label: String, name: String, repo: String, tone: Color, mod: Modifier) =
    Column(mod.background(tone.copy(alpha = 0.10f), RoundedCornerShape(12.dp)).border(1.dp, tone.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(10.dp)) {
        Text(label, fontSize = 10.sp, color = Muted, maxLines = 1)
        Text(name, fontWeight = FontWeight.Bold, color = tone, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(repo, fontSize = 10.sp, color = Muted, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
