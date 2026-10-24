@file:OptIn(ExperimentalLayoutApi::class)
package com.krishna.apphub

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

/**
 * v65: the two prompt tools, split by purpose.
 *  - AppPromptScreen (route aprompt/{id}, opened from a project's Prompt row): prompts for ONE existing project.
 *  - NewProjectScreen (the Prompt tab = "Prompt Maker"): only for a NEW app, website or server, one page at a time.
 */

/** A project's own prompt page: pick a task, write what you want, copy. The stack is the project's own (change it in its Settings). */
@Composable fun AppPromptScreen(vm: HubVM, nav: NavHostController, id: Long) {
    val p = vm.projects.collectAsState().value.firstOrNull { it.id == id } ?: return
    val tasks = Prompts.appTasks(p.kind)
    var task by rememberSaveable(id) { mutableStateOf(if (vm.task in tasks) vm.task else tasks.first()) }
    var note by rememberSaveable(id, task) { mutableStateOf("") }
    var deliver by remember { mutableStateOf(vm.prefs.promptDeliver) }
    val err = vm.errors[id] ?: p.lastError
    val adopt = task == "Adopt"
    val needsNote = !adopt && task != "Fix error"
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Header("${p.name} prompt", nav)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tasks.forEach { t -> FilterChip(task == t, { task = t }, { Text(t) }) }
        }
        if (adopt) Text("Makes this project work with App Hub: workflow, version, secrets and delivery. Send it to Claude with the project.", fontSize = 12.sp, color = Muted)
        else {
            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), minLines = 4, label = { Text(Prompts.hint(task, p.kind)) })
            if (task == "Fix error") Mono(if (err.isBlank()) "No build error on record." else "Build error included (${err.lines().count { it.isNotBlank() }} lines)", color = Muted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                HubSwitch(deliver, { deliver = it; vm.prefs.promptDeliver = it })
                Text("  Zip name and version rules", fontSize = 12.sp)
            }
        }
        BigButton("Copy prompt", Icons.Default.ContentCopy) {
            if (needsNote && note.isBlank()) vm.toast("Write what you want first")
            else vm.copy(Prompts.make(task, p, p.stack, err, deliver, note, vm.isVercel(p)))
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable private fun KindCard(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) =
    Panel(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(Line, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Green) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(sub, fontSize = 12.sp, color = Muted) }
            Icon(Icons.Default.ChevronRight, null, tint = Muted)
        }
    }

@Composable private fun IdeaRow(name: String, pitch: String, onClick: () -> Unit) =
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 6.dp)) {
        Text(name, color = Cyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(pitch, fontSize = 12.sp, color = Muted)
    }

/** Prompt Maker: a new app, website or server in up to 4 pages (type, idea, setup, ready). Apps skip the setup page. */
@Composable fun NewProjectScreen(vm: HubVM, nav: NavHostController) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var kindName by rememberSaveable { mutableStateOf("APP") }
    var cat by rememberSaveable { mutableStateOf(Ideas.cats.first()) }
    var name by rememberSaveable { mutableStateOf("") }
    var pitch by rememberSaveable { mutableStateOf("") }
    var stack by rememberSaveable { mutableStateOf("") }
    var host by rememberSaveable { mutableStateOf("") }
    val kind = ProjectType.valueOf(kindName)
    val app = kind == ProjectType.APP
    val stacks = Prompts.stacksFor(kind)
    val hosts = Prompts.hostsFor(kind)
    val curStack = if (stack in stacks) stack else stacks.firstOrNull().orEmpty()
    val curHost = if (host in hosts) host else hosts.firstOrNull().orEmpty()
    fun next() { step = if (step == 1 && app) 3 else step + 1 }
    fun back() { step = if (step == 3 && app) 1 else step - 1 }
    BackHandler(step > 0) { back() }
    val total = if (app) 3 else 4
    val pos = when (step) { 0 -> 1; 1 -> 2; 2 -> 3; else -> total }
    val title = listOf("New project", "Idea", "Setup", "Ready")[step]
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) IconButton({ back() }, Modifier.size(40.dp)) { Icon(Icons.Default.ArrowBack, null) }
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Green, maxLines = 1)
            Mono("$pos/$total", color = Muted)
        }
        LinearProgressIndicator({ pos / total.toFloat() }, Modifier.fillMaxWidth().height(3.dp), color = Green, trackColor = Line)
        when (step) {
            0 -> {
                KindCard(Icons.Default.Apps, "App", "Android app: Kotlin, Compose, signed APK") { kindName = "APP"; step = 1 }
                KindCard(Icons.Default.Language, "Website", "Site on GitHub Pages or Vercel") { kindName = "WEBSITE"; step = 1 }
                KindCard(Icons.Default.Dns, "Server", "API or bot backend with a health check") { kindName = "SERVER"; step = 1 }
                Text("This is for something new. For an app you already have, open it and use its Prompt.", fontSize = 12.sp, color = Muted)
            }
            1 -> {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Name") })
                OutlinedTextField(pitch, { pitch = it }, Modifier.fillMaxWidth(), minLines = 3, label = { Text("What is it?") })
                Text("Ideas", color = Muted, fontSize = 12.sp)
                if (app) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Ideas.cats.forEach { c -> FilterChip(cat == c, { cat = c }, { Text(c) }) } }
                val ideas = when (kind) { ProjectType.APP -> Ideas.list[cat].orEmpty(); ProjectType.WEBSITE -> Ideas.web; ProjectType.SERVER -> Ideas.server }
                ideas.forEach { (n, d) -> IdeaRow(n, d) { name = n; pitch = d } }
                OutlinedButton({ vm.copy(Prompts.ideasPrompt(cat, pitch, kind)) }, Modifier.fillMaxWidth()) { BtnText("Not sure? Copy an ideas prompt") }
                Button({ next() }, Modifier.fillMaxWidth(), enabled = name.isNotBlank() && pitch.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = androidx.compose.ui.graphics.Color.Black)) { Text("Next") }
            }
            2 -> {
                Text("Stack", color = Muted, fontSize = 12.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { stacks.forEach { s -> FilterChip(curStack == s, { stack = s }, { Text(s) }) } }
                Text("Host", color = Muted, fontSize = 12.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { hosts.forEach { h -> FilterChip(curHost == h, { host = h }, { Text(h) }) } }
                Button({ next() }, Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = androidx.compose.ui.graphics.Color.Black)) { Text("Next") }
            }
            else -> {
                Panel {
                    Mono("type : ${kind.label.lowercase()}")
                    Mono("name : ${name.trim()}")
                    Mono("stack: ${if (app) "Kotlin, Compose, Room" else curStack}")
                    if (!app) Mono("host : $curHost")
                }
                BigButton("Copy prompt", Icons.Default.ContentCopy) {
                    vm.copy(if (app) Prompts.buildPrompt(name.trim(), pitch.trim()) else Prompts.newProject(kind, name.trim(), pitch.trim(), curStack, curHost))
                }
                Text("Send it to Claude. When the zip is ready, add it here.", fontSize = 12.sp, color = Muted)
                OutlinedButton({ nav.navigate("add") }, Modifier.fillMaxWidth()) { BtnText("Set up the project") }
                TextButton({ step = 0; name = ""; pitch = ""; stack = ""; host = "" }) { Text("Start over", color = Muted) }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
