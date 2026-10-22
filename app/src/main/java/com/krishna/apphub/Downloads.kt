package com.krishna.apphub

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.File

/** Finds the newest zip for an app in the Downloads folder (needs "All files access" on Android 11+). */
object Downloads {
    private val dir: File get() = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    fun hasAccess(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ctx.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    fun openAllFiles(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 30) ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}")))
    }

    private fun norm(s: String) = s.filter { it.isLetterOrDigit() }.lowercase()

    /** "AppHub_v9 (1).zip" -> "AppHub" */
    fun baseOf(file: String): String = Regex("^(.*?)_v\\d+", RegexOption.IGNORE_CASE).find(file)?.groupValues?.get(1) ?: file.substringBeforeLast('.')

    /** Newest zip whose name matches the app name or the repo name. */
    fun latestFor(p: Project): File? {
        val names = setOf(norm(p.name), norm(p.repo.substringAfter("/"))).filter { it.isNotEmpty() }.toSet()
        return dir.listFiles()?.filter { it.isFile && it.name.endsWith(".zip", true) && norm(baseOf(it.name)) in names }
            ?.maxWithOrNull(compareBy<File> { Commands.num(Zipper.versionOf(it.name)).toIntOrNull() ?: 0 }.thenBy { it.lastModified() })
    }

    fun key(f: File) = "${f.name}|${f.lastModified()}"
}
