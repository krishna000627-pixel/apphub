package com.krishna.apphub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

/**
 * v46: provider pages (GitHub, Vercel, Supabase; Render is in RenderScreen.kt). Each lists what the account has and
 * adds it as a project in one tap, no setup form. Only the type is chosen: Vercel starts on Website, Render and
 * Supabase on Server, GitHub has none selected (you must pick).
 */

/** "https://github.com/owner/name.git" -> "owner/name". Anything that is not a GitHub address gives "". */
fun repoOf(url: String): String {
    val u = url.trim().removeSuffix("/").removeSuffix(".git")
    return if (u.startsWith("https://github.com/")) u.removePrefix("https://github.com/") else if (Regex("^[\\w.-]+/[\\w.-]+$").matches(u)) u else ""
}

/** One row of a provider page: title, status pill, type chips and Open / Add. [defType] null = nothing selected, Add stays off until you pick. */
@Composable fun ImportCard(
    icon: ImageVector, title: String, sub: String, subColor: Color = Muted, pill: Pair<String, Color>? = null,
    added: Boolean, defType: ProjectType?, canAdd: Boolean = true, note: String = "",
    onOpen: () -> Unit, onAdd: (ProjectType) -> Unit
) {
    var type by remember(title) { mutableStateOf(defType) }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(Line, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(18.dp), tint = Cyan)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sub.isNotBlank()) Text(sub, fontSize = 12.sp, color = subColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (added) Pill("Added", Green) else pill?.let { Pill(it.first, it.second) }
        }
        if (note.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text(note, fontSize = 11.sp, color = Amber) }
        Spacer(Modifier.height(8.dp))
        if (!added) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProjectType.values().forEach { t -> FilterChip(type == t, { type = t }, { Text(t.label, maxLines = 1, softWrap = false) }) }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onOpen, Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) { BtnText("Open") }
            if (!added) Button({ type?.let { onAdd(it) } }, Modifier.weight(1f), enabled = canAdd && type != null,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black)) { BtnText(if (type == null) "Pick type" else "Add") }
        }
    }
}

/** Account header card of a provider page: icon, name, connected line, link to the web dashboard. */
@Composable private fun AccountCard(icon: ImageVector, who: String, dash: String, stats: @Composable RowScope.() -> Unit) {
    val ctx = LocalContext.current
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Cyan, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(who, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Connected", fontSize = 12.sp, color = Green)
            }
            TextButton({ openUrl(ctx, dash) }) { BtnText("Dashboard") }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = stats)
    }
}

@Composable private fun NotConnected(what: String, nav: NavHostController) = Panel {
    Text("Not connected", color = Amber, fontWeight = FontWeight.Bold)
    Text("Log in to $what in Accounts.", fontSize = 12.sp, color = Muted)
    Spacer(Modifier.height(8.dp))
    BigButton("Accounts", Icons.Default.Login) { nav.navigate("accounts") { launchSingleTop = true } }
}

@Composable private fun Refresh(loading: Boolean, onClick: () -> Unit) =
    if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Green)
    else IconButton(onClick, Modifier.size(40.dp)) { Icon(Icons.Default.Refresh, null) }

// ---------- GitHub ----------
@Composable fun GitHubScreen(vm: HubVM, nav: NavHostController) {
    val ctx = LocalContext.current
    val ps by vm.projects.collectAsState()
    var q by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.loadRepos() }
    val on = vm.login.isNotBlank() && !vm.sessionExpired
    val shown = vm.ghRepos.filter { q.isBlank() || it.full.contains(q.trim(), true) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Header("GitHub", nav) { Refresh(vm.ghLoading) { vm.loadRepos() } }
        if (!on) NotConnected("GitHub", nav) else {
            AccountCard(Icons.Default.Code, vm.login, "https://github.com/${vm.login}") {
                Stat("Repos", vm.ghRepos.size, Cyan, Modifier.weight(1f))
                Stat("Private", vm.ghRepos.count { it.priv }, Amber, Modifier.weight(1f))
                Stat("Added", vm.ghRepos.count { r -> ps.any { it.repo.equals(r.full, true) } }, Green, Modifier.weight(1f))
            }
            if (vm.ghMsg.isNotBlank()) Text(vm.ghMsg, fontSize = 12.sp, color = Amber)
            OutlinedTextField(q, { q = it }, label = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            shown.take(40).forEach { r ->
                ImportCard(Icons.Default.Code, r.name, listOf(if (r.priv) "private" else "public", r.lang).filter { it.isNotBlank() }.joinToString(" • "),
                    added = ps.any { it.repo.equals(r.full, true) }, defType = null,
                    onOpen = { openUrl(ctx, "https://github.com/${r.full}") }) { t -> vm.importProject(r.name, r.full, t) }
            }
            if (shown.size > 40) Text("Showing 40 of ${shown.size}. Search to narrow.", fontSize = 12.sp, color = Muted)
            if (shown.isEmpty() && !vm.ghLoading) Panel { Text("No repos", color = Muted) }
        }
        Spacer(Modifier.height(12.dp))
    }
}

// ---------- Vercel ----------
private fun vercelLook(st: String): Pair<String, Color>? = when (st) {
    "READY" -> "Live" to Green
    "BUILDING", "QUEUED", "INITIALIZING" -> "Building" to Cyan
    "ERROR" -> "Failed" to Red
    "CANCELED" -> "Canceled" to Muted
    else -> null
}

