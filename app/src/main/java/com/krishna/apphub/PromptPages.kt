@file:OptIn(ExperimentalLayoutApi::class)
package com.krishna.apphub

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.ui.graphics.Color
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
    val p = projectOrHome(vm, nav, id) ?: return
    val tasks = Prompts.appTasks(p.kind)
    var task by rememberSaveable(id) { mutableStateOf(if (vm.task in tasks) vm.task else tasks.first()) }
    var note by rememberSaveable(id, task) { mutableStateOf("") }
    var deliver by remember { mutableStateOf(vm.prefs.promptDeliver) }
    var partial by remember { mutableStateOf(vm.prefs.promptPartial) }
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
                Text("  Zip name, version and whatsnew.txt", fontSize = 12.sp)
            }
            if (deliver) Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HubSwitch(partial, { partial = it; vm.prefs.promptPartial = it })
                    Text("  Partial zip (only changed files)", fontSize = 12.sp)
                }
                Text(if (partial) "Claude sends only what it changed plus partial.txt. App Hub checks it before the push and applies its delete lines." else "Claude sends the whole project every time.", Modifier.padding(start = 52.dp), fontSize = 11.sp, color = Muted)
            }
        }
        BigButton("Copy prompt", Icons.Default.ContentCopy) {
            if (needsNote && note.isBlank()) vm.toast("Write what you want first")
            else vm.copy(Prompts.make(task, p, p.stack, err, deliver, note, vm.isVercel(p), partial && deliver))
        }
        Spacer(Modifier.height(12.dp))
    }
}

private val CardShape = RoundedCornerShape(14.dp)

@Composable private fun Tag(text: String) =
    Text(text, fontSize = 11.sp, color = Muted, modifier = Modifier.background(Line, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))

/** Top of page 1: App / Website / Server as one segmented control (no page of its own). */
@Composable private fun TypeSegments(kind: ProjectType, onPick: (ProjectType) -> Unit) =
    Row(Modifier.fillMaxWidth().background(Surf, CardShape).border(1.dp, Line, CardShape).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(ProjectType.APP to Icons.Default.Apps, ProjectType.WEBSITE to Icons.Default.Language, ProjectType.SERVER to Icons.Default.Dns).forEach { (t, ic) ->
            val sel = kind == t
            Row(Modifier.weight(1f).height(44.dp).background(if (sel) Line else Color.Transparent, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)).clickable { onPick(t) },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(ic, null, Modifier.size(18.dp), tint = if (sel) Green else Muted)
                Spacer(Modifier.width(6.dp))
                Text(t.label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (sel) Green else Muted)
            }
        }
    }

/** Page 2: an idea card (two per row). The chosen one gets a green border. */
@Composable private fun IdeaCard(name: String, pitch: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) =
    Column(modifier.fillMaxHeight().background(Surf, CardShape).border(1.dp, if (selected) Green else Line, CardShape).clip(CardShape)
        .clickable(onClick = onClick).padding(10.dp)) {
        Text(name, color = if (selected) Green else Cyan, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(pitch, fontSize = 11.sp, color = Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }

/** Page 3: one choice with a short reason, radio style. */
@Composable private fun OptionCard(title: String, sub: String, selected: Boolean, onClick: () -> Unit) =
    Row(Modifier.fillMaxWidth().background(Surf, CardShape).border(1.dp, if (selected) Green else Line, CardShape).clip(CardShape)
        .clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(20.dp).border(2.dp, if (selected) Green else Muted, CircleShape).padding(4.dp)) {
            if (selected) Box(Modifier.fillMaxSize().background(Green, CircleShape))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(sub, fontSize = 12.sp, color = Muted)
        }
    }

@Composable private fun NextStep(n: Int, text: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(24.dp).background(Line, CircleShape), contentAlignment = Alignment.Center) { Text("$n", fontSize = 12.sp, color = Green, fontWeight = FontWeight.Bold) }
    Spacer(Modifier.width(10.dp))
    Text(text, fontSize = 13.sp)
}

private fun stackNote(s: String) = when (s) {
    "HTML + CSS + JS" -> "Plain files. Fastest, no build step."
    "React + Vite" -> "Components and a build step."
    "Next.js" -> "Pages, routing, server rendering."
    "Node + Express" -> "JavaScript with a huge ecosystem."
    "Python + FastAPI" -> "Python with automatic API docs."
    "Go" -> "One small, fast binary."
    else -> ""
}

private fun hostNote(h: String) = when (h) {
    "GitHub Pages" -> "Free static hosting from your repo."
    "Vercel" -> "Deploys on every push to main."
    "Render" -> "Free web service. Listens on PORT."
    "Docker" -> "Runs anywhere with a Dockerfile."
    else -> ""
}

