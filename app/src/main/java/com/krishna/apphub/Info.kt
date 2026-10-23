package com.krishna.apphub

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

/** v34: everything about the app on one page (opened by tapping the overview box). */
@Composable fun InfoScreen(vm: HubVM, nav: NavHostController, id: Long) {
    val p = vm.projects.collectAsState().value.firstOrNull { it.id == id } ?: return
    val ctx = LocalContext.current
    LaunchedEffect(id) { vm.checkInstalls(); vm.loadDetails(p) }
    val i = vm.infos[id]
    val (instText, instColor) = installedLine(vm, p)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Header("${p.name} info", nav) {
                IconButton({ openUrl(ctx, "https://github.com/${p.repo}") }, Modifier.size(40.dp)) { Icon(Icons.Default.OpenInNew, null, tint = Muted) }
            }
        }
        item {
            Panel {
                Text("App", color = Muted, fontSize = 12.sp); Spacer(Modifier.height(4.dp))
                Mono("name     : ${p.name}")
                Mono("package  : ${p.packageName.ifBlank { "unknown" }}")
                Mono("version  : ${p.version}")
                Mono("stack    : ${p.stack}")
                Mono("repo     : ${p.repo}")
                Mono("status   : ${p.lastStatus}", color = statusColor(p.lastStatus))
                Mono("installed: $instText", color = instColor)
                Mono("latest   : ${vm.latest[id] ?: "-"}")
                if (vm.waitingInstall(p)) Mono("saved apk: ${vm.cachedTag(id)} (not installed yet)", color = Amber)
                else if (vm.hasCached(p)) Mono("saved apk: ${vm.cachedTag(id)}")
            }
        }
        item {
            Panel {
                Text("Repository", color = Muted, fontSize = 12.sp); Spacer(Modifier.height(4.dp))
                if (i == null) Text("Loading repo info…", color = Muted) else {
                    if (i.desc.isNotBlank()) { Text(i.desc, fontSize = 13.sp); Spacer(Modifier.height(6.dp)) }
                    Mono("language : ${i.lang.ifBlank { "-" }}"); Mono("branch   : ${i.branch}")
                    Mono("size     : ${"%.1f".format(i.sizeKb / 1024.0)} MB"); Mono("issues   : ${i.issues}   stars: ${i.stars}")
                    Mono("access   : ${if (i.priv) "private" else "public"}"); Mono("pushed   : ${ago(i.pushed)}")
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