@Composable fun VercelScreen(vm: HubVM, nav: NavHostController) {
    val ctx = LocalContext.current
    val ps by vm.projects.collectAsState()
    LaunchedEffect(Unit) { vm.loadVercelProjects() }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Header("Vercel", nav) { Refresh(vm.vcLoading) { vm.loadVercelProjects() } }
        if (vm.vercelUser.isBlank()) NotConnected("Vercel", nav) else {
            AccountCard(Icons.Default.Public, vm.vercelUser, "https://vercel.com/dashboard") {
                Stat("Projects", vm.vcProjects.size, Cyan, Modifier.weight(1f))
                Stat("Live", vm.vcProjects.count { it.state == "READY" }, Green, Modifier.weight(1f))
                Stat("Added", vm.vcProjects.count { v -> ps.any { it.repo.equals(v.repo, true) } }, Green, Modifier.weight(1f))
            }
            if (vm.vcMsg.isNotBlank()) Text(vm.vcMsg, fontSize = 12.sp, color = Amber)
            vm.vcProjects.forEach { v ->
                ImportCard(Icons.Default.Public, v.name, v.url.removePrefix("https://"), Cyan, vercelLook(v.state),
                    added = v.repo.isNotBlank() && ps.any { it.repo.equals(v.repo, true) }, defType = ProjectType.WEBSITE,
                    canAdd = v.repo.isNotBlank(), note = if (v.repo.isBlank()) "No GitHub repo linked" else "",
                    onOpen = { openUrl(ctx, v.url) }) { t -> vm.importProject(v.name, v.repo, t, v.url, v.id) }
            }
            if (vm.vcProjects.isEmpty() && !vm.vcLoading) Panel { Text("No projects", color = Muted) }
        }
        Spacer(Modifier.height(12.dp))
    }
}

// ---------- Supabase ----------
@Composable fun SupabaseScreen(vm: HubVM, nav: NavHostController) {
    val ctx = LocalContext.current
    val ps by vm.projects.collectAsState()
    // Supabase projects carry no repo: use the GitHub repo with the same name, or ask for one
    var ask by remember { mutableStateOf<Pair<SupabaseApi.Proj, ProjectType>?>(null) }
    var askRepo by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.loadSupabase(); vm.loadRepos() }
    fun slug(n: String) = n.lowercase().replace(Regex("[^a-z0-9]+"), "")
    fun match(pr: SupabaseApi.Proj) = vm.ghRepos.firstOrNull { slug(it.name) == slug(pr.name) }?.full.orEmpty()
    ask?.let { (pr, t) ->
        AlertDialog(onDismissRequest = { ask = null },
            title = { Text("Which repo?") },
            text = {
                Column {
                    Text("No GitHub repo is named like ${pr.name}.", fontSize = 12.sp, color = Muted)
                    OutlinedTextField(askRepo, { askRepo = it }, label = { Text("owner/name") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            },
            confirmButton = { TextButton({
                val r = repoOf(askRepo)
                if (r.isBlank()) vm.toast("Use owner/name") else { vm.importProject(pr.name, r, t, "https://${pr.id}.supabase.co"); ask = null }
            }) { Text("Add", color = Green) } },
            dismissButton = { TextButton({ ask = null }) { Text("Cancel") } })
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Header("Supabase", nav) { Refresh(vm.supabaseLoading) { vm.loadSupabase() } }
        if (!vm.supabaseOn) NotConnected("Supabase", nav) else {
            AccountCard(Icons.Default.Storage, "Supabase", "https://supabase.com/dashboard") {
                Stat("Projects", vm.supabaseProjects.size, Cyan, Modifier.weight(1f))
                Stat("Active", vm.supabaseProjects.count { it.status.startsWith("ACTIVE") }, Green, Modifier.weight(1f))
                Stat("Paused", vm.supabaseProjects.count { !it.status.startsWith("ACTIVE") }, if (vm.supabaseProjects.any { !it.status.startsWith("ACTIVE") }) Amber else Muted, Modifier.weight(1f))
            }
            if (vm.supabaseMsg.isNotBlank()) Text(vm.supabaseMsg, fontSize = 12.sp, color = Amber)
            vm.supabaseProjects.forEach { pr ->
                val repo = match(pr)
                val ok = pr.status.startsWith("ACTIVE")
                ImportCard(Icons.Default.Storage, pr.name, "${pr.status.lowercase().replace('_', ' ')} • ${pr.region}", if (ok) Green else Amber,
                    added = repo.isNotBlank() && ps.any { it.repo.equals(repo, true) }, defType = ProjectType.SERVER,
                    onOpen = { openUrl(ctx, "https://supabase.com/dashboard/project/${pr.id}") }) { t ->
                    if (repo.isNotBlank()) vm.importProject(pr.name, repo, t, "https://${pr.id}.supabase.co")
                    else { askRepo = if (vm.login.isNotBlank()) "${vm.login}/" else ""; ask = pr to t }
                }
            }
            if (vm.supabaseProjects.isEmpty() && !vm.supabaseLoading) Panel { Text("No projects", color = Muted) }
        }
        Spacer(Modifier.height(12.dp))
    }
}
