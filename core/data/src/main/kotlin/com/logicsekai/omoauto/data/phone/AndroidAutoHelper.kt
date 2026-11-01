// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.data.phone

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket

/** Facts about Android Auto on this phone, and shortcuts into its settings. */
object AndroidAutoHelper {

    const val PACKAGE = "com.google.android.projection.gearhead"
    private const val SETTINGS_ACTIVITY = "com.google.android.projection.gearhead.companion.settings.DefaultSettingsActivity"
    const val HEAD_UNIT_SERVER_PORT = 5277

    enum class ServerState { RUNNING, NOT_RUNNING, UNKNOWN }

    /** Installed Android Auto version, or null when not installed. */
    fun installedVersion(context: Context): String? = try {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(PACKAGE, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(PACKAGE, 0)
        }
        info.versionName ?: ""
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    /**
     * Android Auto's "Start head unit server" listens on 127.0.0.1:5277. If we cannot bind the
     * port because it is in use, the server is running. Call off the main thread.
     */
    fun headUnitServerState(): ServerState = try {
        ServerSocket().use { socket ->
            socket.reuseAddress = false
            socket.bind(InetSocketAddress(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), HEAD_UNIT_SERVER_PORT), 1)
        }
        ServerState.NOT_RUNNING
    } catch (e: SecurityException) {
        ServerState.UNKNOWN
    } catch (e: Exception) {
        val message = e.message.orEmpty()
        if (message.contains("EADDRINUSE") || message.contains("in use", ignoreCase = true)) ServerState.RUNNING else ServerState.UNKNOWN
    }

    fun openAndroidAutoSettings(context: Context): Boolean = start(
        context,
        Intent().setComponent(ComponentName(PACKAGE, SETTINGS_ACTIVITY)),
    ) || openAppInfo(context)

    fun openAppInfo(context: Context): Boolean = start(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", PACKAGE, null)),
    )

    fun openPlayStore(context: Context): Boolean =
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACKAGE"))) ||
            start(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PACKAGE")))

    fun openUrl(context: Context, url: String): Boolean = start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
