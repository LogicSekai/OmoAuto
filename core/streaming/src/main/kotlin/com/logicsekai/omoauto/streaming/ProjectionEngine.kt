// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.streaming

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.view.Surface
import androidx.core.content.ContextCompat
import com.logicsekai.omoauto.common.OmoLog
import com.logicsekai.omoauto.data.keys.HeadUnitKeyRepository
import com.logicsekai.omoauto.data.phone.AndroidAutoHelper
import com.logicsekai.omoauto.data.phone.CastingApps
import com.logicsekai.omoauto.data.settings.SettingsRepository
import com.logicsekai.omoauto.protocol.aa.AaHeadUnitSession
import com.logicsekai.omoauto.protocol.aa.AaTouchAction
import com.logicsekai.omoauto.protocol.aa.HeadUnitConfig
import com.logicsekai.omoauto.protocol.omoway.OmoDisplaySession
import com.logicsekai.omoauto.streaming.link.BikeSockets
import com.logicsekai.omoauto.streaming.link.WifiDirectLink
import com.logicsekai.omoauto.streaming.video.VideoGeometry
import com.logicsekai.omoauto.streaming.video.VideoPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Orchestrates one ride: Wi-Fi Direct link ⟷ display session ⟷ video pipeline ⟷ Android Auto.
 *
 * Lives for the whole process (owned by the Application). Public methods must be called on the
 * main thread; protocol callbacks hop back to the main thread before touching engine state.
 */
