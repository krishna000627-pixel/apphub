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

private val CardShape = RoundedCornerShape(14.dp)

@Composable private fun Tag(text: String) =
    Text(text, fontSize = 11.sp, color = Muted, modifier = Modifier.background(Line, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))

/** Page 1: a big tappable card per project type. */
@Composable private fun KindCard(icon: ImageVector, title: String, sub: String, tags: List<String>, onClick: () -> Unit) =
    Row(Modifier.fillMaxWidth().background(Surf, CardShape).border(1.dp, Line, CardShape).clip(CardShape).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).background(Line, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Green, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(sub, fontSize = 12.sp, color = Muted)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { tags.forEach { Tag(it) } }
        }
        Icon(Icons.Default.ChevronRight, null, tint = Muted)
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

/** Prompt Maker: a NEW app, website or server, one page at a time. The action bar stays at the bottom, pages slide. */
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
    fun next() { step = if (step == 1 && app) 3 else step + 1 }
    fun back() { step = if (step == 3 && app) 1 else step - 1 }
    BackHandler(step > 0) { back() }
    LaunchedEffect(step) { copied = false }
    val total = if (app) 3 else 4
    val pos = when (step) { 0 -> 1; 1 -> 2; 2 -> 3; else -> total }
    val title = listOf("What are you building?", "Your idea", "Setup", "Ready")[step]
    val canNext = step != 1 || (name.isNotBlank() && pitch.isNotBlank())
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
                        KindCard(Icons.Default.Apps, "App", "Android app with a signed APK", listOf("Kotlin", "Compose")) { kindName = "APP"; step = 1 }
                        KindCard(Icons.Default.Language, "Website", "A site on GitHub Pages or Vercel", listOf("Responsive", "Free host")) { kindName = "WEBSITE"; step = 1 }
                        KindCard(Icons.Default.Dns, "Server", "An API or a bot backend", listOf("Health check", "Env config")) { kindName = "SERVER"; step = 1 }
                        Text("Already have it? Open it from the dashboard and use its Prompt.", fontSize = 12.sp, color = Muted)
                    }
                    1 -> {
                        Panel {
                            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Name") })
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(pitch, { pitch = it }, Modifier.fillMaxWidth(), minLines = 2, label = { Text("What is it?") })
                        }
                        Text("Or start from an idea", color = Muted, fontSize = 12.sp)
                        if (app) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Ideas.cats.forEach { c -> FilterChip(cat == c, { cat = c }, { Text(c) }) }
                        }
                        val ideas = when (kind) { ProjectType.APP -> Ideas.list[cat].orEmpty(); ProjectType.WEBSITE -> Ideas.web; ProjectType.SERVER -> Ideas.server }
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
                    2 -> {
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
                        NextStep(3, "Come back with the zip and set up the project")
                        TextButton({ step = 0; name = ""; pitch = ""; stack = ""; host = "" }) { Text("Start over", color = Muted) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        if (step > 0) Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val green = ButtonDefaults.buttonColors(containerColor = Green, contentColor = androidx.compose.ui.graphics.Color.Black)
            if (step == 3) {
                Button({
                    vm.copy(if (app) Prompts.buildPrompt(name.trim(), pitch.trim()) else Prompts.newProject(kind, name.trim(), pitch.trim(), curStack, curHost), quiet = true)
                    copied = true
                }, Modifier.weight(1.4f), colors = green) {
                    Icon(if (copied) Icons.Default.Check else Icons.Default.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    BtnText(if (copied) "Copied" else "Copy prompt")
                }
                OutlinedButton({ nav.navigate("add") }, Modifier.weight(1f)) { BtnText("Set up") }
            } else {
                OutlinedButton({ back() }, Modifier.weight(1f)) { BtnText("Back") }
                Button({ next() }, Modifier.weight(2f), enabled = canNext, colors = green) { BtnText("Next") }
            }
        }
    }
}
