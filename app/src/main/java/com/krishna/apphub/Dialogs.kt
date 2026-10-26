package com.krishna.apphub

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * v74: one look for every dialog. Icon + title (+ one line under it), a scrolling body, then the buttons:
 * two actions sit side by side (cancel left), three or more are full width and stacked (main action on top).
 * [tone] colours the border, the icon and the main button. Back or a tap outside calls [onDismiss].
 */
class DAct(val label: String, val style: Int, val tone: Color?, val enabled: Boolean, val onClick: () -> Unit) {
    companion object { const val FILLED = 0; const val OUTLINED = 1; const val CANCEL = 2 }
}

/** The main button (filled). */
fun dPrimary(label: String, tone: Color? = null, enabled: Boolean = true, onClick: () -> Unit) = DAct(label, DAct.FILLED, tone, enabled, onClick)
/** A second choice (outlined). */
fun dSecondary(label: String, tone: Color? = null, onClick: () -> Unit) = DAct(label, DAct.OUTLINED, tone, true, onClick)
/** Cancel / Later / Close: outlined grey next to another button, plain text when the buttons are stacked. */
fun dCancel(label: String = "Cancel", onClick: () -> Unit) = DAct(label, DAct.CANCEL, null, true, onClick)

@Composable fun HubDialog(
    title: String, onDismiss: () -> Unit, actions: List<DAct>,
    icon: ImageVector? = null, tone: Color = Green, sub: String? = null,
    body: @Composable ColumnScope.() -> Unit = {}
) {
    val shape = RoundedCornerShape(18.dp)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(max = 620.dp)
            .background(Surf, shape).border(1.dp, tone.copy(alpha = 0.45f), shape).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Box(Modifier.size(34.dp).background(tone.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = tone, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (sub != null) Text(sub, fontSize = 12.sp, color = Muted)
                }
            }
            Column(Modifier.weight(1f, fill = false).padding(top = 10.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp), content = body)
            Spacer(Modifier.height(12.dp))
            if (actions.size > 2) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                actions.forEach { DialogButton(it, tone, false, Modifier.fillMaxWidth()) }
            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.reversed().forEach { DialogButton(it, tone, true, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable private fun DialogButton(a: DAct, def: Color, inRow: Boolean, mod: Modifier) {
    val c = a.tone ?: if (a.style == DAct.CANCEL) Muted else def
    val m = mod.height(42.dp)
    val pad = PaddingValues(horizontal = 10.dp)
    val label = @Composable { Text(a.label, fontSize = 14.sp, fontWeight = if (a.style == DAct.FILLED) FontWeight.Bold else FontWeight.Normal, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
    when {
        a.style == DAct.FILLED -> Button(a.onClick, m, enabled = a.enabled, contentPadding = pad,
            colors = ButtonDefaults.buttonColors(containerColor = c, contentColor = Color.Black)) { label() }
        a.style == DAct.OUTLINED || inRow -> OutlinedButton(a.onClick, m, enabled = a.enabled, contentPadding = pad,
            border = BorderStroke(1.dp, c.copy(alpha = 0.7f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = c)) { label() }
        else -> TextButton(a.onClick, m, enabled = a.enabled, contentPadding = pad, colors = ButtonDefaults.textButtonColors(contentColor = c)) { label() }
    }
}

/** Body helpers: normal text, small grey note, a boxed block, and a boxed file / reason line. */
@Composable fun DialogText(t: String) = Text(t, fontSize = 13.sp)
@Composable fun DialogNote(t: String, color: Color = Muted) = Text(t, fontSize = 12.sp, color = color)
@Composable fun DialogCard(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxWidth().background(Bg, RoundedCornerShape(10.dp)).padding(10.dp), content = content)
@Composable fun DialogItem(title: String, why: String = "", tone: Color = Amber) = DialogCard {
    Mono(title, color = tone)
    if (why.isNotBlank()) Text(why, fontSize = 11.sp, color = Muted)
}
