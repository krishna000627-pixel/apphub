package com.krishna.apphub

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** v27 feature 8: the last crash is saved to a small file (filesDir/crash.txt), so it can be copied from Settings. */
object CrashLog {
    private fun file(ctx: Context) = File(ctx.filesDir, "crash.txt")

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                file(app).writeText("App Hub crash  $stamp\nthread: ${t.name}\n\n${e.stackTraceToString().take(8000)}")
            }
            old?.uncaughtException(t, e)
        }
    }

    fun read(ctx: Context): String = runCatching { file(ctx).takeIf { it.exists() }?.readText() ?: "" }.getOrDefault("")
    fun clear(ctx: Context) { runCatching { file(ctx).delete() } }
}
