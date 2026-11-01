// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.protocol.omoway

import com.logicsekai.omoauto.common.OmoLog
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.locks.LockSupport
import javax.crypto.KeyAgreement

/**
 * One projection session with the motorcycle display.
 *
 * Lifecycle: [start] → (display sends echo / key exchange) → [Listener.onProjectionReady] →
 * caller feeds encoded frames with [submitFrame] → [stop] or the display disconnects →
 * [Listener.onClosed].
 */
class OmoDisplaySession(
    private val host: String,
    private val config: Config,
    private val listener: Listener,
    /** Returns an unconnected socket (Android binds it to the Wi-Fi Direct network). */
    private val socketFactory: (port: Int) -> Socket = { Socket() },
) {

    data class Config(
        val width: Int,
        val height: Int,
        val targetFps: Int = 60,
        /** Sent when the display asks for the theme: "night" or "day". */
        val theme: String = "night",
        /** Drop stale frames instead of letting latency build up (recommended). */
        val boundedLiveQueue: Boolean = true,
        val controlPort: Int = OmoProtocol.CONTROL_PORT,
        val videoPort: Int = OmoProtocol.VIDEO_PORT,
    )

    interface Listener {
        fun onStatus(message: String) {}
        /** The display accepted the session – start sending video (begin with a key frame). */
        fun onProjectionReady() {}
        /** The display (or the live queue) needs a fresh key frame. */
        fun onKeyFrameRequested() {}
        fun onVisibilityChanged(visible: Boolean) {}
        /** The display said another phone app is using it (control 7/3). */
        fun onBusy() {}
        /** [wasReady]: the display had accepted this session before it ended. */
        fun onClosed(reason: String, wasReady: Boolean) {}
    }

    data class Stats(
        val framesSent: Long = 0,
        val bytesSent: Long = 0,
        val senderFps: Double = 0.0,
        val senderMbps: Double = 0.0,
        val writeMeanMs: Double = 0.0,
        val writeMaxMs: Double = 0.0,
        val writesOverBudget: Long = 0,
        val controlPacketsReceived: Long = 0,
        val keyFrameRequests: Long = 0,
        val showRequests: Long = 0,
        val hideRequests: Long = 0,
        val framesSuppressed: Long = 0,
        val queueDropped: Long = 0,
        val queueRecoveries: Long = 0,
        val visible: Boolean = true,
        val projectionReady: Boolean = false,
    )

    @Volatile var isRunning = false
        private set
    @Volatile private var controlSocket: Socket? = null
    @Volatile private var videoSocket: Socket? = null
    @Volatile private var projectionReady = false
    @Volatile private var keyExchanged = false
    @Volatile private var visible = true
    @Volatile private var needKeyFrame = false

    @Volatile private var closeReason = "stopped"
    @Volatile private var lastReply = "none"
    @Volatile private var wasReady = false

    // Write watchdog
    @Volatile private var writeInProgress = false
    @Volatile private var writeStartedNanos = 0L

    private val queue = LiveVideoQueue()
    private val controlLock = Any()

    // Stats (written by the video writer thread)
    @Volatile private var framesSent = 0L
    @Volatile private var bytesSent = 0L
    @Volatile private var firstSendNanos = 0L
    @Volatile private var lastSendNanos = 0L
    @Volatile private var writeTotalNanos = 0L
    @Volatile private var writeMaxNanos = 0L
    @Volatile private var writesOverBudget = 0L
    @Volatile private var controlPackets = 0L
    @Volatile private var keyFrameRequests = 0L
    @Volatile private var showRequests = 0L
    @Volatile private var hideRequests = 0L
    @Volatile private var suppressed = 0L

    fun start() {
        if (isRunning) return
        isRunning = true
        Thread(::controlLoop, "omo-display-control").apply { isDaemon = true; start() }
    }

    fun stop(reason: String = "stopped") {
        if (!isRunning) return
        closeReason = reason
        isRunning = false
        closeSockets()
    }

    /**
     * Queues one encoded access unit. [frame] must be Annex-B; [sps]/[pps] are raw NAL units and
     * are required for key frames.
     */
    fun submitFrame(frame: ByteArray, keyFrame: Boolean, ptsUs: Long, sps: ByteArray?, pps: ByteArray?) {
        if (!isRunning || !projectionReady) return
        val packet = OmoProtocol.encodeVideo(frame, keyFrame, ptsUs, sps, pps)
        if (config.boundedLiveQueue) {
            queue.offer(packet, keyFrame, ptsUs)
            if (queue.consumeKeyFrameRequest() && !keyFrame) listener.onKeyFrameRequested()
        } else {
            try {
                writeVideo(packet, keyFrame)
            } catch (e: IOException) {
                stop("Video socket error: ${e.message}")
            }
        }
    }

    fun stats(): Stats {
        val elapsedS = (lastSendNanos - firstSendNanos) / 1e9
        val fps = if (elapsedS > 0 && framesSent > 1) (framesSent - 1) / elapsedS else 0.0
        val mbps = if (elapsedS > 0) bytesSent * 8 / elapsedS / 1e6 else 0.0
        return Stats(
            framesSent = framesSent,
            bytesSent = bytesSent,
            senderFps = fps,
            senderMbps = mbps,
            writeMeanMs = if (framesSent > 0) writeTotalNanos / 1e6 / framesSent else 0.0,
            writeMaxMs = writeMaxNanos / 1e6,
            writesOverBudget = writesOverBudget,
            controlPacketsReceived = controlPackets,
            keyFrameRequests = keyFrameRequests,
            showRequests = showRequests,
            hideRequests = hideRequests,
            framesSuppressed = suppressed,
            queueDropped = queue.droppedFrames,
            queueRecoveries = queue.recoveries,
            visible = visible,
            projectionReady = projectionReady,
        )
    }

    // ---------------------------------------------------------------------------------------------

    private fun controlLoop() {
        try {
            status("Connecting to the motorcycle display…")
            val control = connect(config.controlPort, isVideo = false)
            controlSocket = control
            val video = connect(config.videoPort, isVideo = true)
            videoSocket = video
            if (config.boundedLiveQueue) {
                startWriterThreads()
            }
            sendControl(OmoProtocol.TYPE_SYSTEM, OmoProtocol.SYS_HELLO, OmoProtocol.helloJson(config.width, config.height).toByteArray())
            sendControl(OmoProtocol.TYPE_PROJECTION, OmoProtocol.PRJ_FOREGROUND, """{"foreground":"1"}""".toByteArray())
            status("Wi-Fi connected · waiting for the display to accept")

            val reader = OmoControlReader(DataInputStream(control.getInputStream().buffered()))
            while (isRunning) {
                val packet = reader.read()
                controlPackets++
                if (controlPackets <= LOGGED_CONTROL_PACKETS) {
                    OmoLog.i(TAG, "Display → type ${packet.type} id ${packet.id} (${packet.body.size} B)")
                }
                lastReply = "${packet.type}/${packet.id}"
                handleControl(packet)
            }
        } catch (e: EOFException) {
            if (isRunning) closeReason = "Display closed the connection · last reply $lastReply"
        } catch (e: IOException) {
            if (isRunning) closeReason = "${e.javaClass.simpleName}: ${e.message}"
        } catch (e: Exception) {
            if (isRunning) closeReason = "Error: ${e.message}"
            OmoLog.e(TAG, "Display session failed", e)
        } finally {
            isRunning = false
            projectionReady = false
            closeSockets()
            status("Display session ended · $closeReason (control packets $controlPackets, frames sent $framesSent, suppressed $suppressed)")
            listener.onClosed(closeReason, wasReady)
        }
    }

    private fun handleControl(packet: OmoControlPacket) {
        val isHide = packet.type == OmoProtocol.TYPE_PROJECTION && packet.id == OmoProtocol.PRJ_HIDE
        val wasReadyBefore = projectionReady
        when {
            packet.type == OmoProtocol.TYPE_SYSTEM && packet.id == OmoProtocol.SYS_KEYFRAME_REQUEST -> {
                keyFrameRequests++
                needKeyFrame = true
                requestRecovery()
            }
            packet.type == OmoProtocol.TYPE_PROJECTION && packet.id == OmoProtocol.PRJ_SHOW -> {
                showRequests++
                visible = true
                needKeyFrame = true
                requestRecovery()
                listener.onVisibilityChanged(true)
            }
            packet.type == OmoProtocol.TYPE_PROJECTION && packet.id == OmoProtocol.PRJ_HIDE -> {
                hideRequests++
                visible = false
                if (config.boundedLiveQueue) queue.flush()
                listener.onVisibilityChanged(false)
            }
            packet.type == OmoProtocol.TYPE_SYSTEM && packet.id == OmoProtocol.SYS_ECHO -> {
                // Heartbeat / handshake echo: send the body back, then we are ready to project.
                sendControl(OmoProtocol.TYPE_SYSTEM, OmoProtocol.SYS_ECHO, packet.body)
                markProjectionReady()
            }
            packet.type == OmoProtocol.TYPE_SYSTEM && packet.id == OmoProtocol.SYS_KEY_EXCHANGE -> {
                answerKeyExchange(packet.body)
                markProjectionReady()
            }
            packet.type == OmoProtocol.TYPE_PROJECTION && packet.id == OmoProtocol.PRJ_BUSY -> {
                closeReason = "Display is used by another phone app"
                listener.onBusy()
            }
            packet.type == OmoProtocol.TYPE_SYSTEM && packet.id == OmoProtocol.SYS_STATUS -> {
                val status = packet.body.firstOrNull()?.toInt()?.and(0xFF)
                if (status == OmoProtocol.STATUS_BUSY) {
                    closeReason = "Display is used by another phone app (status 1/7 = 3)"
                    listener.onBusy()
                } else {
                    OmoLog.i(TAG, "Display status $status")
                }
            }
            packet.type == OmoProtocol.TYPE_PROJECTION && packet.id == OmoProtocol.PRJ_THEME_QUERY -> {
                sendControl(OmoProtocol.TYPE_PROJECTION, OmoProtocol.PRJ_THEME, """{"mode":"${config.theme}"}""".toByteArray())
            }
            else -> OmoLog.d(TAG, "Control packet ignored: $packet")
        }
        // As WayXAuto did: every message from the display (heartbeats included, hide excepted)
        // asks the encoder for a fresh key frame, so a display that missed the start recovers.
        if (wasReadyBefore && !isHide) listener.onKeyFrameRequested()
    }

    private fun requestRecovery() {
        if (config.boundedLiveQueue) queue.flush()
        listener.onKeyFrameRequested()
    }

    /** ECDH P-256: the display sends its public key, we answer with ours (X.509 encoded). */
    private fun answerKeyExchange(peerPublicKey: ByteArray) {
        if (keyExchanged) return
        val generator = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
        val ours = generator.generateKeyPair()
        val theirs = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(peerPublicKey))
        KeyAgreement.getInstance("ECDH").apply {
            init(ours.private)
            doPhase(theirs, true)
            generateSecret() // not used by the display today; kept to mirror the handshake
        }
        sendControl(OmoProtocol.TYPE_SYSTEM, OmoProtocol.SYS_KEY_EXCHANGE, ours.public.encoded)
        keyExchanged = true
    }

    private fun markProjectionReady() {
        if (projectionReady || !isRunning) return
        sendControl(OmoProtocol.TYPE_PROJECTION, OmoProtocol.PRJ_READY, """{"code":0}""".toByteArray())
        projectionReady = true
        wasReady = true
        needKeyFrame = true
        status("Projection control ready · starting video")
        listener.onProjectionReady()
        listener.onKeyFrameRequested()
    }

    private fun sendControl(type: Int, id: Int, body: ByteArray) {
        val socket = controlSocket ?: throw IOException("Control socket closed")
        synchronized(controlLock) {
            socket.getOutputStream().apply {
                write(OmoProtocol.encodeControl(type, id, body))
                flush()
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Video

    private fun startWriterThreads() {
        Thread({
            while (isRunning) {
                val item = queue.poll()
                if (item == null) {
                    LockSupport.parkNanos(1_000_000)
                    continue
                }
                try {
                    writeVideo(item.packet, item.keyFrame)
                } catch (e: IOException) {
                    stop("Video socket error: ${e.message}")
                }
            }
        }, "omo-display-video-writer").apply { isDaemon = true; start() }

        Thread({
            while (isRunning) {
                if (writeInProgress && System.nanoTime() - writeStartedNanos > STALL_NANOS) {
                    stop("Video socket stalled over 750 ms")
                    break
                }
                LockSupport.parkNanos(25_000_000)
            }
        }, "omo-display-watchdog").apply { isDaemon = true; start() }
    }

    private fun writeVideo(packet: ByteArray, keyFrame: Boolean) {
        if (!visible || (needKeyFrame && !keyFrame)) {
            suppressed++
            return
        }
        if (keyFrame) needKeyFrame = false
        val socket = videoSocket ?: return
        val start = System.nanoTime()
        writeStartedNanos = start
        writeInProgress = true
        try {
            socket.getOutputStream().apply {
                write(packet)
                flush()
            }
        } finally {
            writeInProgress = false
        }
        val end = System.nanoTime()
        val took = end - start
        if (framesSent == 0L) {
            firstSendNanos = end
            OmoLog.i(TAG, "First video frame sent to the display (${if (keyFrame) "key frame" else "delta"}, ${packet.size} B)")
        }
        lastSendNanos = end
        framesSent++
        bytesSent += packet.size
        writeTotalNanos += took
        if (took > writeMaxNanos) writeMaxNanos = took
        if (config.targetFps > 0 && took > 1_000_000_000L / config.targetFps) writesOverBudget++
    }

    // ---------------------------------------------------------------------------------------------

    private fun connect(port: Int, isVideo: Boolean): Socket {
        val socket = socketFactory(port)
        if (isVideo && config.boundedLiveQueue) socket.sendBufferSize = 16 * 1024
        socket.tcpNoDelay = true
        socket.connect(InetSocketAddress(host, port), 4_000)
        socket.soTimeout = 6_000
        return socket
    }

    private fun closeSockets() {
        runCatching { controlSocket?.close() }
        runCatching { videoSocket?.close() }
        controlSocket = null
        videoSocket = null
    }

    private fun status(message: String) {
        OmoLog.i(TAG, message)
        listener.onStatus(message)
    }

    private companion object {
        const val TAG = "OmoDisplay"
        const val STALL_NANOS = 750_000_000L
        /** Control packets logged per session, to see how the display reacts. */
        const val LOGGED_CONTROL_PACKETS = 20
    }
}
