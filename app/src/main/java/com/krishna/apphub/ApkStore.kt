package com.krishna.apphub

import android.content.Context
import androidx.room.Room
import androidx.work.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    // ---- v27 rollback: one extra file per app, the build that was current before the latest one ----
    fun prev(ctx: Context, id: Long) = File(dir(ctx), "app_${id}_prev.apk")
    fun hasPrev(ctx: Context, prefs: Prefs, id: Long) = prefs.prevTag(id).isNotBlank() && prev(ctx, id).exists()
    /** Moves the saved (older) APK aside as the previous build, replacing the one kept before. Called when a newer release arrives. */
    fun archive(ctx: Context, prefs: Prefs, id: Long) {
        val f = file(ctx, id); val tag = prefs.cachedTag(id)
        if (!f.exists() || tag.isBlank()) return
        val pv = prev(ctx, id); pv.delete()
        if (f.renameTo(pv)) prefs.setPrevTag(id, tag)
    }
    fun clearPrev(ctx: Context, prefs: Prefs, id: Long) { prev(ctx, id).delete(); prefs.setPrevTag(id, "") }
    /** Space used by all saved APKs (latest, previous and partial downloads). */
    fun usedBytes(ctx: Context): Long = dir(ctx).listFiles()?.sumOf { it.length() } ?: 0L

    /** Partial download of one release. Named per release so a half file of an older build is never continued. */
    fun part(ctx: Context, id: Long, tag: String) = File(dir(ctx), "app_${id}_${tag.filter { it.isLetterOrDigit() }}.part")
    fun partBytes(ctx: Context, id: Long, tag: String): Long = part(ctx, id, tag).let { if (it.exists()) it.length() else 0L }
    fun clearParts(ctx: Context, id: Long, except: File? = null) {
        dir(ctx).listFiles()?.filter { it.name.startsWith("app_${id}_") && it.name.endsWith(".part") && it != except }?.forEach { it.delete() }
    }

    /**
     * Downloads via a .part file so a broken download is never installed. An interrupted download keeps its .part file
     * and the next try resumes it. If another download of the same app is running (UI or worker) this waits for it.
     */
    suspend fun fetch(ctx: Context, gh: GitHub, prefs: Prefs, p: Project, r: GitHub.Rel, announce: Boolean = false, onProgress: (Long, Long) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            if (!busy.add(p.id)) {
                while (busy.contains(p.id)) delay(500)
                return@withContext prefs.cachedTag(p.id) == r.tag && file(ctx, p.id).exists()
            }
            try {
                val f = file(ctx, p.id); val part = part(ctx, p.id, r.tag)
                f.parentFile?.mkdirs()
                clearParts(ctx, p.id, part)
                try { gh.download(r.assetUrl, part, onProgress) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { return@withContext false }
                // a complete download must be a readable APK, otherwise start over next time
                val readable = part.length() > 0 && ctx.packageManager.getPackageArchiveInfo(part.path, 0) != null
                if (!readable) { part.delete(); return@withContext false }
                val oldTag = prefs.cachedTag(p.id)
                if (oldTag.isNotBlank() && oldTag != r.tag) archive(ctx, prefs, p.id)   // keep the build that was current as "Previous build"
                f.delete()
                if (!part.renameTo(f)) { part.delete(); return@withContext false }
                prefs.setCachedTag(p.id, r.tag); prefs.setInstallTry(p.id, 0L)
                if (announce) Notifier.apkReady(ctx, prefs, p, r.tag)
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
                    if (rel.tag != prefs.installed(p.id) && !saved) ApkStore.fetch(ctx, gh, prefs, p, rel, announce = true) { _, _ -> }
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
