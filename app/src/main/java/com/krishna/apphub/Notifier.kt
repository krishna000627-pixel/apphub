package com.krishna.apphub

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build

/**
 * Notifications (v63 redesign). Five channels, so each kind can be tuned in Android's own settings:
 *  - Failed builds (loud), Finished builds (quiet), Server down (loud), Server back up (quiet), Downloaded updates.
 * A failed build shows the commit message and how long it ran, and opens the build list (builds/ID). Buttons: Builds, Install.
 * Everything is grouped under one App Hub summary. The Settings switch, the Android permission and the per-app Mute all apply.
 */
object Notifier {
    private const val GROUP = "apphub"
    private const val SUMMARY_ID = 1
    private val ACCENT = 0xFF19E68C.toInt()

    private class Act(val label: String, val route: String)

    private fun allowed(ctx: Context, prefs: Prefs, p: Project): Boolean =
        prefs.notifyOn && !prefs.muted(p.id) && (Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    private fun channels(nm: NotificationManager) {
        // the three v27 channels had one importance for everything; they are replaced by the ones below
        listOf("builds", "servers", "apks").forEach { if (nm.getNotificationChannel(it) != null) nm.deleteNotificationChannel(it) }
        nm.createNotificationChannels(listOf(
            NotificationChannel("build_failed", "Failed builds", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel("build_ok", "Finished builds", NotificationManager.IMPORTANCE_LOW),
            NotificationChannel("server_down", "Server down", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel("server_up", "Server back up", NotificationManager.IMPORTANCE_LOW),
            NotificationChannel("updates", "Downloaded updates", NotificationManager.IMPORTANCE_DEFAULT)
        ))
    }

    private fun post(ctx: Context, nid: Int, channel: String, title: String, text: String, big: String?, route: String, icon: Int, actions: List<Act> = emptyList()) {
        runCatching {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            channels(nm)
            fun pi(code: Int, r: String) = PendingIntent.getActivity(ctx, code,
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("route", r),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val b = Notification.Builder(ctx, channel).setSmallIcon(icon).setColor(ACCENT).setContentTitle(title).setContentText(text)
                .setContentIntent(pi(nid * 10, route)).setAutoCancel(true).setGroup(GROUP).setShowWhen(true)
                .setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
            if (big != null) b.setStyle(Notification.BigTextStyle().bigText(big))
            actions.forEachIndexed { i, a -> b.addAction(Notification.Action.Builder(Icon.createWithResource(ctx, icon), a.label, pi(nid * 10 + i + 1, a.route)).build()) }
            nm.notify(nid, b.build())
            nm.notify(SUMMARY_ID, Notification.Builder(ctx, channel).setSmallIcon(icon).setColor(ACCENT).setContentTitle("App Hub")
                .setGroup(GROUP).setGroupSummary(true).setAutoCancel(true).setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN).build())
        }
    }

    /** One notification per app and kind: a newer one replaces the older one. */
    fun buildDone(ctx: Context, prefs: Prefs, p: Project, number: Int, ok: Boolean, msg: String = "", sec: Int = 0) {
        if (!allowed(ctx, prefs, p)) return
        val verb = if (!ok) "failed" else if (p.kind == ProjectType.APP) "passed" else "deployed"
        val first = msg.lineSequence().firstOrNull().orEmpty().trim().take(90)
        val line = listOf(first, if (sec > 0) fmtDur(sec) else "").filter { it.isNotBlank() }.joinToString(" • ")
        val acts = ArrayList<Act>()
        acts.add(Act("Builds", "builds/${p.id}"))
        if (ok && p.kind == ProjectType.APP) acts.add(Act("Install", "install/${p.id}"))
        post(ctx, 100 + (p.id * 2).toInt(), if (ok) "build_ok" else "build_failed", "${p.name} #$number $verb",
            line.ifBlank { if (ok) "Tap to open" else "Tap to see the error" },
            if (ok) null else line.ifBlank { "The build failed." } + "\nTap to see the error and fix it.",
            if (ok) "detail/${p.id}" else "builds/${p.id}",
            if (ok) android.R.drawable.stat_sys_upload_done else android.R.drawable.stat_notify_error, acts)
    }

    /** v42: a server stopped answering / answers again. One notification per server, the newer one replaces the older. */
    fun serverDown(ctx: Context, prefs: Prefs, p: Project) {
        if (!allowed(ctx, prefs, p)) return
        post(ctx, 100_000 + p.id.toInt(), "server_down", "${p.name} is not answering", "Tap to open", null, "detail/${p.id}", android.R.drawable.ic_dialog_alert)
    }

    fun serverUp(ctx: Context, prefs: Prefs, p: Project) {
        if (!allowed(ctx, prefs, p)) return
        post(ctx, 100_000 + p.id.toInt(), "server_up", "${p.name} is back up", "Tap to open", null, "detail/${p.id}", android.R.drawable.ic_dialog_info)
    }

    fun apkReady(ctx: Context, prefs: Prefs, p: Project, tag: String) {
        if (!allowed(ctx, prefs, p)) return
        post(ctx, 101 + (p.id * 2).toInt(), "updates", "${p.name} $tag downloaded", "Tap to install", null,
            "install/${p.id}", android.R.drawable.stat_sys_download_done)
    }
}
