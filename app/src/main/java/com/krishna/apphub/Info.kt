@file:OptIn(ExperimentalLayoutApi::class)

package com.krishna.apphub

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

/** v34: the "installed" line shared by the short overview and the details page: text + colour. */
fun installedLine(vm: HubVM, p: Project): Pair<String, Color> {
    val inst = vm.installedTag(p.id)
    val phone = vm.isInstalled(p)
    val text = when {
        phone == false -> "no (not on this phone)"
        phone == true -> "yes, ${vm.onPhone[p.id] ?: "?"}" + if (inst.isNotBlank()) " ($inst)" else ""
        inst.isNotBlank() -> inst
        else -> "unknown"
    }
    return text to (if (phone == false) Amber else Color.Unspecified)
}

/** One label/value line of an info card. [copy] makes the line tappable (copies the value). */
private class IR(val k: String, val v: String, val c: Color = Color.Unspecified, val copy: Boolean = false, val mono: Boolean = false)

/** A titled card: label on the left, value on the right of it (wraps instead of being cut), thin dividers between lines. */
@Composable private fun InfoCard(title: String, vm: HubVM, rows: List<IR?>, footer: @Composable ColumnScope.() -> Unit = {}) {
    val list = rows.filterNotNull()
    Column(Modifier.fillMaxWidth().background(Surf, RoundedCornerShape(16.dp)).border(1.dp, Line, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(title.uppercase(), fontSize = 11.sp, color = Muted, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 2.dp, bottom = 2.dp))
        list.forEachIndexed { i, r ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Line.copy(alpha = 0.6f)))
            Row(Modifier.fillMaxWidth().let { if (r.copy) it.clickable { vm.copy(r.v) } else it }.padding(vertical = 11.dp), verticalAlignment = Alignment.Top) {
                Text(r.k, Modifier.width(96.dp), fontSize = 13.sp, color = Muted)
                Text(r.v, Modifier.weight(1f), fontSize = 13.sp, color = r.c, fontFamily = if (r.mono) FontFamily.Monospace else FontFamily.Default)
                if (r.copy) { Spacer(Modifier.width(8.dp)); Icon(Icons.Default.ContentCopy, null, Modifier.size(14.dp).padding(top = 2.dp), tint = Muted) }
            }
        }
        footer()
    }
}

/** v34: everything about the app on one page (opened by tapping the overview box). v37: hero card, grouped cards, tap to copy. */
@Composable fun InfoScreen(vm: HubVM, nav: NavHostController, id: Long) {
    val p = vm.projects.collectAsState().value.firstOrNull { it.id == id } ?: return
    val ctx = LocalContext.current
    LaunchedEffect(id) { vm.checkInstalls(); vm.loadDetails(p) }
    val i = vm.infos[id]
    val (instText, instColor) = installedLine(vm, p)
    val phone = vm.isInstalled(p)
    val upd = vm.hasUpdate(p)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Header("Info", nav) {
                IconButton({ openUrl(ctx, "https://github.com/${p.repo}") }, Modifier.size(40.dp)) { Icon(Icons.Default.OpenInNew, null, tint = Muted) }
            }
        }
        // hero: icon, name, version, quick status pills
        item {
            Column(Modifier.fillMaxWidth().background(Surf, RoundedCornerShape(16.dp)).border(1.dp, Line, RoundedCornerShape(16.dp)).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).background(Line, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Icon(p.kind.icon(), null, Modifier.size(30.dp), tint = Cyan) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(p.stack, fontSize = 12.sp, color = Muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    Text(p.version, fontSize = 26.sp, color = Cyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val st = vm.stage(p)
                    Pill(st.pill(), st.color())
                    if (p.kind == ProjectType.APP) {
                        when (phone) {
                            true -> Pill("● ${vm.onPhone[id]?.ifBlank { null } ?: "On phone"}", Green)
                            false -> Pill("○ Not installed", Muted)
                            null -> {}
                        }
                        if (upd) Pill(if (phone == false) "Install" else "Update", Amber)
                    } else {
                        Pill(p.kind.label, Muted)
                        hostOf(p.liveUrl)?.let { Pill("↗ $it", Cyan) }
                    }
                }
            }
        }
        item {
            InfoCard(p.kind.label, vm, listOf(
                if (p.kind == ProjectType.APP) IR("Package", p.packageName.ifBlank { "unknown" }, copy = p.packageName.isNotBlank(), mono = true)
                else IR("Live URL", p.liveUrl.ifBlank { "not set" }, if (p.liveUrl.isBlank()) Muted else Color.Unspecified, copy = p.liveUrl.isNotBlank(), mono = true),
                IR("Repo", p.repo, copy = true, mono = true),
                IR("Stack", p.stack)
            ))
        }
        item {
            InfoCard("Build", vm, listOf(
                vm.stage(p).let { IR("Status", it.label, it.color()) },
                if (p.kind == ProjectType.APP) IR("Installed", instText, instColor) else null,
                if (p.kind == ProjectType.APP) IR("Latest", vm.latest[id] ?: "-", mono = true) else null,
                if (p.kind != ProjectType.APP) null
                else if (vm.waitingInstall(p)) IR("Saved APK", "${vm.cachedTag(id)} (not installed yet)", Amber, mono = true)
                else if (vm.hasCached(p)) IR("Saved APK", vm.cachedTag(id), mono = true) else null
            ))
        }
        item {
            if (i == null) InfoCard("Repository", vm, listOf(IR("", "Loading repo info…", Muted)))
            else InfoCard("Repository", vm, listOf(
                if (i.desc.isNotBlank()) IR("About", i.desc) else null,
                IR("Language", i.lang.ifBlank { "-" }),
                IR("Branch", i.branch, mono = true),
                IR("Size", "%.1f MB".format(i.sizeKb / 1024.0)),
                IR("Issues", "${i.issues}"),
                IR("Stars", "${i.stars}"),
                IR("Access", if (i.priv) "Private" else "Public"),
                IR("Pushed", ago(i.pushed))
            ))
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
