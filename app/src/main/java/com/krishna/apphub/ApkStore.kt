package com.krishna.apphub

import android.content.Context
import androidx.room.Room
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** The latest APK of each app, saved in the app's own storage. Shared by the UI and the background worker. */
object ApkStore {
    private val busy = ConcurrentHashMap.newKeySet<Long>()   // one download per app at a time (UI or worker)

    fun dir(ctx: Context) = File(ctx.filesDir, "apks")
    fun file(ctx: Context, id: Long) = File(dir(ctx), "app_$id.apk")

    /** Downloads via a .part file so a broken download is never installed. */
    suspend fun fetch(ctx: Context, gh: GitHub, prefs: Prefs, p: Project, r: GitHub.Rel, onProgress: (Long, Long) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            if (!busy.add(p.id)) return@withContext false
            try {
                val f = file(ctx, p.id); val part = File(f.path + ".part")
                f.parentFile?.mkdirs()
                val ok = runCatching { gh.download(r.assetUrl, part, onProgress) }.isSuccess && part.length() > 0 && run { f.delete(); part.renameTo(f) }
                if (!ok) { part.delete(); return@withContext false }
                prefs.setCachedTag(p.id, r.tag); prefs.setInstallTry(p.id, 0L)
                true
            } finally { busy.remove(p.id) }
        }
}

/** Runs about hourly even when the app is closed: saves the newest APK of every app that has a release newer than the installed one. */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = Prefs(ctx)
        if (prefs.token.isBlank() || !prefs.autoDownload) return Result.success()
        val gh = GitHub(prefs)
        val db = Room.databaseBuilder(ctx, Db::class.java, "hub").build()
        try {
            db.dao().all().first().filter { it.repo.contains("/") }.forEach { p ->
                runCatching {
                    val rel = gh.latest(p.repo) ?: return@runCatching
                    val saved = prefs.cachedTag(p.id) == rel.tag && ApkStore.file(ctx, p.id).exists()
                    if (rel.tag != prefs.installed(p.id) && !saved) ApkStore.fetch(ctx, gh, prefs, p, rel) { _, _ -> }
                }
            }
        } finally { db.close() }
        return Result.success()
    }
}

object Sync {
    private const val NAME = "apk-sync"
    fun schedule(ctx: Context, prefs: Prefs) {
        val wm = WorkManager.getInstance(ctx)
        if (!prefs.autoDownload) { wm.cancelUniqueWork(NAME); return }
        val c = Constraints.Builder()
            .setRequiredNetworkType(if (prefs.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true).build()
        wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS).setConstraints(c).build())
    }
}