/** Prompt Maker: a NEW app, website or server. Page 1 = type + idea, page 2 = setup (website / server), last = ready. */
@Composable fun NewProjectScreen(vm: HubVM, nav: NavHostController) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var kindName by rememberSaveable { mutableStateOf("APP") }
    var cat by rememberSaveable { mutableStateOf(Ideas.cats.first()) }
    var name by rememberSaveable { mutableStateOf("") }
    var pitch by rememberSaveable { mutableStateOf("") }
    var stack by rememberSaveable { mutableStateOf("") }
    var host by rememberSaveable { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }
    val kind = ProjectType.valueOf(kindName)
    val app = kind == ProjectType.APP
    val stacks = Prompts.stacksFor(kind)
    val hosts = Prompts.hostsFor(kind)
    val curStack = if (stack in stacks) stack else stacks.firstOrNull().orEmpty()
    val curHost = if (host in hosts) host else hosts.firstOrNull().orEmpty()
    fun ideasOf(k: ProjectType) = when (k) { ProjectType.APP -> Ideas.list.values.flatten(); ProjectType.WEBSITE -> Ideas.web; ProjectType.SERVER -> Ideas.server }
    fun next() { step = if (step == 0 && app) 2 else step + 1 }
    fun back() { step = if (step == 2 && app) 0 else step - 1 }
    BackHandler(step > 0) { back() }
    LaunchedEffect(step) { copied = false }
    val total = if (app) 2 else 3
    val pos = if (step == 0) 1 else if (step == 1) 2 else total
    val title = listOf("New project", "Setup", "Ready")[step]
    val canNext = step != 0 || (name.isNotBlank() && pitch.isNotBlank())
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) IconButton({ back() }, Modifier.size(40.dp)) { Icon(Icons.Default.ArrowBack, null) }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Green, maxLines = 1)
                Text("Step $pos of $total", fontSize = 11.sp, color = Muted)
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 1..total) Box(Modifier.weight(1f).height(4.dp).background(if (i <= pos) Green else Line, RoundedCornerShape(2.dp)))
        }
        AnimatedContent(step, Modifier.weight(1f).fillMaxWidth(), transitionSpec = {
            val fwd = targetState > initialState
            (slideInHorizontally { w -> if (fwd) w / 6 else -w / 6 } + fadeIn()) togetherWith (slideOutHorizontally { w -> if (fwd) -w / 6 else w / 6 } + fadeOut())
        }, label = "newProject") { s ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (s) {
                    0 -> {
                        TypeSegments(kind) { t ->
                            if (t != kind) {
                                // a name and idea taken from the old type's list are dropped, your own text stays
                                if (ideasOf(kind).any { it.first == name && it.second == pitch }) { name = ""; pitch = "" }
                                kindName = t.name; stack = ""; host = ""
                            }
                        }
                        Text(when (kind) {
                            ProjectType.APP -> "Android app. A signed APK is built on GitHub."
                            ProjectType.WEBSITE -> "A site on GitHub Pages or Vercel."
                            ProjectType.SERVER -> "An API or a bot backend with a health check."
                        }, fontSize = 12.sp, color = Muted)
                        Panel {
                            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Name") })
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(pitch, { pitch = it }, Modifier.fillMaxWidth(), minLines = 2, label = { Text("What is it?") })
                        }
                        Text("Or start from an idea", color = Muted, fontSize = 12.sp)
                        if (app) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Ideas.cats.forEach { c -> FilterChip(cat == c, { cat = c }, { Text(c) }) }
                        }
                        val ideas = if (app) Ideas.list[cat].orEmpty() else ideasOf(kind)
                        ideas.chunked(2).forEach { pair ->
                            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { (n, d) -> IdeaCard(n, d, name == n && pitch == d, Modifier.weight(1f)) { name = n; pitch = d } }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                        TextButton({ vm.copy(Prompts.ideasPrompt(cat, pitch, kind)) }) {
                            Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp), tint = Cyan); Spacer(Modifier.width(6.dp))
                            Text("Not sure? Copy a prompt for 8 ideas", color = Cyan, fontSize = 13.sp)
                        }
                    }
                    1 -> {
                        Text("Stack", color = Muted, fontSize = 12.sp)
                        stacks.forEach { OptionCard(it, stackNote(it), curStack == it) { stack = it } }
                        Spacer(Modifier.height(4.dp))
                        Text("Host", color = Muted, fontSize = 12.sp)
                        hosts.forEach { OptionCard(it, hostNote(it), curHost == it) { host = it } }
                    }
                    else -> {
                        Panel {
                            Text(name.trim(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Cyan, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(pitch.trim(), fontSize = 12.sp, color = Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Tag(kind.label)
                                if (app) Tag("Kotlin, Compose, Room") else { Tag(curStack); Tag(curHost) }
                            }
                        }
                        Text("Next", color = Muted, fontSize = 12.sp)
                        NextStep(1, "Copy the prompt")
                        NextStep(2, "Send it to Claude and answer its questions")
                        NextStep(3, "Set up the project here, then give App Hub the zip")
                        TextButton({ step = 0; name = ""; pitch = ""; stack = ""; host = "" }) { Text("Start over", color = Muted) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val green = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black)
            if (step == 2) {   // the ready page is always step 2 (an app skips step 1)
                Button({
                    vm.copy(if (app) Prompts.buildPrompt(name.trim(), pitch.trim()) else Prompts.newProject(kind, name.trim(), pitch.trim(), curStack, curHost), quiet = true)
                    copied = true
                }, Modifier.weight(1.4f), colors = green) {
                    Icon(if (copied) Icons.Default.Check else Icons.Default.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    BtnText(if (copied) "Copied" else "Copy prompt")
                }
                OutlinedButton({ vm.draftName = name.trim(); vm.draftType = kind; nav.navigate("add") }, Modifier.weight(1f)) { BtnText("Set up") }
            } else {
                if (step > 0) OutlinedButton({ back() }, Modifier.weight(1f)) { BtnText("Back") }
                Button({ next() }, Modifier.weight(2f), enabled = canNext, colors = green) { BtnText("Next") }
            }
        }
    }
}