class ProjectionEngine(
    context: Context,
    private val settings: SettingsRepository,
    private val keys: HeadUnitKeyRepository,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    val link = WifiDirectLink(appContext)

    /** OmoWeb on the car screen takes the Controls buttons (see [OmoWebLink]). */
    val webLink = OmoWebLink(appContext, scope)

    /** Media buttons for whatever app is playing (see [MediaKeys]). */
    val mediaKeys = MediaKeys(appContext)

    private val aaStatus = MutableStateFlow(AaStatus())
    private val displayStatus = MutableStateFlow(DisplayStatus())
    private val streamStats = MutableStateFlow(StreamStats())

    val state: StateFlow<ProjectionState> = combine(link.state, link.peers, aaStatus, displayStatus, streamStats) { l, p, a, d, s ->
        ProjectionState(link = l, peers = p, androidAuto = a, display = d, stats = s)
    }.stateIn(scope, SharingStarted.Eagerly, ProjectionState())

    /** True while something needs the foreground service (link up or Android Auto running). */
    val needsForeground: StateFlow<Boolean> = state.map { s ->
        s.link is WifiDirectLink.State.Connected || s.link is WifiDirectLink.State.Connecting ||
            (s.androidAuto.phase != AaPhase.OFF && s.androidAuto.phase != AaPhase.FAILED)
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, false)

    private var aaSession: AaHeadUnitSession? = null
    private var pipeline: VideoPipeline? = null
    @Volatile private var displaySession: OmoDisplaySession? = null
    private var geometry: VideoGeometry? = null

    private var previewSurface: Surface? = null
    private var previewWidth = 0
    private var previewHeight = 0

    private var tickerJob: Job? = null
    private var displayRetryJob: Job? = null
    private var reconnectJob: Job? = null
    private var displayRetryDelayMs = 2_000L
    /** First time the display refused us (closed before accepting) in the current streak. */
    private var displayRefusedSince = 0L
    @Volatile private var displayReportedBusy = false
    private var reconnectAttempts = 0

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    // Stats bookkeeping
    private var lastFramesSent = 0L
    private var lastBytesSent = 0L
    private var lastProgressAt = 0L
    private var sessionStartedAt = 0L
    private val fpsHistory = ArrayDeque<Float>()
    private val mbpsHistory = ArrayDeque<Float>()

    init {
        scope.launch {
            link.state.collect { onLinkState(it) }
        }
        scope.launch {
            needsForeground.collect { active -> if (active) startForegroundService() }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Link

    fun startSearch() = link.startDiscovery()

    fun stopSearch() = link.stopDiscovery()

    fun connectBike(address: String, name: String) {
        reconnectAttempts = 0
        reconnectJob?.cancel()
        link.connect(address, name)
    }

    fun disconnectBike() {
        reconnectJob?.cancel()
        stopDisplaySession("Disconnected")
        link.disconnect()
    }

    private fun onLinkState(state: WifiDirectLink.State) {
        when (state) {
            is WifiDirectLink.State.Connected -> {
                reconnectAttempts = 0
                scope.launch { settings.rememberBike(state.peer.address, state.peer.displayName) }
                maybeStartDisplaySession()
            }
            is WifiDirectLink.State.Failed -> {
                stopDisplaySession("Wi-Fi Direct ${state.reason.name.lowercase()}")
                val peer = state.peer
                val retryable = state.reason == WifiDirectLink.Reason.LOST || state.reason == WifiDirectLink.Reason.TIMEOUT
                if (peer != null && retryable && settings.settings.value.autoReconnect && reconnectAttempts < MAX_RECONNECTS) {
                    reconnectAttempts++
                    reconnectJob?.cancel()
                    reconnectJob = scope.launch {
                        delay(3_000L * reconnectAttempts)
                        OmoLog.i(TAG, "Reconnecting to ${peer.displayName} (attempt $reconnectAttempts)")
                        link.connect(peer.address, peer.name)
                    }
                }
            }
            WifiDirectLink.State.Idle -> stopDisplaySession("Link idle")
            else -> Unit
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Android Auto

    fun startAndroidAuto(): StartResult {
        if (aaSession?.isRunning == true) return StartResult.ALREADY_RUNNING
        if (AndroidAutoHelper.installedVersion(appContext) == null) {
            aaStatus.value = AaStatus(AaPhase.FAILED, AaFailure.NOT_INSTALLED, "Android Auto is not installed")
            return StartResult.NOT_INSTALLED
        }
        if (keys.identity() == null) {
            aaStatus.value = AaStatus(AaPhase.FAILED, AaFailure.NO_KEYS, "Head unit keys are missing")
            return StartResult.NO_KEYS
        }
        val s = settings.settings.value
        val fit = s.fitMargins
        val geo = VideoGeometry.compute(s.outputWidth, s.sourceWidth, fit.left, fit.top, fit.right, fit.bottom)
        val pipe = VideoPipeline(geo, s.bitrateBps) { frame ->
            displaySession?.submitFrame(frame.annexB, frame.keyFrame, frame.ptsUs, frame.sps, frame.pps)
        }
        try {
            pipe.start()
        } catch (e: Exception) {
            OmoLog.e(TAG, "Video pipeline failed", e)
            pipe.release()
            aaStatus.value = AaStatus(AaPhase.FAILED, AaFailure.ENCODER_UNAVAILABLE, e.message.orEmpty())
            return StartResult.ENCODER_UNAVAILABLE
        }
        previewSurface?.let { pipe.setPreviewSurface(it, previewWidth, previewHeight) }
        geometry = geo
        pipeline = pipe

        val session = AaHeadUnitSession(
            identityProvider = { keys.identity() },
            config = HeadUnitConfig(video = geo.aa, nightMode = s.androidAutoNightMode),
            listener = AaListener(),
        )
        aaSession = session
        aaStatus.value = AaStatus(AaPhase.STARTING, detail = "Starting Android Auto")
        sessionStartedAt = System.currentTimeMillis()
        acquireLocks()
        startTicker()
        if (!session.start()) {
            cleanUpAndroidAuto()
            return StartResult.NO_KEYS
        }
        maybeStartDisplaySession()
        return StartResult.STARTED
    }

    fun stopAndroidAuto() {
        val session = aaSession ?: return
        session.stop()
        cleanUpAndroidAuto()
        aaStatus.value = AaStatus(AaPhase.OFF, detail = "Stopped")
    }

    fun sendKey(keyCode: Int) {
        if (webLink.handleKey(keyCode)) return
        aaSession?.sendKey(keyCode)
    }

    fun sendRotary(delta: Int) {
        if (webLink.handleRotary(delta)) return
        aaSession?.sendRotary(delta)
    }

    /** True while a touch that started inside Android Auto's UI area is in progress. */
    private var touchActive = false

    /**
     * Forwards a touch on the phone preview ([viewWidth] x [viewHeight] px) to Android Auto, so
     * apps that need tapping (e.g. a web browser) can be used while parked. [action] is an
     * AaTouchAction value. Touches that start in the margins are ignored.
     */
    fun touchPreview(action: Int, x: Float, y: Float, viewWidth: Int, viewHeight: Int) {
        val session = aaSession ?: return
        val geo = geometry ?: return
        when (action) {
            AaTouchAction.DOWN -> {
                val point = geo.previewToSource(x, y, viewWidth, viewHeight) ?: return
                touchActive = true
                session.sendTouch(AaTouchAction.DOWN, point.first, point.second)
            }
            else -> {
                if (!touchActive) return
                val point = geo.previewToSource(x, y, viewWidth, viewHeight, clamp = true) ?: return
                session.sendTouch(action, point.first, point.second)
                if (action == AaTouchAction.UP) touchActive = false
            }
        }
    }

    /**
     * Shows the composed picture on the phone (a TextureView on Home or Controls). The newest
     * surface wins; see [clearPreviewSurface].
     */
    fun setPreviewSurface(surface: Surface, width: Int, height: Int) {
        previewSurface = surface
        previewWidth = width
        previewHeight = height
        pipeline?.setPreviewSurface(surface, width, height)
    }

    /**
     * Detaches [surface] if it is still the preview. When moving between Home and Controls the
     * new screen's surface can arrive before the old one is destroyed; the old screen must not
     * detach the new one.
     */
    fun clearPreviewSurface(surface: Surface) {
        if (previewSurface !== surface) return
        previewSurface = null
        previewWidth = 0
        previewHeight = 0
        pipeline?.setPreviewSurface(null, 0, 0)
    }

    /** Stops everything (notification "Stop" action). */
    fun stopAll() {
        stopAndroidAuto()
        disconnectBike()
    }

    private inner class AaListener : AaHeadUnitSession.Listener {
        override fun onPhase(phase: AaHeadUnitSession.Phase) {
            val mapped = when (phase) {
                AaHeadUnitSession.Phase.IDLE -> AaPhase.STARTING
                AaHeadUnitSession.Phase.CONNECTING -> AaPhase.CONNECTING
                AaHeadUnitSession.Phase.HANDSHAKING -> AaPhase.HANDSHAKING
                AaHeadUnitSession.Phase.CONNECTED -> AaPhase.CONNECTED
                AaHeadUnitSession.Phase.STREAMING -> AaPhase.STREAMING
                AaHeadUnitSession.Phase.ENDED -> return
            }
            aaStatus.value = aaStatus.value.copy(phase = mapped, failure = null)
        }

        override fun onStatus(message: String) {
            val current = aaStatus.value
            val recovering = current.phase != AaPhase.STREAMING && message.contains("retrying")
            aaStatus.value = current.copy(detail = message, phase = if (recovering) AaPhase.RECOVERING else current.phase)
        }

        override fun onVideoConfig(data: ByteArray, offset: Int, length: Int) {
            pipeline?.onCodecConfig(data, offset, length)
        }

        override fun onVideoFrame(data: ByteArray, offset: Int, length: Int, timestampUs: Long) {
            pipeline?.onFrame(data, offset, length, timestampUs)
        }

        override fun onEnded(reason: AaHeadUnitSession.EndReason, detail: String) {
            scope.launch(Dispatchers.Main) {
                if (aaSession == null) return@launch
                cleanUpAndroidAuto()
                aaStatus.value = when (reason) {
                    AaHeadUnitSession.EndReason.STOPPED -> AaStatus(AaPhase.OFF, detail = detail)
                    AaHeadUnitSession.EndReason.PHONE_EXIT -> AaStatus(AaPhase.OFF, AaFailure.PHONE_EXIT, detail)
                    AaHeadUnitSession.EndReason.NO_KEYS -> AaStatus(AaPhase.FAILED, AaFailure.NO_KEYS, detail)
                    AaHeadUnitSession.EndReason.KEYS_REJECTED -> AaStatus(AaPhase.FAILED, AaFailure.KEYS_REJECTED, detail)
                    AaHeadUnitSession.EndReason.UNREACHABLE -> AaStatus(AaPhase.FAILED, AaFailure.SERVER_NOT_RUNNING, detail)
                    AaHeadUnitSession.EndReason.NOT_ANSWERING -> AaStatus(AaPhase.FAILED, AaFailure.NOT_ANSWERING, detail)
                    AaHeadUnitSession.EndReason.PROTOCOL_ERROR -> AaStatus(AaPhase.FAILED, AaFailure.PROTOCOL, detail)
                }
            }
        }
    }

    private fun cleanUpAndroidAuto() {
        aaSession = null
        stopDisplaySession("Android Auto stopped")
        pipeline?.release()
        pipeline = null
        geometry = null
        tickerJob?.cancel()
        tickerJob = null
        releaseLocks()
        streamStats.value = StreamStats()
        fpsHistory.clear()
        mbpsHistory.clear()
    }

    // ---------------------------------------------------------------------------------------------
    // Display session

    private fun maybeStartDisplaySession() {
        val host = (link.state.value as? WifiDirectLink.State.Connected)?.hostAddress ?: return
        val geo = geometry ?: return
        if (aaSession == null || displaySession?.isRunning == true) return
        displayRetryJob?.cancel()
        val s = settings.settings.value
        val session = OmoDisplaySession(
            host = host,
            config = OmoDisplaySession.Config(
                width = geo.outputWidth,
                height = geo.outputHeight,
                theme = if (s.displayNightTheme) "night" else "day",
                boundedLiveQueue = s.boundedLiveQueue,
            ),
            listener = DisplayListener(),
            socketFactory = { BikeSockets.create(appContext, host) },
        )
        displaySession = session
        lastFramesSent = 0
        lastBytesSent = 0
        lastProgressAt = System.currentTimeMillis()
        // While busy, keep showing BUSY during the silent retries (no flicker on the Home screen).
        if (displayStatus.value.phase != DisplayPhase.BUSY) {
            displayStatus.value = DisplayStatus(DisplayPhase.CONNECTING, "Connecting to the display")
        }
        session.start()
    }

    private fun stopDisplaySession(reason: String) {
        displayRetryJob?.cancel()
        displayRefusedSince = 0L
        displayReportedBusy = false
        val session = displaySession ?: run {
            if (displayStatus.value.phase != DisplayPhase.IDLE) displayStatus.value = DisplayStatus(DisplayPhase.IDLE, reason)
            return
        }
        displaySession = null
        session.stop(reason)
        displayStatus.value = DisplayStatus(DisplayPhase.IDLE, reason)
    }

    private inner class DisplayListener : OmoDisplaySession.Listener {
        override fun onStatus(message: String) {
            val current = displayStatus.value
            displayStatus.value = when (current.phase) {
                DisplayPhase.CONNECTING -> current.copy(phase = DisplayPhase.WAITING, detail = message)
                DisplayPhase.BUSY -> current // keep the busy explanation until the display accepts us
                else -> current.copy(detail = message)
            }
        }

        override fun onProjectionReady() {
            displayRetryDelayMs = 2_000L
            displayRefusedSince = 0L
            displayReportedBusy = false
            displayStatus.value = DisplayStatus(DisplayPhase.STREAMING, "Showing on the motorcycle display")
            pipeline?.requestKeyFrame()
        }

        override fun onKeyFrameRequested() {
            pipeline?.requestKeyFrame()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            displayStatus.value = DisplayStatus(
                if (visible) DisplayPhase.STREAMING else DisplayPhase.HIDDEN,
                if (visible) "Showing on the motorcycle display" else "The display switched to another screen",
            )
        }

        override fun onBusy() {
            displayReportedBusy = true
            // Background copies of the OMOWAY app can be stopped without asking; one that runs a
            // foreground service needs Force stop (Home shows the button).
            CastingApps.stopInBackground(appContext)
        }

        override fun onClosed(reason: String, wasReady: Boolean) {
            scope.launch(Dispatchers.Main) {
                if (displaySession?.isRunning == false) displaySession = null
                if (aaSession == null || link.state.value !is WifiDirectLink.State.Connected) {
                    displayStatus.value = DisplayStatus(DisplayPhase.IDLE, reason)
                    return@launch
                }
                val now = System.currentTimeMillis()
                if (wasReady) {
                    displayRefusedSince = 0L
                    displayReportedBusy = false
                } else if (displayRefusedSince == 0L) {
                    displayRefusedSince = now
                }
                // An explicit busy status (1/7 = 3), or 10 s of refusals, means another phone app
                // (usually the OMOWAY app) still holds the display.
                val busy = displayReportedBusy || (displayRefusedSince > 0 && now - displayRefusedSince >= BUSY_AFTER_MS)
                val wait: Long
                if (busy) {
                    if (displayStatus.value.phase != DisplayPhase.BUSY) {
                        OmoLog.w(TAG, "Display busy · another phone app holds it ($reason)")
                    }
                    displayStatus.value = DisplayStatus(DisplayPhase.BUSY, reason, CastingApps.installed(appContext))
                    // Keep knocking so the picture appears as soon as the other app is stopped.
                    wait = BUSY_RETRY_MS
                } else {
                    displayStatus.value = DisplayStatus(DisplayPhase.RETRYING, reason)
                    wait = displayRetryDelayMs
                    displayRetryDelayMs = (displayRetryDelayMs * 2).coerceAtMost(10_000L)
                }
                displayRetryJob?.cancel()
                displayRetryJob = scope.launch {
                    delay(wait)
                    maybeStartDisplaySession()
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Stats + watchdog

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(1_000)
                sampleStats()
            }
        }
    }

    private fun sampleStats() {
        val now = System.currentTimeMillis()
        val display = displaySession?.stats()
        val pipe = pipeline?.stats()
        val framesSent = display?.framesSent ?: 0L
        val bytesSent = display?.bytesSent ?: 0L
        val fps = (framesSent - lastFramesSent).coerceAtLeast(0).toDouble()
        val mbps = (bytesSent - lastBytesSent).coerceAtLeast(0) * 8 / 1e6
        if (framesSent > lastFramesSent) lastProgressAt = now
        lastFramesSent = framesSent
        lastBytesSent = bytesSent

        push(fpsHistory, (fps / 60.0).toFloat())
        push(mbpsHistory, (mbps / 10.0).toFloat())

        streamStats.value = StreamStats(
            fps = fps,
            mbps = mbps,
            framesSent = framesSent,
            renderedFrames = pipe?.renderedFrames ?: 0L,
            decodedFrames = pipe?.decodedFrames ?: 0L,
            encoder = pipe?.encoder.orEmpty(),
            bitrate = pipe?.bitrate ?: 0,
            outputSize = geometry?.let { "${it.outputWidth}×${it.outputHeight}" }.orEmpty(),
            writeMeanMs = display?.writeMeanMs ?: 0.0,
            writeMaxMs = display?.writeMaxMs ?: 0.0,
            droppedFrames = (display?.queueDropped ?: 0L) + (display?.framesSuppressed ?: 0L),
            keyFrameRequests = display?.keyFrameRequests ?: 0L,
            fpsHistory = fpsHistory.toList(),
            mbpsHistory = mbpsHistory.toList(),
            sessionSeconds = if (sessionStartedAt > 0) (now - sessionStartedAt) / 1000 else 0L,
        )

        // Watchdog: the display accepted us and is visible, Android Auto streams, but nothing
        // reached the display for 8 s → restart the display session.
        val session = displaySession
        if (session != null && display != null && display.projectionReady && display.visible &&
            aaStatus.value.phase == AaPhase.STREAMING && now - lastProgressAt > 8_000
        ) {
            OmoLog.w(TAG, "No frames reached the display for 8 s · restarting the display session")
            lastProgressAt = now
            session.stop("Display stalled")
        }
    }

    private fun push(history: ArrayDeque<Float>, value: Float) {
        history.addLast(value.coerceIn(0f, 1f))
        while (history.size > HISTORY) history.removeFirst()
    }

    /** Plain-text report for "Share diagnostics". */
    fun diagnosticsReport(appVersion: String): String = buildString {
        val s = state.value
        val cfg = settings.settings.value
        appendLine("OmoAuto $appVersion")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Android Auto: ${AndroidAutoHelper.installedVersion(appContext) ?: "not installed"}")
        appendLine("Keys: ${keys.state.value}")
        appendLine("Settings: $cfg")
        appendLine()
        appendLine("Link: ${s.link}")
        appendLine("Android Auto: ${s.androidAuto}")
        appendLine("Display: ${s.display}")
        appendLine("Stats: ${s.stats.copy(fpsHistory = emptyList(), mbpsHistory = emptyList())}")
        appendLine("Display session: ${displaySession?.stats() ?: "none"}")
        appendLine("Video pipeline: ${pipeline?.stats() ?: "none"}")
        appendLine()
        appendLine("== Recent events ==")
        OmoLog.history.snapshot().forEach { appendLine(it) }
    }

    // ---------------------------------------------------------------------------------------------

    private fun startForegroundService() {
        try {
            ContextCompat.startForegroundService(appContext, Intent(appContext, ProjectionService::class.java))
        } catch (e: Exception) {
            OmoLog.w(TAG, "Foreground service could not start", e)
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireLocks() {
        if (wakeLock == null) {
            val pm = appContext.getSystemService(PowerManager::class.java)
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OmoAuto:projection")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        if (wifiLock == null) {
            val wm = appContext.getSystemService(WifiManager::class.java)
            val mode = if (Build.VERSION.SDK_INT >= 29) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wm?.createWifiLock(mode, "OmoAuto:projection")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseLocks() {
        runCatching { wakeLock?.release() }
        runCatching { wifiLock?.release() }
        wakeLock = null
        wifiLock = null
    }

    private companion object {
        const val TAG = "OmoEngine"
        const val HISTORY = 12
        const val MAX_RECONNECTS = 5
        const val BUSY_AFTER_MS = 10_000L
        const val BUSY_RETRY_MS = 3_000L
    }
}
