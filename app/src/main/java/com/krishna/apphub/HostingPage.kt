@file:OptIn(ExperimentalLayoutApi::class)
package com.krishna.apphub

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File

/**
 * v62: everything about where a project is hosted and who can see its repo, in one place (was inside Screens.kt):
 * the Settings panels (Repository, server Hosting, GitHub Pages, Vercel) and the address a website shows (siteLink / siteName).
 */
@Composable fun ColumnScope.HostingPanels(vm: HubVM, nav: NavHostController, p: Project) {
    val ctx = LocalContext.current
    // v57: repo visibility, simply from Settings
    if (p.repo.contains("/")) {
        LaunchedEffect(p.id) { vm.repoMsg = ""; vm.loadVisibility(p) }
        var askPublic by remember { mutableStateOf(false) }
        val priv = vm.repoPrivate[p.id]
        Panel {
            SectionTitle(Icons.Default.Lock, "Repository")
            Text(p.repo + when (priv) { true -> " is private"; false -> " is public"; null -> "" }, fontSize = 12.sp, color = Muted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                HubSwitch(priv == false, { if (priv != null && !vm.repoBusy) { if (it) askPublic = true else vm.changeVisibility(p, true) } })
                Text(if (vm.repoBusy) "  Changing…" else "  Public repo", fontSize = 13.sp)
            }
            Text("GitHub Pages on the free plan needs a public repo. Vercel works with either.", fontSize = 11.sp, color = Muted)
            if (vm.repoMsg.isNotBlank()) Text(vm.repoMsg, Modifier.padding(top = 4.dp), fontSize = 12.sp, color = Amber)
        }
        if (askPublic) HubDialog("Make ${p.repo} public?", { askPublic = false }, listOf(
            dPrimary("Make public", Amber) { askPublic = false; vm.changeVisibility(p, false) }, dCancel { askPublic = false }), icon = Icons.Default.Lock, tone = Amber) {
            DialogText("Anyone can see the code and its history, including anything secret that was ever pushed. You can make it private again later.")
        }
    }
    if (p.kind == ProjectType.SERVER) {
        LaunchedEffect(p.id) {
            if (vm.renderUser.isNotBlank() && vm.renderServices.isEmpty()) vm.loadRender()
            if (vm.supabaseOn && vm.supabaseProjects.isEmpty()) vm.loadSupabase()
        }
        val rs = vm.renderFor(p); val sb = vm.supabaseFor(p); val onVercel = vm.isVercel(p)
        if (rs != null || sb != null || onVercel) Panel {
            SectionTitle(Icons.Default.Storage, "Hosting")
            if (onVercel) SettingRow(Icons.Default.OpenInNew, "Open on Vercel", "Dashboard", Cyan, { openUrl(ctx, "https://vercel.com/dashboard") })
            rs?.let { SettingRow(Icons.Default.OpenInNew, "Open on Render", it.name, Cyan, { openUrl(ctx, it.dashboardUrl) }) }
            sb?.let { SettingRow(Icons.Default.OpenInNew, "Open on Supabase", it.name, Cyan, { openUrl(ctx, "https://supabase.com/dashboard/project/${it.id}") }) }
        }
    }
    // v59: a website can be on GitHub Pages and on Vercel at the same time; the home card shows Vercel while it is linked, else Pages
    val siteOnPages = p.kind == ProjectType.WEBSITE && vm.pagesOn[p.id] == true
    if (p.kind == ProjectType.WEBSITE) LaunchedEffect(p.id) { vm.pagesMsg = ""; vm.loadPages(p) }
    if (p.kind == ProjectType.WEBSITE) Panel {
        SectionTitle(Icons.Default.Public, "GitHub Pages")
        if (siteOnPages) {
            Text("Enabled. ${pagesUrl(p.repo)}", fontSize = 12.sp, color = Green)
            Text("Every push to main publishes the site through the deploy workflow in your zip.", fontSize = 11.sp, color = Muted)
            SettingRow(Icons.Default.OpenInNew, "Open site", pagesUrl(p.repo), Cyan, { openUrl(ctx, pagesUrl(p.repo)) })
            SettingRow(Icons.Default.PowerSettingsNew, if (vm.pagesBusy) "Working…" else "Turn off", "Stops publishing to GitHub Pages", Amber, { if (!vm.pagesBusy) vm.disablePages(p) })
        } else {
            Text("Off. Turns Pages on for ${p.repo} with GitHub Actions as the source, then starts the deploy workflow.", fontSize = 11.sp, color = Muted)
            SettingRow(Icons.Default.CloudUpload, if (vm.pagesBusy) "Enabling…" else "Enable GitHub Pages", "Uses your GitHub login", Green, { if (!vm.pagesBusy) vm.enablePages(p) })
        }
        if (vm.pagesMsg.isNotBlank()) Text(vm.pagesMsg, Modifier.padding(top = 4.dp), fontSize = 12.sp, color = Amber)
    }
    if (p.kind != ProjectType.APP) Panel {
        SectionTitle(Icons.Default.Cloud, "Vercel")
        if (vm.isVercel(p)) {
            Text("Linked. Every push deploys by itself and the status follows the Vercel deployment.", fontSize = 11.sp, color = Muted)
            SettingRow(Icons.Default.VpnKey, "Environment variables", "Names only, values stay hidden", Cyan, { nav.navigate("env/${p.id}") })
            SettingRow(Icons.Default.OpenInNew, "Open on Vercel", "Dashboard", Muted, { openUrl(ctx, "https://vercel.com/dashboard") })
            SettingRow(Icons.Default.CloudUpload, if (vm.vercelBusy) "Starting…" else "Deploy now", "Asks Vercel to deploy the latest main", Green, { if (!vm.vercelBusy) vm.deployVercelNow(p) })
            SettingRow(Icons.Default.LinkOff, "Unlink", "Links switch to GitHub Pages", Amber, { vm.unlinkVercel(p) })
        } else {
            Text(if (vm.vercelUser.isBlank()) "Log in to Vercel in Accounts first." else "Not linked. Link creates the Vercel project from ${p.repo}.", fontSize = 11.sp, color = Muted)
            SettingRow(Icons.Default.Link, if (vm.vercelBusy) "Linking…" else "Link to Vercel", "Deploy this ${p.kind.label.lowercase()} on Vercel", Green, { if (!vm.vercelBusy) vm.linkVercel(p) })
        }
        if (vm.vercelMsg.isNotBlank()) Text(vm.vercelMsg, Modifier.padding(top = 4.dp), fontSize = 12.sp, color = Amber)
    }
}

