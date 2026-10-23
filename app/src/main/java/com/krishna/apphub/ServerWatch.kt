package com.krishna.apphub

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * v42: server down / up alerts. A background check about every 15 minutes (Android decides the exact time) asks each server's
 * address if it answers, even when App Hub is closed. The app's own check (every 15 to 60 s while it is open) feeds the same state,
 * so a change is announced once, whoever sees it first.
 */
object ServerWatch {
    private const val NAME = "server-watch"

    /**
     * Records one check. Down is announced after 2 failed checks in a row, up only when a down was announced.
     * The first ever result is only remembered, never announced.
     */
    fun record(ctx: Context, prefs: Prefs, p: Project, ok: Boolean) {
        val state = prefs.serverState(p.id)
        if (ok) {
            prefs.setServerFails(p.id, 0)
            if (state != 1) {
                prefs.setServerState(p.id, 1)
                if (state == 3 && prefs.serverAlerts) Notifier.serverUp(ctx, prefs, p)
            }
        } else {
            val fails = prefs.serverFails(p.id) + 1
            prefs.setServerFails(p.id, fails)
            if (fails >= 2 && state != 2 && state != 3) {
                if (state == 1 && prefs.serverAlerts) { prefs.setServerState(p.id, 3); Notifier.serverDown(ctx, prefs, p) }
                else prefs.setServerState(p.id, 2)
            }
        }
    }

    fun schedule(ctx: Context, prefs: Prefs) {
        val wm = WorkManager.getInstance(ctx)
        if (!prefs.serverAlerts) { wm.cancelUniqueWork(NAME); return }
        val c = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<ServerWatchWorker>(15, TimeUnit.MINUTES).setConstraints(c).build())
    }
}

class ServerWatchWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = Prefs(ctx)
        if (!prefs.serverAlerts) return Result.success()
        val db = HubDb.build(ctx)
        try {
            db.dao().all().first().filter { it.kind == ProjectType.SERVER && Health.canProbe(it.liveUrl) }.forEach { p ->
                var ok = Health.probe(p.liveUrl)
                ServerWatch.record(ctx, prefs, p, ok)
                if (!ok) {   // a failure is checked once more a few seconds later, so one slow answer is no alarm
                    delay(5_000)
                    ok = Health.probe(p.liveUrl)
                    ServerWatch.record(ctx, prefs, p, ok)
                }
            }
        } finally { db.close() }
        return Result.success()
    }
}
