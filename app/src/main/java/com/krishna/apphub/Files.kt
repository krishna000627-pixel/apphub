package com.krishna.apphub

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

// ---------- v31: tab bar that never clips its labels ----------
/** Equal-width tabs: the label gets the full width of its tab (the stock TabRow pads 16dp each side and cut "Overview" to "Overvie"). */
@Composable fun HubTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, t ->
                val on = i == selected
                Column(Modifier.weight(1f).clickable { onSelect(i) }.padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(t, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (on) Green else Muted, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(2.dp).background(if (on) Green else Color.Transparent))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
    }
}

// ---------- file helpers ----------
fun fileIcon(name: String): ImageVector = when (name.substringAfterLast('.', "").lowercase()) {
    "kt", "java", "kts", "gradle", "js", "ts", "py", "sh", "c", "cpp", "h" -> Icons.Default.Code
    "xml", "json", "yml", "yaml", "toml", "properties", "html", "css" -> Icons.Default.DataObject
    "md", "txt" -> Icons.Default.Description
    "png", "jpg", "jpeg", "webp", "gif", "svg" -> Icons.Default.Image
    "jks", "keystore", "pem" -> Icons.Default.Lock
    "zip", "apk", "aab", "jar" -> Icons.Default.Archive
    else -> Icons.Default.InsertDriveFile
}

private data class Node(val name: String, val path: String, val isDir: Boolean, val size: Long, val count: Int)

/** One folder level: folders first (with file counts), then files. */
private fun listDir(all: List<GitHub.RepoFile>, dir: String): List<Node> {
    val folders = LinkedHashMap<String, Int>()
    val files = mutableListOf<Node>()
    for (f in all) {
        if (!f.path.startsWith(dir)) continue
        val rest = f.path.substring(dir.length)
        val i = rest.indexOf('/')
        if (i >= 0) { val n = rest.substring(0, i); folders[n] = (folders[n] ?: 0) + 1 }
        else files += Node(rest, f.path, false, f.size, 0)
    }
    val dirs = folders.entries.sortedBy { it.key.lowercase() }.map { Node(it.key, dir + it.key + "/", true, 0, it.value) }
    return dirs + files.sortedBy { it.name.lowercase() }
}

@Composable private fun Crumb(t: String, last: Boolean, onClick: () -> Unit) =
    Text(t, Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 6.dp), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        color = if (last) Green else Cyan, fontWeight = if (last) FontWeight.Bold else FontWeight.Normal)

@Composable private fun NodeRow(n: Node, showPath: Boolean, busy: Boolean, canDelete: Boolean, onOpen: () -> Unit, onDelete: () -> Unit) {
    // v76: while the file is being deleted the whole row turns red (tint, red icon, red name, "Deleting…")
    Row(Modifier.fillMaxWidth().background(if (busy) Red.copy(alpha = 0.14f) else Color.Transparent, RoundedCornerShape(10.dp))
        .clickable(enabled = !busy, onClick = onOpen).padding(vertical = 8.dp, horizontal = if (busy) 8.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).background(if (busy) Red.copy(alpha = 0.22f) else Line, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(if (n.isDir) Icons.Default.Folder else fileIcon(n.name), null, Modifier.size(20.dp), tint = if (busy) Red else if (n.isDir) Amber else Cyan)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(n.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (busy) Red else Color.Unspecified)
            val sub = if (busy) "Deleting…" else if (n.isDir) "${n.count} file${if (n.count == 1) "" else "s"}"
                else fmtBytes(n.size) + if (showPath && n.path.contains('/')) " • ${n.path.substringBeforeLast('/')}" else ""
            Text(sub, fontSize = 11.sp, color = if (busy) Red else Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (n.isDir) Icon(Icons.Default.ChevronRight, null, tint = Muted)
        else if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Red, strokeWidth = 2.dp)
        else IconButton(onDelete, Modifier.size(44.dp), enabled = canDelete) { Icon(Icons.Default.DeleteOutline, null, Modifier.size(20.dp), tint = Red.copy(alpha = 0.7f)) }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Line.copy(alpha = 0.6f)))
}

