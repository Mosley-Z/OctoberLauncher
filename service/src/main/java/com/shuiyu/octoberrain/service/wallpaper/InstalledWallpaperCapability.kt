package com.shuiyu.octoberrain.service.wallpaper

import android.content.ComponentName
import android.content.Context
import java.io.File
import java.io.IOException

/** Device-local result survives updates, is excluded from backups, and disappears on uninstall. */
internal object InstalledWallpaperCapability {
    private var processResult: Boolean? = null

    @Synchronized
    fun read(context: Context): Boolean {
        processResult?.let { return it }
        return readInstalledCapability(File(context.noBackupFilesDir, "wallpaper-component-v1")) {
            val info = context.packageManager.getActivityInfo(ComponentName(VENDOR_PACKAGE, VENDOR_ACTIVITY), 0)
            info.enabled && info.exported && info.applicationInfo.enabled
        }.also { processResult = it }
    }
}

/** A failed write keeps this process usable; a missing or partial record is retried on the next launch. */
internal fun readInstalledCapability(
    marker: File,
    probe: () -> Boolean,
): Boolean {
    val stored =
        when (runCatching { if (marker.exists()) marker.readText() else null }.getOrNull()) {
            "1" -> true
            "0" -> false
            else -> null
        }
    if (stored != null) return stored
    val available = runCatching(probe).getOrDefault(false)
    try {
        marker.writeText(if (available) "1" else "0")
    } catch (_: IOException) {
        // Nonessential capability persistence must not stop Application startup.
    } catch (_: SecurityException) {
        // Retain the in-memory result when the installation's directory is not writable.
    }
    return available
}
