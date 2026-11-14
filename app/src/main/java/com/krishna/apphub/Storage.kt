package com.krishna.apphub

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

/**
 * v87: Settings > Storage. A private repo on the free GitHub plan has 500 MB of Actions storage, and every build's APK artifact counts
 * (release assets do not). A full quota stops builds, which is why repos get switched to public. This page shows the artifacts of each repo,
 * deletes them (one repo or all) and can make build.yml keep them 1 day. The APK itself stays in the release.
 */
private const val FREE_MB = 500.0

private val PadB = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
private fun mb(bytes: Long) = "%.1f".format(bytes / 1048576.0)

@Composable fun StorageScreen(vm: HubVM, nav: NavHostController) {
    val ps = vm.projects.collectAsState().value.filter { it.repo.contains("/") }
    LaunchedEffect(ps.size) { vm.storMsg = ""; vm.loadStorage() }
    val priv = ps.mapNotNull { p -> vm.stor[p.id]?.takeIf { it.priv == true } }
    val usedMb = priv.sumOf { it.bytes } / 1048576.0
    val total = ps.sumOf { vm.stor[it.id]?.count ?: 0 }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Header("Storage", nav) {
            IconButton({ vm.loadStorage() }, Modifier.size(40.dp)) { Icon(Icons.Default.Refresh, null, tint = Muted) }
        }
        Panel {
            Text("Private repos", fontWeight = FontWeight.Bold)
            Text("${"%.1f".format(usedMb)} MB of ${FREE_MB.toInt()} MB (free plan)", fontSize = 13.sp, color = if (usedMb > FREE_MB * 0.8) Amber else Green)
            LinearProgressIndicator({ (usedMb / FREE_MB).toFloat().coerceIn(0f, 1f) }, Modifier.fillMaxWidth().padding(top = 6.dp),
                color = if (usedMb > FREE_MB * 0.8) Amber else Green, gapSize = 0.dp, drawStopIndicator = {})
            Text("Build artifacts count against this. When it is full, builds in private repos fail. Public repos have no limit. The APK stays in the release, so artifacts can go.",
                Modifier.padding(top = 6.dp), fontSize = 11.sp, color = Muted)
            if (vm.storBusy) Text("Working…", Modifier.padding(top = 4.dp), fontSize = 12.sp, color = Cyan)
            else if (vm.storMsg.isNotBlank()) Text(vm.storMsg, Modifier.padding(top = 4.dp), fontSize = 12.sp, color = Amber)
        }
        if (total > 0) BigButton("Delete all artifacts ($total)", Icons.Default.DeleteSweep) { if (!vm.storBusy) vm.cleanAllArtifacts() }
        ps.forEach { p ->
            val s = vm.stor[p.id]
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontWeight = FontWeight.Bold, color = Cyan)
                        Text(if (s == null) "Loading…" else "${s.count} artifact(s) • ${mb(s.bytes)} MB", fontSize = 12.sp, color = Muted)
                    }
                    if (s?.priv != null) Pill(if (s.priv) "Private" else "Public", if (s.priv) Green else Muted)
                }
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ vm.cleanArtifacts(p) }, Modifier.weight(1f), enabled = !vm.storBusy && (s?.count ?: 0) > 0, contentPadding = PadB) { BtnText("Delete") }
                    if (p.kind == ProjectType.APP) OutlinedButton({ vm.shortenRetention(p) }, Modifier.weight(1f), enabled = !vm.storBusy, contentPadding = PadB) { BtnText("Keep 1 day") }
                }
            }
        }
        if (ps.isEmpty()) EmptyState(Icons.Default.Storage, "No repos yet", "Add a project with a GitHub repo first.") {}
        Spacer(Modifier.height(12.dp))
    }
}
