// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.data.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.logicsekai.omoauto.data.R

/** "New version available" notification. Tapping it opens the Update screen. */
object UpdateNotifier {
    /** Extra on the launch intent; MainActivity opens [OPEN_UPDATE] when it sees it. */
    const val EXTRA_OPEN = "com.logicsekai.omoauto.extra.OPEN"
    const val OPEN_UPDATE = "update"

    private const val CHANNEL_ID = "omo_updates"
    private const val NOTIFICATION_ID = 7301

    /** Returns false when notifications are not allowed. */
    fun show(context: Context, release: UpdateChecker.Release): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        if (!manager.areNotificationsEnabled()) return false
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.omo_update_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return false
        launch.putExtra(EXTRA_OPEN, OPEN_UPDATE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_omo_update)
            .setContentTitle(context.getString(R.string.omo_update_title, release.version))
            .setContentText(context.getString(R.string.omo_update_text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        return true
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }
}
