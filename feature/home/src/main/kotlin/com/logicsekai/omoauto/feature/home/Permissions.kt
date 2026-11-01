// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.feature.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.logicsekai.omoauto.data.phone.PhoneSettings

/** Runtime permissions needed to find and join the motorcycle over Wi-Fi Direct. */
internal object LinkPermissions {
    val required: Array<String>
        get() = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    val optional: Array<String>
        get() = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()

    fun granted(context: Context): Boolean = required.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

/**
 * Returns a function that runs an action once the link permissions are granted
 * (asking for them first when needed). [onDenied] is called when the user refuses, and
 * [onLocationOff] when Android 8–12 would not find any device because Location is off.
 */
@Composable
internal fun rememberWithLinkPermissions(onDenied: () -> Unit, onLocationOff: () -> Unit): (action: () -> Unit) -> Unit {
    val context = LocalContext.current
    val currentOnDenied = rememberUpdatedState(onDenied)
    val currentOnLocationOff = rememberUpdatedState(onLocationOff)
    fun runChecked(action: () -> Unit) {
        if (PhoneSettings.locationBlocksDiscovery(context)) currentOnLocationOff.value() else action()
    }
    val pending = remember { arrayOfNulls<() -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val ok = LinkPermissions.required.all { result[it] == true || LinkPermissions.granted(context) }
        val action = pending[0]
        pending[0] = null
        if (!ok) currentOnDenied.value() else if (action != null) runChecked(action)
    }
    return remember(launcher) {
        val run: (() -> Unit) -> Unit = { action ->
            if (LinkPermissions.granted(context)) {
                runChecked(action)
            } else {
                pending[0] = action
                launcher.launch(LinkPermissions.required + LinkPermissions.optional)
            }
        }
        run
    }
}