/** v31: the Files tab. Folder browser with breadcrumbs, search across the whole repo, tap a file to open its page. */
fun LazyListScope.filesBrowser(
    vm: HubVM, p: Project, dir: String, onDir: (String) -> Unit, fq: String, onFq: (String) -> Unit,
    onOpen: (String) -> Unit, onDelete: (String) -> Unit
) {
    val all = vm.repoFiles[p.id]
    val nodes = when {
        all == null -> emptyList()
        fq.isBlank() -> listDir(all, dir)
        else -> all.filter { it.path.contains(fq, true) }.take(200).map { Node(it.path.substringAfterLast('/'), it.path, false, it.size, 0) }
    }
    if (all != null && fq.isBlank() && dir.isNotEmpty()) item(key = "up") {
        val parent = dir.dropLast(1).substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        Row(Modifier.fillMaxWidth().clickable { onDir(parent) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).background(Line, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ArrowUpward, null, Modifier.size(20.dp), tint = Green)
            }
            Spacer(Modifier.width(12.dp))
            Text("Up one folder", fontSize = 14.sp, color = Green)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line.copy(alpha = 0.6f)))
    }
    items(nodes, key = { it.path }) { n ->
        NodeRow(n, fq.isNotBlank(), vm.deletingFile == n.path, vm.deletingFile.isEmpty(),
            onOpen = { if (n.isDir) onDir(n.path) else onOpen(n.path) }, onDelete = { onDelete(n.path) })
    }
    if (all == null && !vm.filesLoading) item { EmptyState(Icons.Default.Folder, "No file list", "Log in with GitHub to see the files in this repo.") }
    if (all != null && nodes.isEmpty()) item {
        if (fq.isNotBlank()) EmptyState(Icons.Default.Search, "No file matches", "Try a shorter name.")
        else EmptyState(Icons.Default.Folder, "This folder is empty", "Go back up with the path above.")
    }
}

