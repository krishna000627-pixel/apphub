package com.krishna.apphub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay

/**
 * v79: the job log laid out like github.com. One block per step (status icon, name, time), tap to open or close it.
 * A running step and a failed step start open. While the job runs the steps and the log refresh by themselves.
 */
private class Sect(val lines: List<Pair<Int, String>>)   // (line number, text)

private sealed class LogRow {
    class Head(val i: Int, val open: Boolean, val canOpen: Boolean) : LogRow()
    class Ln(val no: Int, val text: String) : LogRow()
}

/**
 * Cuts the log into one section per step. A step starts at "##[group]Run ...", "Post job cleanup." or "Cleaning up orphan processes"
 * (the first one at line 1). Returns null when the number of sections does not match the number of steps (a finished job must match;
 * a running job may have fewer sections than steps), and the page then shows the steps and the whole log below them.
 */
private fun splitLog(lines: List<String>, steps: Int, live: Boolean): List<Sect>? {
    if (lines.isEmpty() || steps < 1) return null
    val starts = sortedSetOf(0)
    lines.forEachIndexed { i, l ->
        if (l.startsWith("##[group]Run ") || l.startsWith("Post job cleanup.") || l.startsWith("Cleaning up orphan processes")) starts.add(i)
    }
    if (starts.size != steps && !(live && starts.size < steps)) return null
    val s = starts.toList()
    return List(steps) { k ->
        if (k >= s.size) Sect(emptyList())
        else Sect((s[k] until (s.getOrNull(k + 1) ?: lines.size)).map { (it + 1) to lines[it] })
    }
}

private fun isErr(l: String) = l.startsWith("##[error]") || l.startsWith("e: ") || l.contains("error", true) || l.contains("FAILED") || l.contains("What went wrong")

/** GitHub's own markers become colours: errors red, warnings amber, commands cyan, group titles muted. */
private fun styled(t: String): Pair<String, Color> = when {
    t.startsWith("##[error]") -> t.removePrefix("##[error]") to Red
    t.startsWith("##[warning]") -> t.removePrefix("##[warning]") to Amber
    t.startsWith("##[notice]") -> t.removePrefix("##[notice]") to Cyan
    t.startsWith("##[group]") -> ("▸ " + t.removePrefix("##[group]")) to Muted
    t.startsWith("##[debug]") -> t.removePrefix("##[debug]") to Muted
    t.startsWith("##[command]") -> t.removePrefix("##[command]") to Cyan
    t.startsWith("[command]") -> t to Cyan
    else -> t to Color.Unspecified
}

@Composable fun LogScreen(vm: HubVM, nav: NavHostController) {
    var q by remember { mutableStateOf("") }
    var errOnly by remember { mutableStateOf(false) }
    var tapped by remember { mutableStateOf(mapOf<Int, Boolean>()) }   // step index -> open, as you chose
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val steps = vm.logSteps
    val lines = vm.logLines
    val live = vm.logLive
    val sects = remember(lines, steps.size, live) { splitLog(lines, steps.size, live) }
    val filtering = q.isNotBlank() || errOnly

    DisposableEffect(Unit) { onDispose { vm.stopLog() } }   // closing the page ends the live poll
    LaunchedEffect(live) { while (live) { now = System.currentTimeMillis(); delay(1_000) } }

    val rows = remember(lines, steps, sects, tapped, q, errOnly) {
        val out = ArrayList<LogRow>()
        fun keep(l: String) = l.isNotEmpty() && !l.startsWith("##[endgroup]") && (!errOnly || isErr(l)) && (q.isBlank() || l.contains(q, true))
        if (sects != null) {
            steps.forEachIndexed { i, st ->
                val ls = sects[i].lines.filter { keep(it.second) }
                if (filtering && ls.isEmpty()) return@forEachIndexed
                val open = if (filtering) true else tapped[i] ?: (st.running || st.ok == false)
                out.add(LogRow.Head(i, open, true))
                if (open) ls.forEach { out.add(LogRow.Ln(it.first, it.second)) }
            }
        } else {
            steps.forEachIndexed { i, _ -> out.add(LogRow.Head(i, false, false)) }
            lines.forEachIndexed { i, l -> if (keep(l)) out.add(LogRow.Ln(i + 1, l)) }
        }
        out
    }

    // while live, follow the newest line, unless you scrolled up to read
    val ls = rememberLazyListState()
    var prev by remember { mutableIntStateOf(0) }
    LaunchedEffect(rows.size) {
        val last = ls.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        if (live && rows.isNotEmpty() && (prev == 0 || last >= prev - 2)) {
            val end = rows.indexOfLast { it is LogRow.Ln }
            ls.scrollToItem(if (end >= 0) end else rows.lastIndex)
        }
        prev = rows.size
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Header(vm.logTitle, nav) {
            IconButton({ vm.copy(lines.joinToString("\n")) }, Modifier.size(40.dp)) { Icon(Icons.Default.ContentCopy, null) }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            val (txt, col) = when {
                live -> "● live" to Amber
                vm.logOk == true -> "✓ passed" to Green
                vm.logOk == false -> "✕ failed" to Red
                else -> "" to Muted
            }
            if (txt.isNotEmpty()) Mono(txt, color = col)
            Spacer(Modifier.weight(1f))
            Mono("${lines.size} lines", color = Muted)
        }
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search log") },
            leadingIcon = { Icon(Icons.Default.Search, null) })
        Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(errOnly, { errOnly = !errOnly }, { Text("Errors only", fontSize = 12.sp) })
        }
        if (vm.logLoading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Green)
        if (lines.isEmpty() && !vm.logLoading) Mono(
            if (live) "No log text from GitHub yet. The steps update live; the text shows up here as soon as GitHub gives it." else "No log available.",
            Modifier.padding(vertical = 6.dp), Muted)
        LazyColumn(Modifier.weight(1f), state = ls) {
            items(rows) { r ->
                when (r) {
                    is LogRow.Head -> steps.getOrNull(r.i)?.let { st ->
                        val sec = when {
                            st.sec >= 0 -> st.sec
                            st.running && st.startMs > 0 -> ((now - st.startMs) / 1000).toInt().coerceAtLeast(0)
                            else -> -1
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).background(Surf)
                            .then(if (r.canOpen) Modifier.clickable { tapped = tapped + (r.i to !r.open) } else Modifier)
                            .padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                                if (st.running) CircularProgressIndicator(Modifier.size(14.dp), color = Amber, strokeWidth = 2.dp)
                                else Text(mark(st.ok), fontSize = 14.sp, color = when (st.ok) { true -> Green; false -> Red; else -> Muted })
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(st.name, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = if (st.ok == false) Red else Color.Unspecified)
                            if (sec >= 0) { Mono(fmtDur(sec), color = Muted); Spacer(Modifier.width(6.dp)) }
                            if (r.canOpen) Icon(if (r.open) Icons.Default.KeyboardArrowDown else Icons.Default.ChevronRight, null, Modifier.size(18.dp), tint = Muted)
                        }
                    }
                    is LogRow.Ln -> {
                        val (t, c) = styled(r.text)
                        Row(Modifier.fillMaxWidth().padding(start = 4.dp)) {
                            Mono(r.no.toString().padStart(4), color = Muted.copy(alpha = 0.6f))
                            Spacer(Modifier.width(8.dp))
                            Mono(t, Modifier.weight(1f), c)
                        }
                    }
                }
            }
        }
    }
}