/**
 * The address the home card, the info page and Open site show. Worked out from state only (no waiting for the database or the network),
 * so a change shows at once: Vercel while it is linked; else the GitHub Pages address when Pages is on, or when the old address was a
 * Vercel one (it was just unlinked); a github.io address is dropped as soon as Pages is turned off; else an address you typed yourself.
 */
fun HubVM.siteLink(p: Project): String {
    if (p.kind != ProjectType.WEBSITE) return p.liveUrl
    val live = p.liveUrl
    if (isVercel(p)) return if (live.contains(".github.io")) "" else live
    val pages = if (p.repo.contains("/")) pagesUrl(p.repo) else ""
    return when {
        pagesOn[p.id] == true -> pages
        live.contains(".vercel.app") -> pages
        live.contains(".github.io") && pagesOn[p.id] == false -> ""
        else -> live
    }
}

/** The short name the home card shows for the address: Vercel, GitHub Pages, or the host of an address you typed. */
fun HubVM.siteName(p: Project): String? {
    val l = siteLink(p)
    if (l.isBlank()) return null
    if (p.kind != ProjectType.WEBSITE) return hostOf(l)
    return when { l.contains(".vercel.app") -> "Vercel"; l.contains(".github.io") -> "GitHub Pages"; else -> hostOf(l) }
}