// ---------- delete confirmation (Files page and file page) ----------
@Composable fun DeleteFileDialog(p: Project, path: String, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val risky = path.startsWith(".github/workflows/")
    val name = path.substringAfterLast('/')
    val folder = path.substringBeforeLast('/', "")
    HubDialog("Delete file?", onCancel, listOf(dPrimary("Delete", Red, onClick = onConfirm), dCancel(onClick = onCancel)),
        icon = Icons.Default.DeleteOutline, tone = Red) {
        DialogCard {
            Text(name, fontWeight = FontWeight.Bold, color = Cyan, fontSize = 15.sp)
            if (folder.isNotEmpty()) Mono(folder, color = Muted)
        }
        DialogNote("One new commit removes it from ${p.repo} and a new build starts. Undo last push (app Settings) brings it back.")
        if (risky) Row(Modifier.fillMaxWidth().background(Amber.copy(alpha = 0.12f), RoundedCornerShape(10.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, Modifier.size(18.dp), tint = Amber); Spacer(Modifier.width(8.dp))
            Text("Build workflow. Without it GitHub cannot build the app.", fontSize = 12.sp, color = Amber)
        }
    }
}

// ---------- the file page ----------
private sealed class Body {
    object Loading : Body()
    class Txt(val lines: List<String>) : Body()
    class Img(val bmp: ImageBitmap) : Body()
    object Bin : Body()
    class Err(val msg: String) : Body()
}

private fun bodyOf(name: String, b: GitHub.Blob?): Body {
    if (b == null) return Body.Loading
    val bytes = b.bytes ?: return Body.Err(b.err.ifBlank { "Could not open the file." })
    val ext = name.substringAfterLast('.', "").lowercase()
    if (ext in setOf("png", "jpg", "jpeg", "webp", "gif")) {
        val bmp = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
        return if (bmp != null) Body.Img(bmp) else Body.Bin
    }
    if (bytes.any { it == 0.toByte() }) return Body.Bin
    return Body.Txt(String(bytes, Charsets.UTF_8).replace("\r", "").split("\n"))
}

private fun highlight(line: String, q: String) = buildAnnotatedString {
    if (q.isBlank()) { append(line); return@buildAnnotatedString }
    var i = 0
    while (true) {
        val j = line.indexOf(q, i, ignoreCase = true)
        if (j < 0) { append(line.substring(i)); break }
        append(line.substring(i, j))
        withStyle(SpanStyle(background = Amber.copy(alpha = 0.4f))) { append(line.substring(j, j + q.length)) }
        i = j + q.length
    }
}

/** v88: sends a file to another app (Claude, WhatsApp, Drive...) through the Android share sheet. */
fun shareFile(ctx: android.content.Context, f: java.io.File, mime: String) {
    val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType(mime)
        .putExtra(android.content.Intent.EXTRA_STREAM, uri).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION), null)
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** v31: one repo file as its own page: path, size, line count, line numbers, in-file search, copy, open on GitHub, delete. Images are shown as images. */
@Composable fun FileViewerScreen(vm: HubVM, nav: NavHostController, id: Long, path: String) {
    val p = projectOrHome(vm, nav, id) ?: return
    val ctx = LocalContext.current
    val name = path.substringAfterLast('/')
    var blob by remember(path) { mutableStateOf<GitHub.Blob?>(null) }
    LaunchedEffect(path) { blob = vm.readFile(p, path) }
    val body = remember(blob) { bodyOf(name, blob) }
    var find by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val lines = (body as? Body.Txt)?.lines ?: emptyList()
    if (confirm) DeleteFileDialog(p, path, { confirm = false }) { confirm = false; vm.deleteFile(p, path); nav.popBackStack() }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Header(name, nav) {
            if (body is Body.Txt) {
                IconButton({ find = !find; if (!find) q = "" }, Modifier.size(40.dp)) { Icon(if (find) Icons.Default.Close else Icons.Default.Search, null, tint = if (find) Green else Muted) }
                IconButton({ vm.copy(lines.joinToString("\n")) }, Modifier.size(40.dp)) { Icon(Icons.Default.ContentCopy, null, tint = Muted) }
            }
            if (blob?.bytes != null) {   // v88: share the file itself; v89: always the latest version from GitHub
                IconButton({
                    if (sharing) return@IconButton
                    sharing = true
                    scope.launch {
                        val fresh = runCatching { vm.readFile(p, path) }.getOrNull()?.takeIf { it.bytes != null }   // reload now, the repo may have changed
                        if (fresh != null) blob = fresh
                        val b = (fresh ?: blob)?.bytes
                        if (b != null) {
                            val dir = java.io.File(ctx.cacheDir, "share").apply { deleteRecursively(); mkdirs() }   // the old shared copy is replaced
                            val f = java.io.File(dir, name).apply { writeBytes(b) }
                            val mime = if (body is Body.Txt) "text/plain" else android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"
                            shareFile(ctx, f, mime)
                        }
                        sharing = false
                    }
                }, Modifier.size(40.dp)) { Icon(Icons.Default.Share, null, tint = if (sharing) Muted else Cyan) }
            }
            IconButton({ openUrl(ctx, "https://github.com/${p.repo}/blob/${vm.infos[id]?.branch ?: "main"}/$path") }, Modifier.size(40.dp)) { Icon(Icons.Default.OpenInNew, null, tint = Muted) }
            IconButton({ confirm = true }, Modifier.size(40.dp), enabled = vm.deletingFile.isEmpty()) { Icon(Icons.Default.Delete, null, tint = Red.copy(alpha = 0.85f)) }
        }
        Mono(path, color = Muted)
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            blob?.takeIf { it.bytes != null }?.let { Pill(fmtBytes(it.size), Cyan) }
            if (body is Body.Txt) Pill("${lines.size} lines", Muted)
            val ext = name.substringAfterLast('.', "")
            if (ext.isNotEmpty() && ext != name) Pill(ext.uppercase(), Green)
        }
        if (find && body is Body.Txt) OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(bottom = 8.dp), singleLine = true,
            placeholder = { Text("Find") }, leadingIcon = { Icon(Icons.Default.Search, null) })
        Box(Modifier.weight(1f).fillMaxWidth().background(Surf, RoundedCornerShape(12.dp)).border(1.dp, Line, RoundedCornerShape(12.dp))) {
            when (body) {
                is Body.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Green)
                is Body.Err -> EmptyState(Icons.Default.ErrorOutline, "Cannot show this file", body.msg, Modifier.align(Alignment.Center))
                is Body.Bin -> EmptyState(Icons.Default.InsertDriveFile, "No preview for this file type", "It is a binary file. Open it on GitHub to download it.", Modifier.align(Alignment.Center))
                is Body.Img -> Image(body.bmp, name, Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), contentScale = ContentScale.Fit)
                is Body.Txt -> {
                    val shown = remember(lines, q) {
                        val all = lines.mapIndexed { i, l -> i + 1 to l }
                        (if (q.isBlank()) all else all.filter { it.second.contains(q, true) }).take(4000)
                    }
                    if (shown.isEmpty()) EmptyState(Icons.Default.Search, "No match", "Nothing in this file contains \"$q\".", Modifier.align(Alignment.Center))
                    else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
                        itemsIndexed(shown) { _, (no, l) ->
                            Row(Modifier.fillMaxWidth()) {
                                Text("$no", Modifier.width(38.dp).padding(end = 8.dp), fontSize = 11.sp, color = Muted, fontFamily = FontFamily.Monospace, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                                Text(highlight(l.ifEmpty { " " }, q), Modifier.weight(1f), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

// ---------- the Files page (opened from the Files box on Info) ----------
/** Search, path and totals. Stays on screen while the list scrolls underneath. */
@Composable private fun FilesHead(vm: HubVM, p: Project, dir: String, onDir: (String) -> Unit, fq: String, onFq: (String) -> Unit) {
    val all = vm.repoFiles[p.id]
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(fq, onFq, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp), placeholder = { Text("Search all files") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { if (fq.isNotEmpty()) IconButton({ onFq("") }) { Icon(Icons.Default.Close, null) } })
        Box(Modifier.fillMaxWidth().height(3.dp).padding(top = 1.dp)) { if (vm.filesLoading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Green) }
        if (all != null) {
            if (fq.isBlank()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Home, null, Modifier.size(16.dp).clickable { onDir("") }, tint = if (dir.isEmpty()) Green else Cyan)
                var acc = ""
                dir.split("/").filter { it.isNotEmpty() }.forEach { seg ->
                    acc += "$seg/"
                    val target = acc
                    Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp), tint = Muted)
                    Crumb(seg, target == dir) { onDir(target) }
                }
                if (dir.isEmpty()) Crumb(p.repo.substringAfter('/'), true) { onDir("") }
            }
            Mono(if (fq.isBlank()) "${all.size} files • ${fmtBytes(all.sumOf { it.size })}" else "${all.count { it.path.contains(fq, true) }} matches",
                Modifier.padding(top = 2.dp, bottom = 4.dp), Muted)
        }
    }
}

/** Folder browser on its own page. Back goes up one folder (or clears the search) before leaving the page. */
@Composable fun FilesScreen(vm: HubVM, nav: NavHostController, id: Long) {
    val p = projectOrHome(vm, nav, id) ?: return
    val ctx = LocalContext.current
    var fq by remember(id) { mutableStateOf("") }
    var delPath by remember(id) { mutableStateOf<String?>(null) }
    var dir by remember(id) { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(id) { vm.loadFiles(p) }
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(owner) {   // v89: coming back to the page (from a file, from another app) reloads the list
        val o = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME && vm.repoFiles[id] != null) vm.loadFiles(p, quiet = true) }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) }
    }
    LaunchedEffect(dir, fq) { list.scrollToItem(0) }
    BackHandler(enabled = dir.isNotEmpty() || fq.isNotEmpty()) {
        if (fq.isNotEmpty()) fq = "" else dir = dir.dropLast(1).substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
    }
    delPath?.let { path -> DeleteFileDialog(p, path, { delPath = null }) { delPath = null; vm.deleteFile(p, path) } }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Header("Files", nav) {
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton({ menu = true }, Modifier.size(40.dp)) { Icon(Icons.Default.MoreVert, null, tint = Muted) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Download source code") }, { menu = false; vm.downloadSource(p) }, enabled = !vm.sourceBusy,
                        leadingIcon = { Icon(Icons.Default.Download, null) })
                    DropdownMenuItem({ Text("Share source code") }, { menu = false; vm.shareSource(ctx, p) }, enabled = !vm.sourceBusy,
                        leadingIcon = { Icon(Icons.Default.Share, null) })
                    DropdownMenuItem({ Text("Reload files") }, { menu = false; vm.loadFiles(p) }, enabled = !vm.filesLoading,
                        leadingIcon = { Icon(Icons.Default.Refresh, null) })
                }
            }
        }
        FilesHead(vm, p, dir, { dir = it }, fq, { fq = it })
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list) {
            filesBrowser(vm, p, dir, { dir = it }, fq, { fq = it },
                onOpen = { nav.navigate("file/$id?path=${Uri.encode(it)}") }, onDelete = { delPath = it })
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
