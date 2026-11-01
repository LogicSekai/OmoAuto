// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.data.phone

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.core.location.LocationManagerCompat

/**
 * Phone makers whose system apps stop background apps aggressively. On these phones Android Auto
 * can stop answering OmoAuto unless it is allowed to auto-start and run without battery limits.
 */
enum class PhoneVendor(
    /** Vendor screens that list auto-start / background-launch permissions, tried in order. */
    val autostartScreens: List<ComponentName>,
) {
    XIAOMI(
        listOf(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        ),
    ),
    SAMSUNG(emptyList()),
    OPPO_REALME_ONEPLUS(
        listOf(
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
        ),
    ),
    VIVO(
        listOf(
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
        ),
    ),
    HUAWEI_HONOR(
        listOf(
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ),
    ),
    /** Infinix, Tecno and itel (Transsion). */
    TRANSSION(
        listOf(
            ComponentName("com.transsion.phonemaster", "com.cyin.himgr.autostart.AutoStartActivity"),
        ),
    ),
    /** Pixel, Motorola, Nokia and other near-stock phones: no extra step needed. */
    OTHER(emptyList()),
    ;

    /** True when Setup should show the "Keep Android Auto running" step. */
    val needsBackgroundStep: Boolean get() = this != OTHER

    companion object {
        val current: PhoneVendor by lazy { from(Build.MANUFACTURER, Build.BRAND) }

        /** Maps Build.MANUFACTURER / Build.BRAND to a vendor family. Pure, so it can be unit-tested. */
        fun from(manufacturer: String?, brand: String?): PhoneVendor {
            val names = listOf(manufacturer, brand).map { it.orEmpty().trim().lowercase() }
            fun any(vararg keys: String) = names.any { name -> keys.any { name.contains(it) } }
            return when {
                any("xiaomi", "redmi", "poco") -> XIAOMI
                any("samsung") -> SAMSUNG
                any("oppo", "realme", "oneplus") -> OPPO_REALME_ONEPLUS
                any("vivo", "iqoo") -> VIVO
                any("huawei", "honor") -> HUAWEI_HONOR
                any("infinix", "tecno", "itel", "transsion") -> TRANSSION
                else -> OTHER
            }
        }
    }
}

/** Shortcuts into system settings that keep Android Auto alive and let Wi-Fi Direct search. */
object PhoneSettings {

    /**
     * Opens this phone's auto-start manager. Returns false when none could be opened (the
     * vendor screen moved in this OS version); callers then fall back to Android Auto's app info.
     */
    fun openAutostart(context: Context, vendor: PhoneVendor = PhoneVendor.current): Boolean =
        vendor.autostartScreens.any { start(context, Intent().setComponent(it)) }

    /** Android's own list of apps and their battery-optimisation state. */
    fun openBatteryOptimisation(context: Context): Boolean =
        start(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) ||
            start(context, Intent(Settings.ACTION_SETTINGS))

    /**
     * Android 8–12 only report Wi-Fi Direct peers while the Location service is on (Android 13+
     * uses the Nearby devices permission instead).
     */
    val locationServiceRequired: Boolean get() = Build.VERSION.SDK_INT < 33

    fun isLocationEnabled(context: Context): Boolean {
        val manager = context.getSystemService(LocationManager::class.java) ?: return true
        return LocationManagerCompat.isLocationEnabled(manager)
    }

    /** True when searching for the motorcycle would silently find nothing because Location is off. */
    fun locationBlocksDiscovery(context: Context): Boolean = locationServiceRequired && !isLocationEnabled(context)

    fun openLocationSettings(context: Context): Boolean =
        start(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) ||
            start(context, Intent(Settings.ACTION_SETTINGS))

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        // Vendor screens are sometimes not exported to other apps.
        false
    }
}
