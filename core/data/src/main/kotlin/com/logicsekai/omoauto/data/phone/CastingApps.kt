// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.data.phone

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Apps that can hold the OMOWAY display. The motorcycle shows one phone app at a time; while one
 * of these is still connected, the display answers OmoAuto with "busy" (control 7/3) and closes
 * the connection. Force-stopping that app frees the display.
 *
 * Package names must also be listed under <queries> in the app manifest.
 */
object CastingApps {

    data class App(val packageName: String, val label: String)

    val KNOWN: List<App> = listOf(
        App("com.omoway.app.android", "OMOWAY"),
        App("com.sdmv.wayxcast", "WayXCast"),
        App("com.codebros.omopoc", "OMOWAY POC"),
        App("com.sdmv.wayxauto", "WayXAuto"),
    )

    /** Known casting apps installed on this phone (the likely reason the display is busy). */
    fun installed(context: Context): List<App> = KNOWN.filter { isInstalled(context, it.packageName) }

    /**
     * Stops the known casting apps if they only run in the background (needs the normal
     * KILL_BACKGROUND_PROCESSES permission). Apps with a foreground service are not affected;
     * those need Force stop in app info.
     */
    fun stopInBackground(context: Context) {
        val am = context.getSystemService(ActivityManager::class.java) ?: return
        installed(context).forEach { app -> runCatching { am.killBackgroundProcesses(app.packageName) } }
    }

    /** Opens the system app-info screen of [packageName], where Force stop is. */
    fun openAppInfo(context: Context, packageName: String): Boolean = try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    private fun isInstalled(context: Context, packageName: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
        }
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
