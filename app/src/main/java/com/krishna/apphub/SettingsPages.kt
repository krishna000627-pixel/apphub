package com.krishna.apphub

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

/** v74: Settings sub pages. Push type and Theme used to be chips inside Settings; now each is a row there that opens a page with one card per choice. */

/** One choice: icon, title, one line, radio. The chosen card gets a green border. */
@Composable fun ChoiceCard(icon: ImageVector?, title: String, sub: String, selected: Boolean, badge: String? = null, swatch: Palette? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(Modifier.fillMaxWidth().clip(shape).background(Surf).border(1.dp, if (selected) Green else Line, shape)
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (swatch != null) Box(Modifier.size(36.dp).background(swatch.bg, RoundedCornerShape(10.dp)).border(1.dp, swatch.line, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Box(Modifier.size(14.dp).background(swatch.green, CircleShape))
        } else Box(Modifier.size(36.dp).background(Line, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            if (icon != null) Icon(icon, null, Modifier.size(18.dp), tint = if (selected) Green else Cyan)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.Bold, color = if (selected) Green else androidx.compose.ui.graphics.Color.Unspecified)
                if (badge != null) { Spacer(Modifier.width(8.dp)); Pill(badge, Cyan) }
            }
            Text(sub, fontSize = 12.sp, color = Muted)
        }
        RadioButton(selected, null, colors = RadioButtonDefaults.colors(selectedColor = Green, unselectedColor = Muted))
    }
}

/** One labelled line inside an info panel. */
@Composable private fun InfoLine(label: String, text: String) = Row(Modifier.padding(top = 4.dp)) {
    Text(label, Modifier.width(78.dp), fontSize = 11.sp, color = Cyan, fontWeight = FontWeight.Bold)
    Text(text, Modifier.weight(1f), fontSize = 12.sp, color = Muted)
}

