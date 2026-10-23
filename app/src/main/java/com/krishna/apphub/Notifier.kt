package com.krishna.apphub

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * v27 feature 3: two notifications only.
 *  - a build finished (passed or failed, with the app name)
 *  - an update APK finished downloading ("tap to install")
 * Both respect the Settings switch (Prefs.notifyOn) and the Android notification permission.
 */
object Notifier {
    private fun allowed(ctx: Context, prefs: Prefs): Boolean =
        prefs.notifyOn && (Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    private fun post(ctx: Context, nid: Int, channel: String, channelName: String, title: String, text: String, route: String, icon: Int) {
        runCatching {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(channel, channelName, NotificationManager.IMPORTANCE_DEFAULT))
            val open = Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("route", route)
            val pi = PendingIntent.getActivity(ctx, nid, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val n = Notification.Builder(ctx, channel).setSmallIcon(icon).setContentTitle(title).setContentText(text)
                .setContentIntent(pi).setAutoCancel(true).build()
            nm.notify(nid, n)
        }
    }

    /** One notification per app and kind: a newer one replaces the older one. */
    fun buildDone(ctx: Context, prefs: Prefs, p: Project, number: Int, ok: Boolean) {
        if (!allowed(ctx, prefs)) return
        post(ctx, 100 + (p.id * 2).toInt(), "builds", "Builds",
            "${p.name} build #$number ${if (!ok) "failed" else if (p.kind == ProjectType.APP) "passed" else "deployed"}",
            if (ok) "Tap to open the app" else "Tap to see the error",
            "detail/${p.id}", android.R.drawable.ic_dialog_info)
    }

    fun apkReady(ctx: Context, prefs: Prefs, p: Project, tag: String) {
        if (!allowed(ctx, prefs)) return
        post(ctx, 101 + (p.id * 2).toInt(), "apks", "Downloaded updates",
            "${p.name} $tag downloaded", "Tap to install",
            "install/${p.id}", android.R.drawable.stat_sys_download_done)
    }
}
