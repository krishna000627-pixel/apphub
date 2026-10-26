package com.krishna.apphub

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

/** v74: Settings sub pages. Push type and Theme used to be chips inside Settings; now each is a row there that opens a page with one card per choice. */

/** One choice: icon, title, one line, radio. The chosen card gets a green border. */
@Composable fun ChoiceCard(icon: ImageVector, title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(Modifier.fillMaxWidth().clip(shape).background(Surf).border(1.dp, if (selected) Green else Line, shape)
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(Line, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(18.dp), tint = if (selected) Green else Cyan)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = if (selected) Green else androidx.compose.ui.graphics.Color.Unspecified)
            Text(sub, fontSize = 12.sp, color = Muted)
        }
        RadioButton(selected, null, colors = RadioButtonDefaults.colors(selectedColor = Green, unselectedColor = Muted))
    }
}

@Composable fun PushTypeScreen(vm: HubVM, nav: NavHostController) {
    var pm by remember { mutableIntStateOf(vm.prefs.pushMode) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Header("Push type", nav)
        Text("How App Hub sends a zip to GitHub when you deploy.", fontSize = 12.sp, color = Muted)
        ChoiceCard(Icons.Default.CloudUpload, "API", "The default. Each changed file is uploaded on its own (big files two at a time) and a failed one is retried.", pm == 0) { pm = 0; vm.prefs.pushMode = 0 }
        ChoiceCard(Icons.Default.Storage, "Git pack", "Like git push: one request with the raw files. Checked after, falls back to API if anything is off.", pm == 1) { pm = 1; vm.prefs.pushMode = 1 }
        Panel {
            SectionTitle(Icons.Default.Info, "Compare them")
            Text("The log shows how long a push took and how many MB, for both types. Push the same zip with each one to see which is faster for you.", fontSize = 12.sp, color = Muted)
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
        Spacer(Modifier.height(12.dp))
    }
}
