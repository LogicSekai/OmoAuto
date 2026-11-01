// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.protocol.aa

import com.logicsekai.omoauto.common.OmoLog
import com.logicsekai.omoauto.common.u16be
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.OutputStream
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * A virtual Android Auto head unit.
 *
 * It connects to Android Auto's developer "head unit server" on the same phone (127.0.0.1:5277),
 * completes the TLS + service discovery handshake and then receives the projected H.264 video.
 * Audio is accepted and discarded (the motorcycle plays phone audio over Bluetooth).
 *
 * Threading: [start] spawns one reader thread; [sendKey]/[sendRotary] are queued on a small
 * sender executor so they are safe to call from the UI thread.
 */
class AaHeadUnitSession(
    private val identityProvider: () -> HeadUnitIdentity?,
    private val config: HeadUnitConfig,
    private val listener: Listener,
    private val socketFactory: () -> Socket = { Socket() },
) {

    enum class Phase { IDLE, CONNECTING, HANDSHAKING, CONNECTED, STREAMING, ENDED }

    enum class EndReason {
        /** We called [stop]. */
        STOPPED,
        /** The user exited Android Auto on the phone (shutdown request). */
        PHONE_EXIT,
        /** Head unit keys are missing. */
        NO_KEYS,
        /** Android Auto rejected our certificate or TLS failed. */
        KEYS_REJECTED,
        /** Head unit server not reachable after retries were abandoned. */
        UNREACHABLE,
        /**
         * The server accepted the connection (or let it hang) but never answered. Usually the
         * phone froze Android Auto in the background, or a previous session left the server
         * stuck. Retrying makes it worse (hung connections pile up), so the session stops and
         * the user is guided to wake Android Auto / restart its head unit server.
         */
        NOT_ANSWERING,
        PROTOCOL_ERROR,
    }

    interface Listener {
        fun onPhase(phase: Phase) {}
        fun onStatus(message: String) {}
        /** H.264 SPS/PPS (Annex-B) – configure the decoder. */
        fun onVideoConfig(data: ByteArray, offset: Int, length: Int) {}
        /** One H.264 access unit (Annex-B). The buffer is reused after this call returns. */
        fun onVideoFrame(data: ByteArray, offset: Int, length: Int, timestampUs: Long) {}
        fun onInputBound() {}
        fun onEnded(reason: EndReason, detail: String) {}
    }

    @Volatile private var running = false
    @Volatile private var socket: Socket? = null
    @Volatile private var output: OutputStream? = null
    @Volatile private var tls: TlsBridge? = null
    @Volatile private var encrypted = false
    @Volatile private var inputBound = false
    @Volatile private var phoneExited = false
    @Volatile var phase: Phase = Phase.IDLE
        private set

    /** True once Android Auto answered the version request on the current connection. */
    @Volatile private var versionReceived = false

    private var videoSession = 0
    private var audioSession = 0
    private val sendLock = Any()
    private var reader: Thread? = null
    private var sender: ExecutorService? = null

    /** Video frames received in the current connection. */
    @Volatile var videoFrames: Long = 0
        private set

    val isRunning: Boolean get() = running

    /** Returns false (and reports NO_KEYS) when no head unit identity is available. */
    @Synchronized
    fun start(): Boolean {
        if (running) return true
        if (identityProvider() == null) {
            status("Head unit keys are missing · finish Setup first")
            listener.onEnded(EndReason.NO_KEYS, "Head unit keys are missing")
            return false
        }
        running = true
        phoneExited = false
        sender = Executors.newSingleThreadExecutor { r -> Thread(r, "omo-aa-sender").apply { isDaemon = true } }
        reader = Thread(::runLoop, "omo-aa-reader").apply { isDaemon = true; start() }
        return true
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        if (encrypted) {
            runCatching { send(AaChannel.CONTROL, 0, AaControl.SHUTDOWN_REQUEST, AaMessages.shutdownRequest()) }
        }
        closeSocket()
        sender?.shutdownNow()
        reader?.interrupt()
    }

    fun sendKey(keyCode: Int) = enqueueInput {
        val now = System.nanoTime()
        send(AaChannel.INPUT, 0, AaInput.EVENT, AaMessages.keyEvent(now, keyCode, down = true))
        send(AaChannel.INPUT, 0, AaInput.EVENT, AaMessages.keyEvent(now + 1_000_000, keyCode, down = false))
    }

    /** Touch at ([x], [y]) in video-frame coordinates; [action] is an [AaTouchAction] value. */
    fun sendTouch(action: Int, x: Int, y: Int) = enqueueInput {
        send(AaChannel.INPUT, 0, AaInput.EVENT, AaMessages.touchEvent(System.nanoTime(), action, x, y))
    }

    fun sendRotary(delta: Int) = enqueueInput {
        send(AaChannel.INPUT, 0, AaInput.EVENT, AaMessages.rotaryEvent(System.nanoTime(), delta))
    }

    private fun enqueueInput(block: () -> Unit) {
        if (!inputBound) return
        sender?.execute {
            try {
                block()
            } catch (e: Exception) {
                OmoLog.w(TAG, "Input send failed", e)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Connection loop. A refused connection (server not started yet) is retried with backoff
    // (1.5 s → 12 s, at most MAX_UNREACHABLE_ATTEMPTS times). A server that accepts but never
    // answers is NOT retried: every new attempt would add another hung connection to its queue.

    private fun runLoop() {
        var backoffMs = 1_500L
        var unreachableAttempts = 0
        var endReason = EndReason.STOPPED
        var endDetail = "stopped"
        while (running) {
            val outcome = try {
                runOnce()
                if (phoneExited) Outcome(EndReason.PHONE_EXIT, "Android Auto ended the session", retry = false)
                else Outcome(EndReason.STOPPED, "Connection closed", retry = true)
            } catch (e: SSLException) {
                Outcome(EndReason.KEYS_REJECTED, "TLS failed: ${e.message}", retry = false)
            } catch (e: NotAnsweringException) {
                Outcome(EndReason.NOT_ANSWERING, e.message ?: "Android Auto did not answer", retry = false)
            } catch (e: ConnectException) {
                unreachableAttempts++
                Outcome(
                    EndReason.UNREACHABLE,
                    "Head unit server is not running",
                    retry = unreachableAttempts < MAX_UNREACHABLE_ATTEMPTS,
                )
            } catch (e: EOFException) {
                Outcome(EndReason.STOPPED, "Android Auto closed the connection", retry = true)
            } catch (e: IOException) {
                Outcome(EndReason.STOPPED, "${e.javaClass.simpleName}: ${e.message}", retry = true)
            } catch (e: IllegalArgumentException) {
                Outcome(EndReason.PROTOCOL_ERROR, "Protocol error: ${e.message}", retry = true)
            } catch (e: IllegalStateException) {
                Outcome(EndReason.PROTOCOL_ERROR, e.message ?: "Protocol error", retry = false)
            } finally {
                closeSocket()
                encrypted = false
                inputBound = false
                tls = null
            }
            endReason = outcome.reason
            endDetail = outcome.detail
            OmoLog.i(TAG, "Session ended: ${outcome.detail} (frames $videoFrames)")
            if (!running || !outcome.retry) break
            status("${outcome.detail} · retrying in ${backoffMs / 1000.0} s")
            try {
                Thread.sleep(backoffMs)
            } catch (_: InterruptedException) {
                break
            }
            backoffMs = (backoffMs * 2).coerceAtMost(12_000L)
        }
        if (!running) {
            endReason = if (phoneExited) EndReason.PHONE_EXIT else EndReason.STOPPED
        }
        running = false
        setPhase(Phase.ENDED)
        listener.onEnded(endReason, endDetail)
    }

    private class Outcome(val reason: EndReason, val detail: String, val retry: Boolean)

    private fun runOnce() {
        videoFrames = 0
        versionReceived = false
        setPhase(Phase.CONNECTING)
        status("Connecting to Android Auto head unit server on port ${config.port}…")
        val s = socketFactory()
        socket = s
        s.tcpNoDelay = true
        try {
            s.connect(InetSocketAddress(config.host, config.port), CONNECT_TIMEOUT_MS)
        } catch (e: SocketTimeoutException) {
            // A refused port fails at once; a timeout means the server's accept queue is full.
            throw NotAnsweringException("Android Auto's head unit server is stuck (connect timed out)")
        }
        s.soTimeout = VERSION_TIMEOUT_MS
        output = s.getOutputStream()
        setPhase(Phase.HANDSHAKING)
        status("Connected · sending version request")
        send(AaChannel.CONTROL, 0, AaControl.VERSION_REQUEST, AaMessages.versionRequest(), allowEncryption = false)

        val reader = AaFrameReader(DataInputStream(s.getInputStream().buffered(64 * 1024)))
        val assembler = FrameAssembler()
        while (running && !phoneExited) {
            val frame = try {
                reader.read()
            } catch (e: SocketTimeoutException) {
                if (!versionReceived) {
                    throw NotAnsweringException("Android Auto did not answer within ${VERSION_TIMEOUT_MS / 1000} s")
                }
                throw e
            }
            val payload = if (frame.isEncrypted) {
                val bridge = tls ?: throw IOException("Encrypted frame before TLS")
                bridge.decrypt(frame.payload)
            } else {
                frame.payload
            }
            val message = assembler.accept(frame.channel, frame.flags, payload) ?: continue
            if (message.size >= 2) handleMessage(frame.channel, message)
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun handleMessage(channel: Int, msg: ByteArray) {
        val id = msg.u16be(0)
        val bodyLength = msg.size - 2
        if (channel != AaChannel.CONTROL && id == AaControl.CHANNEL_OPEN_REQUEST) {
            val service = runCatching { ProtoReader.parse(msg, 2, bodyLength).varint(2, -1) }.getOrDefault(-1)
            status("Channel $channel opened by phone (service $service)")
            send(channel, AaFrame.FLAG_CONTROL, AaControl.CHANNEL_OPEN_RESPONSE, AaMessages.status())
            return
        }
        when (channel) {
            AaChannel.CONTROL -> handleControl(id, msg, bodyLength)
            AaChannel.SENSOR -> handleSensor(id, msg, bodyLength)
            AaChannel.VIDEO -> handleVideo(id, msg, bodyLength)
            AaChannel.INPUT -> if (id == AaInput.BINDING_REQUEST) {
                send(AaChannel.INPUT, 0, AaInput.BINDING_RESPONSE, AaMessages.status())
                inputBound = true
                status("Phone controls bound")
                listener.onInputBound()
            }
            AaChannel.AUDIO -> handleAudio(id, msg, bodyLength)
            AaChannel.MICROPHONE -> when (id) {
                AaMedia.SETUP_REQUEST -> send(channel, 0, AaMedia.CONFIG_RESPONSE, AaMessages.mediaConfig(1))
                AaMedia.MIC_REQUEST -> {
                    send(channel, 0, AaMedia.MIC_RESPONSE, AaMessages.micResponse())
                    status("Microphone requested by Android Auto (not available)")
                }
            }
            else -> OmoLog.d(TAG, "Unhandled channel $channel message $id")
        }
    }

    private fun handleControl(id: Int, msg: ByteArray, bodyLength: Int) {
        when (id) {
            AaControl.VERSION_RESPONSE -> {
                val major = msg.u16be(2)
                val minor = msg.u16be(4)
                val result = if (bodyLength >= 6) msg.u16be(6) else 0
                versionReceived = true
                socket?.soTimeout = READ_TIMEOUT_MS
                status("Phone protocol version $major.$minor (result $result)")
                if (result != 0) throw IllegalStateException("Protocol version mismatch: $major.$minor")
                val identity = identityProvider() ?: throw IllegalStateException("Head unit keys are missing")
                val bridge = TlsBridge.client(identity)
                tls = bridge
                send(AaChannel.CONTROL, 0, AaControl.SSL_HANDSHAKE, bridge.begin(), allowEncryption = false)
                status("TLS handshake started")
            }
            AaControl.SSL_HANDSHAKE -> {
                val bridge = tls ?: throw IllegalStateException("Handshake data before version exchange")
                val reply = bridge.onHandshakeData(msg.copyOfRange(2, msg.size))
                if (reply.isNotEmpty()) send(AaChannel.CONTROL, 0, AaControl.SSL_HANDSHAKE, reply, allowEncryption = false)
                if (bridge.isHandshakeComplete && !encrypted) {
                    send(AaChannel.CONTROL, 0, AaControl.AUTH_COMPLETE, AaMessages.status(), allowEncryption = false)
                    encrypted = true
                    socket?.soTimeout = 0
                    setPhase(Phase.CONNECTED)
                    status("TLS handshake complete · head unit keys accepted")
                }
            }
            AaControl.SERVICE_DISCOVERY_REQUEST -> {
                val request = runCatching { ProtoReader.parse(msg, 2, bodyLength) }.getOrNull()
                val device = request?.string(4) ?: request?.string(5) ?: "phone"
                status("Service discovery from $device")
                send(AaChannel.CONTROL, 0, AaControl.SERVICE_DISCOVERY_RESPONSE, AaMessages.serviceDiscoveryResponse(config))
            }
            AaControl.PING_REQUEST -> {
                val timestamp = ProtoReader.parse(msg, 2, bodyLength).varint(1, 0)
                send(AaChannel.CONTROL, 0, AaControl.PING_RESPONSE, AaMessages.pingResponse(timestamp))
            }
            AaControl.NAV_FOCUS_REQUEST ->
                send(AaChannel.CONTROL, 0, AaControl.NAV_FOCUS_NOTIFICATION, AaMessages.navFocus())
            AaControl.AUDIO_FOCUS_REQUEST -> {
                val request = ProtoReader.parse(msg, 2, bodyLength).varint(1, 1).toInt()
                val state = if (request == 4) 3 else 1 // RELEASE → LOSS, anything else → GAIN
                send(AaChannel.CONTROL, 0, AaControl.AUDIO_FOCUS_NOTIFICATION, AaMessages.audioFocus(state))
            }
            AaControl.SHUTDOWN_REQUEST -> {
                send(AaChannel.CONTROL, 0, AaControl.SHUTDOWN_RESPONSE, ByteArray(0))
                status("Android Auto ended the session")
                phoneExited = true
                closeSocket()
            }
            AaControl.SHUTDOWN_RESPONSE -> closeSocket()
            AaControl.PING_RESPONSE, AaControl.VOICE_SESSION_REQUEST -> Unit
            else -> OmoLog.d(TAG, "Unhandled control message $id")
        }
    }

    private fun handleSensor(id: Int, msg: ByteArray, bodyLength: Int) {
        if (id != AaSensor.START_REQUEST) return
        val type = ProtoReader.parse(msg, 2, bodyLength).varint(1, -1).toInt()
        send(AaChannel.SENSOR, 0, AaSensor.START_RESPONSE, AaMessages.status())
        when (type) {
            AaSensor.TYPE_DRIVING_STATUS ->
                send(AaChannel.SENSOR, 0, AaSensor.EVENT, AaMessages.drivingStatusEvent())
            AaSensor.TYPE_NIGHT_MODE ->
                send(AaChannel.SENSOR, 0, AaSensor.EVENT, AaMessages.nightModeEvent(config.nightMode))
        }
        status("Sensor $type started")
    }

    private fun handleVideo(id: Int, msg: ByteArray, bodyLength: Int) {
        when (id) {
            AaMedia.DATA -> {
                // [id:2][timestamp:8][h264...]
                if (bodyLength > 8) {
                    var timestamp = 0L
                    for (i in 0 until 8) timestamp = (timestamp shl 8) or (msg[2 + i].toLong() and 0xFF)
                    onVideoData(msg, 10, msg.size - 10, timestamp)
                }
                ackVideo()
            }
            AaMedia.CODEC_CONFIG -> {
                listener.onVideoConfig(msg, 2, bodyLength)
                ackVideo()
            }
            AaMedia.SETUP_REQUEST -> {
                status("Video setup requested")
                send(AaChannel.VIDEO, 0, AaMedia.CONFIG_RESPONSE, AaMessages.mediaConfig(12))
                send(AaChannel.VIDEO, 0, AaMedia.VIDEO_FOCUS_NOTIFICATION, AaMessages.videoFocus(unsolicited = true))
            }
            AaMedia.START -> {
                videoSession = ProtoReader.parse(msg, 2, bodyLength).varint(1, 0).toInt()
                status("Video started (session $videoSession)")
                send(AaChannel.VIDEO, 0, AaMedia.VIDEO_FOCUS_NOTIFICATION, AaMessages.videoFocus(unsolicited = false))
            }
            AaMedia.STOP -> status("Video stopped by phone")
            AaMedia.VIDEO_FOCUS_REQUEST ->
                send(AaChannel.VIDEO, 0, AaMedia.VIDEO_FOCUS_NOTIFICATION, AaMessages.videoFocus(unsolicited = false))
            else -> OmoLog.d(TAG, "Unhandled video message $id")
        }
    }

    private fun onVideoData(data: ByteArray, offset: Int, length: Int, timestampUs: Long) {
        videoFrames++
        if (videoFrames == 1L) {
            status("First video data received")
            setPhase(Phase.STREAMING)
        }
        listener.onVideoFrame(data, offset, length, timestampUs)
    }

    private fun ackVideo() = send(AaChannel.VIDEO, 0, AaMedia.ACK, AaMessages.mediaAck(videoSession))

    private fun handleAudio(id: Int, msg: ByteArray, bodyLength: Int) {
        when (id) {
            AaMedia.DATA, AaMedia.CODEC_CONFIG ->
                send(AaChannel.AUDIO, 0, AaMedia.ACK, AaMessages.mediaAck(audioSession))
            AaMedia.SETUP_REQUEST -> send(AaChannel.AUDIO, 0, AaMedia.CONFIG_RESPONSE, AaMessages.mediaConfig(16))
            AaMedia.START -> audioSession = ProtoReader.parse(msg, 2, bodyLength).varint(1, 0).toInt()
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun send(channel: Int, flags: Int, messageId: Int, body: ByteArray, allowEncryption: Boolean = true) {
        synchronized(sendLock) {
            val out = output ?: throw IOException("Not connected")
            var payload = AaFrame.message(messageId, body)
            var frameFlags = flags
            if (allowEncryption && encrypted) {
                payload = (tls ?: throw IOException("TLS not ready")).encrypt(payload)
                frameFlags = frameFlags or AaFrame.FLAG_ENCRYPTED
            }
            out.write(AaFrame.encode(channel, frameFlags, payload))
            out.flush()
        }
    }

    private fun closeSocket() {
        runCatching { socket?.close() }
        socket = null
        output = null
    }

    private fun setPhase(value: Phase) {
        if (phase == value) return
        phase = value
        listener.onPhase(value)
    }

    private fun status(message: String) {
        OmoLog.i(TAG, message)
        listener.onStatus(message)
    }

    /** Waits for the reader thread to finish (tests / orderly shutdown). */
    fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean {
        reader?.join(unit.toMillis(timeout))
        return reader?.isAlive != true
    }

    /** The head unit server took the connection but never answered (see [EndReason.NOT_ANSWERING]). */
    private class NotAnsweringException(message: String) : IOException(message)

    private companion object {
        const val TAG = "OmoAA"
        const val CONNECT_TIMEOUT_MS = 3_000
        /** Android Auto normally answers the version request within milliseconds. */
        const val VERSION_TIMEOUT_MS = 4_000
        const val READ_TIMEOUT_MS = 10_000
        const val MAX_UNREACHABLE_ATTEMPTS = 6
    }
}