@Composable fun PushTypeScreen(vm: HubVM, nav: NavHostController) {
    var pm by remember { mutableIntStateOf(vm.prefs.pushMode) }
    var del by remember { mutableStateOf(vm.prefs.deleteMissing) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Header("Push type", nav)
        Text("How App Hub sends a zip to GitHub when you deploy.", fontSize = 12.sp, color = Muted)
        ChoiceCard(Icons.Default.CloudUpload, "API", "Each changed file is uploaded on its own.", pm == 0, "Default") { pm = 0; vm.prefs.pushMode = 0 }
        ChoiceCard(Icons.Default.Storage, "Git pack", "Like git push: one request with the raw files.", pm == 1) { pm = 1; vm.prefs.pushMode = 1 }
        Panel {
            SectionTitle(Icons.Default.Info, if (pm == 1) "About Git pack" else "About API")
            if (pm == 1) {
                InfoLine("How", "Builds the changed files and folders as git objects and sends them in one request, like git push. No base64, no per-file calls.")
                InfoLine("Speed", "Fewer round trips, so it should help most with many changed files or big ones. Not measured yet: compare the log lines.")
                InfoLine("Safety", "After the push the repo is checked. If anything is off the branch is put back and the API push runs instead.")
                InfoLine("Limits", "Needs the full repo listing and an unmoved branch. A huge repo or a busy branch uses the API.")
            } else {
                InfoLine("How", "Changed files are uploaded one by one (6 at a time, big files 2 at a time), then one commit.")
                InfoLine("Speed", "Fine for a few files, slower for hundreds because every file is its own request.")
                InfoLine("Safety", "A failed file is tried 3 times. If it still fails nothing is pushed and the log names the file and the reason.")
                InfoLine("Limits", "A file too large for the GitHub API fails; leave it out when the file check offers it.")
            }
        }
        Panel {
            SectionTitle(Icons.Default.Check, "Our pick")
            Text("Stay on API as the everyday choice: it is the tried path. Switch to Git pack when your zips change many files or carry big images, on a steady connection. The log shows MB and seconds for both, so push the same zip each way once and keep the faster one.", fontSize = 12.sp, color = Muted)
        }
        Panel {
            SectionTitle(Icons.Default.DeleteSweep, "Delete removed files")
            Row(verticalAlignment = Alignment.CenterVertically) {
                HubSwitch(del, { del = it; vm.prefs.deleteMissing = it })
                Column(Modifier.padding(start = 10.dp)) {
                    Text(if (del) "On" else "Off", fontWeight = FontWeight.Bold, color = if (del) Green else Muted)
                    Text("Works with both push types.", fontSize = 11.sp, color = Muted)
                }
            }
            InfoLine("Off", "A push only adds and updates. A file you removed from the project stays in the repo.")
            InfoLine("On", "Files that are in the repo but not in the zip are deleted in the same commit. The preview lists them as \"- path\" and counts them as \"to delete\".")
            InfoLine("Guards", "The .github folder is never deleted. If more than half the repo is missing from the zip, deleting is skipped (a partial zip). Undo last push brings files back.")
            InfoLine("Our pick", "On when every zip is the whole project. Off when you send partial zips, or files like keystores live only in the repo.")
        }
        Panel {
            SectionTitle(Icons.Default.FolderOpen, "Partial zips")
            Text("Send only the files that changed. Put partial.txt at the top of the zip:", fontSize = 12.sp, color = Muted)
            Mono("partial\ndelete: app/src/Old.kt\ndelete: old_folder/", Modifier.fillMaxWidth().background(Bg, RoundedCornerShape(8.dp)).padding(8.dp).padding(top = 0.dp), Cyan)
            InfoLine("Rules", "Include the version file (app/build.gradle.kts for apps). Only the delete: lines delete anything; the switch above is ignored for that zip. A path ending with / removes the folder.")
            InfoLine("Checked", "Before the push: empty zip, missing version file, a file both in the zip and in delete:. During it: lines not in the repo, protected files (.github, build files).")
            InfoLine("Our pick", "Partial zips for small fixes, full zips for big changes. In App Hub's prompt page turn on Partial zip and Claude writes partial.txt for you.")
            OutlinedButton({ vm.copy("partial\ndelete: path/to/file.kt\ndelete: old_folder/") }, Modifier.padding(top = 6.dp)) { Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)); Text(" Copy example", fontSize = 12.sp) }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable fun ThemeScreen(vm: HubVM, nav: NavHostController) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Header("Theme", nav)
        Text("The colours of the whole app. The change applies at once.", fontSize = 12.sp, color = Muted)
        ChoiceCard(Icons.Default.DarkMode, "Dark", "Green and cyan on near black.", vm.themeMode == 0) { vm.setTheme(0) }
        ChoiceCard(Icons.Default.LightMode, "White", "Light background with darker accents.", vm.themeMode == 1) { vm.setTheme(1) }
        ChoiceCard(Icons.Default.PhoneAndroid, "System", "Follows the dark mode of your phone.", vm.themeMode == 2) { vm.setTheme(2) }
        Text("More themes", Modifier.padding(top = 6.dp), fontSize = 12.sp, color = Muted)
        val more = listOf(3 to "True black, saves battery on AMOLED screens.", 4 to "Deep blue with a sky blue accent.", 5 to "Warm brown with an amber accent.",
            6 to "Dark purple with a violet accent.", 7 to "Blue grey with a teal accent.", 8 to "Warm light paper with an amber accent.")
        more.forEach { (m, line) -> ChoiceCard(null, Themes.names[m], line, vm.themeMode == m, swatch = Themes.byMode[m]) { vm.setTheme(m) } }
        Panel {
            SectionTitle(Icons.Default.Palette, "Accent colour")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Themes.accents.forEachIndexed { i, _ ->
                    val shown = if (i == 0) Themes.of(vm.themeMode, androidx.compose.foundation.isSystemInDarkTheme()).green
                        else Themes.accents[i]!!.let { if (Pal.p.light) it.second else it.first }
                    Box(Modifier.size(34.dp).clip(CircleShape).background(shown).border(2.dp, if (vm.accent == i) Pal.p.text else Color.Transparent, CircleShape)
                        .clickable { vm.setAccent(i) }, contentAlignment = Alignment.Center) { if (i == 0) Text("A", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold) }
                }
            }
            Text("Changes the buttons, titles and switches. The first circle is the theme's own colour.", Modifier.padding(top = 6.dp), fontSize = 11.sp, color = Muted)
        }
        Panel {
            SectionTitle(Icons.Default.Info, "Text size")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Small", "Normal", "Large").forEachIndexed { i, l ->
                    val on = vm.textSize == i
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).border(1.dp, if (on) Green else Line, RoundedCornerShape(10.dp))
                        .clickable { vm.setTextSize(i) }.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                        Text(l, fontSize = 13.sp, color = if (on) Green else Muted, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
