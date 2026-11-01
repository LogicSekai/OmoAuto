// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.streaming

import android.app.BroadcastOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.logicsekai.omoauto.common.OmoLog
import com.logicsekai.omoauto.protocol.aa.AaKeyCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Link to OmoWeb (the separate browser app for the car screen).
 *
 * Android Auto keeps head-unit keys and the rotary knob for its own UI; an app shown on the car
 * screen never receives them. So while OmoWeb is on the car screen it announces itself with a
 * heartbeat broadcast, and OmoAuto's Controls are sent straight to it instead of to Android Auto.
 * Home still goes to Android Auto so the rider can leave OmoWeb. See docs/OMOWEB-LINK.md.
 */
class OmoWebLink(context: Context, private val scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val _active = MutableStateFlow(false)

    /** True while OmoWeb is open on the car screen. */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private var webPackage: String? = null
    private var lastHeartbeat = 0L
    private var watchdog: Job? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_WEB_STATE) return
            // OmoWeb exists only on Android 15+, where the sender's identity is available.
            val sender = if (Build.VERSION.SDK_INT >= 34) sentFromPackage else null
            if (sender == null || sender !in OMOWEB_PACKAGES) return
            val isActive = intent.getBooleanExtra(EXTRA_ACTIVE, false)
            if (isActive) {
                webPackage = sender
                lastHeartbeat = SystemClock.elapsedRealtime()
                if (!_active.value) OmoLog.i(TAG, "OmoWeb on the car screen ($sender): controls go to OmoWeb")
                _active.value = true
                startWatchdog()
            } else if (sender == webPackage) {
                setInactive()
            }
        }
    }

    init {
        ContextCompat.registerReceiver(appContext, receiver, IntentFilter(ACTION_WEB_STATE), ContextCompat.RECEIVER_EXPORTED)
    }

    /** OmoWeb stopped sending heartbeats (closed, crashed, killed): give the controls back to Android Auto. */
    private fun startWatchdog() {
        if (watchdog?.isActive == true) return
        watchdog = scope.launch {
            while (isActive && _active.value) {
                delay(HEARTBEAT_TIMEOUT_MS / 2)
                if (SystemClock.elapsedRealtime() - lastHeartbeat > HEARTBEAT_TIMEOUT_MS) setInactive()
            }
        }
    }

    private fun setInactive() {
        if (_active.value) OmoLog.i(TAG, "OmoWeb left the car screen: controls go to Android Auto")
        _active.value = false
        webPackage = null
    }

    /** Sends a key to OmoWeb when it is active. Returns false when Android Auto should get it. */
    fun handleKey(keyCode: Int): Boolean = when (keyCode) {
        AaKeyCode.DPAD_UP -> send("up")
        AaKeyCode.DPAD_DOWN -> send("down")
        AaKeyCode.DPAD_LEFT -> send("left")
        AaKeyCode.DPAD_RIGHT -> send("right")
        AaKeyCode.DPAD_CENTER -> send("select")
        AaKeyCode.BACK -> send("back")
        else -> false // Home: back to Android Auto
    }

    /** Rotary steps become up (counter-clockwise) / down (clockwise) in OmoWeb. */
    fun handleRotary(delta: Int): Boolean {
        if (delta == 0 || !_active.value) return false
        repeat(minOf(kotlin.math.abs(delta), 10)) { send(if (delta < 0) "up" else "down") }
        return true
    }

    /**
     * Screen commands only OmoWeb has (Controls › Media › OmoWeb). Playback itself goes through
     * the standard media keys ([MediaKeys]); OmoWeb answers them with its media session.
     */
    fun sendScreen(command: ScreenCommand): Boolean = send(command.wire)

    enum class ScreenCommand(val wire: String) {
        /** Toggles full screen. */
        FULLSCREEN("fullscreen"),
        /** Toggles ultrawide: full screen with the video zoomed to the full width, top and bottom cropped. */
        ULTRAWIDE("ultrawide"),
    }

    private fun send(command: String, dx: Float = 0f, dy: Float = 0f): Boolean {
        val pkg = webPackage ?: return false
        if (!_active.value) return false
        val intent = Intent(ACTION_REMOTE).setPackage(pkg).putExtra(EXTRA_COMMAND, command)
            .putExtra(EXTRA_DX, dx).putExtra(EXTRA_DY, dy)
        if (Build.VERSION.SDK_INT >= 34) {
            val options = BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle()
            return runCatching { appContext.sendBroadcast(intent, null, options) }
                .onFailure { OmoLog.w(TAG, "Sending $command to OmoWeb failed", it) }
                .isSuccess
        }
        return false
    }

    companion object {
        private const val TAG = "OmoWebLink"
        const val ACTION_REMOTE = "com.logicsekai.omoweb.action.REMOTE"
        const val ACTION_WEB_STATE = "com.logicsekai.omoauto.action.WEB_STATE"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_ACTIVE = "active"
        const val EXTRA_DX = "dx"
        const val EXTRA_DY = "dy"
        const val HEARTBEAT_TIMEOUT_MS = 10_000L
        val OMOWEB_PACKAGES = setOf("com.logicsekai.omoweb", "com.logicsekai.omoweb.debug")
    }
}
