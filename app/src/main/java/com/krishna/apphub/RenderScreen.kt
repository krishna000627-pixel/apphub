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
import kotlinx.coroutines.delay

/** v45: Render on its own page: summary, one card per service (last deploy, open, deploy). */
@Composable fun RenderScreen(vm: HubVM, nav: NavHostController) {
    val ctx = LocalContext.current
    // refresh on open, then every 20 s while the page is open (silent)
    LaunchedEffect(Unit) { while (true) { vm.refreshRender(); delay(20_000) } }
    var q by remember { mutableStateOf("") }
    val all = vm.renderServices
    val list = all.filter { q.isBlank() || it.name.contains(q.trim(), true) || it.repo.contains(q.trim(), true) }
    val live = all.count { !it.suspended }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchHeader("Render", nav, vm.renderLoading, q, { q = it }) { vm.refreshRender() }
        if (vm.renderUser.isBlank()) {
            Panel {
                Text("Not connected", color = Amber, fontWeight = FontWeight.Bold)
                Text("Add your Render API key in Accounts.", fontSize = 12.sp, color = Muted)
                Spacer(Modifier.height(8.dp))
                BigButton("Accounts", Icons.Default.Login) { nav.navigate("accounts") { launchSingleTop = true } }
            }
        } else {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Cloud, null, tint = Cyan, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(vm.renderUser, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Connected", fontSize = 12.sp, color = Green)
                    }
                    TextButton({ openUrl(ctx, "https://dashboard.render.com") }) { BtnText("Dashboard") }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("Total", all.size, Cyan, Modifier.weight(1f))
                    Stat("Active", live, Green, Modifier.weight(1f))
                    Stat("Off", all.size - live, if (all.size - live > 0) Amber else Muted, Modifier.weight(1f))
                }
            }
            if (vm.renderMsg.isNotBlank()) Text(vm.renderMsg, fontSize = 12.sp, color = Amber)
            if (list.isEmpty() && !vm.renderLoading) Panel { Text(if (q.isBlank()) "No services yet" else "No match", color = Muted) }
            list.forEach { sv -> RenderCard(vm, sv) { openUrl(ctx, it) } }
        }
        Spacer(Modifier.height(12.dp))
    }
}

private fun kindIcon(kind: String): ImageVector = when (kind) {
    "static_site" -> Icons.Default.Language
    "background_worker" -> Icons.Default.Memory
    "cron_job" -> Icons.Default.Schedule
    "private_service" -> Icons.Default.Lock
    else -> Icons.Default.Dns
}

private fun kindLabel(kind: String) = when (kind) {
    "web_service" -> "Web"; "static_site" -> "Static"; "background_worker" -> "Worker"
    "private_service" -> "Private"; "cron_job" -> "Cron"; else -> kind.replace('_', ' ')
}

/** Deploy status word + colour. */
private fun deployLook(st: String): Pair<String, Color> = when (st) {
    "live" -> "Live" to Green
    "build_in_progress", "update_in_progress", "created" -> "Building" to Cyan
    "pre_deploy_in_progress" -> "Starting" to Cyan
    "build_failed", "update_failed", "pre_deploy_failed" -> "Failed" to Red
    "canceled" -> "Canceled" to Muted
    "deactivated" -> "Old" to Muted
    else -> "" to Muted
}

@Composable private fun RenderCard(vm: HubVM, sv: RenderApi.Svc, open: (String) -> Unit) {
    val ps by vm.projects.collectAsState()
    val (dl, dc) = deployLook(vm.renderDeploys[sv.id].orEmpty())
    var type by remember(sv.id) { mutableStateOf<ProjectType?>(ProjectType.SERVER) }   // Render starts on Server
    val added = sv.repo.isNotBlank() && ps.any { it.repo.equals(sv.repo, true) }
    Panel(border = if (dl == "Failed") Red else Line) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(Line, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Icon(kindIcon(sv.kind), null, Modifier.size(18.dp), tint = Cyan)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(sv.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(kindLabel(sv.kind) + " • " + if (sv.suspended) "suspended" else "active", fontSize = 12.sp, color = if (sv.suspended) Amber else Muted)
            }
            if (added) Pill("Added", Green) else if (dl.isNotBlank()) Pill(dl, dc)
        }
        if (sv.url.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Mono(sv.url.removePrefix("https://"), Modifier.clickable { open(sv.url) }, Cyan)
        }
        if (sv.repo.isBlank()) { Spacer(Modifier.height(4.dp)); Text("No GitHub repo linked", fontSize = 11.sp, color = Amber) }
        if (!added && sv.repo.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProjectType.values().forEach { t -> FilterChip(type == t, { type = t }, { Text(t.label, maxLines = 1, softWrap = false) }) }
            }
        }
        Spacer(Modifier.height(8.dp))
        val pad = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ open(sv.dashboardUrl) }, Modifier.weight(1f), contentPadding = pad) { BtnText("Open") }
            if (!sv.suspended) OutlinedButton({ vm.renderDeploy(sv) }, Modifier.weight(1f), contentPadding = pad) { BtnText("Deploy") }
            if (!added && sv.repo.isNotBlank()) Button({ type?.let { vm.importProject(sv.name, sv.repo, it, sv.url) } }, Modifier.weight(1f), contentPadding = pad,
                colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black)) { BtnText("Add") }
        }
    }
}
