// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.streaming

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.logicsekai.omoauto.streaming.link.WifiDirectLink
import kotlinx.coroutines.launch

/** Implemented by the Application so the service can reach the process-wide engine. */
interface ProjectionEngineOwner {
    val projectionEngine: ProjectionEngine
}

/**
 * Keeps the process alive (foreground, type connectedDevice) while the motorcycle link or
 * Android Auto is active, and shows a notification with a Stop action. All the work happens in
 * [ProjectionEngine]; this service only mirrors its state.
 */
class ProjectionService : LifecycleService() {

    private val engine: ProjectionEngine
        get() = (application as ProjectionEngineOwner).projectionEngine

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startInForeground(buildNotification(engine.state.value))
        lifecycleScope.launch {
            engine.state.collect { state ->
                val manager = getSystemService(NotificationManager::class.java)
                manager?.notify(NOTIFICATION_ID, buildNotification(state))
            }
        }
        lifecycleScope.launch {
            engine.needsForeground.collect { needed ->
                if (!needed) {
                    ServiceCompat.stopForeground(this@ProjectionService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            engine.stopAll()
        }
        return START_NOT_STICKY
    }

    private fun startInForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun buildNotification(state: ProjectionState): Notification {
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ProjectionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val (title, text) = describe(state)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_omoauto)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setContentIntent(open)
            .addAction(0, getString(R.string.omo_notification_stop), stop)
            .build()
    }

    private fun describe(state: ProjectionState): Pair<String, String> {
        val bike = state.linkedPeer?.displayName
        val title = when {
            state.isStreamingToBike -> getString(R.string.omo_notification_streaming)
            state.isAndroidAutoOn -> getString(R.string.omo_notification_aa_running)
            state.link is WifiDirectLink.State.Connecting -> getString(R.string.omo_notification_connecting)
            else -> getString(R.string.omo_notification_linked)
        }
        val text = buildString {
            if (bike != null) append(bike)
            if (state.isStreamingToBike) {
                if (isNotEmpty()) append(" · ")
                append(String.format(java.util.Locale.ROOT, "%.0f fps · %.1f Mbps", state.stats.fps, state.stats.mbps))
            }
            if (isEmpty()) append(getString(R.string.omo_notification_tap_to_open))
        }
        return title to text
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.omo_notification_channel), NotificationManager.IMPORTANCE_LOW)
        channel.setShowBadge(false)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "projection"
        const val NOTIFICATION_ID = 12
        const val ACTION_STOP = "com.logicsekai.omoauto.action.STOP"

        fun stopIntent(context: Context): Intent = Intent(context, ProjectionService::class.java).setAction(ACTION_STOP)
    }
}
