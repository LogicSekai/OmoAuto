// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.protocol.aa

import com.logicsekai.omoauto.common.u16be
import org.junit.Test
import java.io.DataInputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drives [AaHeadUnitSession] against a fake Android Auto phone that speaks the real framing,
 * a real TLS handshake (server side) and the media/control messages.
 */
class AaHeadUnitSessionTest {

    private class Recorder : AaHeadUnitSession.Listener {
        val configs = LinkedBlockingQueue<ByteArray>()
        val frames = LinkedBlockingQueue<Pair<ByteArray, Long>>()
        val ended = CountDownLatch(1)
        val inputBound = CountDownLatch(1)
        @Volatile var endReason: AaHeadUnitSession.EndReason? = null

        override fun onVideoConfig(data: ByteArray, offset: Int, length: Int) {
            configs.add(data.copyOfRange(offset, offset + length))
        }

        override fun onVideoFrame(data: ByteArray, offset: Int, length: Int, timestampUs: Long) {
            frames.add(data.copyOfRange(offset, offset + length) to timestampUs)
        }

        override fun onInputBound() = inputBound.countDown()

        override fun onEnded(reason: AaHeadUnitSession.EndReason, detail: String) {
            endReason = reason
            ended.countDown()
        }
    }

    /** Minimal phone: TLS server + AA framing. */
    private class FakePhone : AutoCloseable {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        private lateinit var socket: Socket
        private lateinit var out: OutputStream
        private lateinit var reader: AaFrameReader
        private val assembler = FrameAssembler()
        private val tls = TlsBridge(TlsBridge.createEngine(TestKeys.phoneIdentity(), clientMode = false))
        private var encrypted = false

        fun accept() {
            server.soTimeout = 5_000
            socket = server.accept()
            socket.soTimeout = 5_000
            out = socket.getOutputStream()
            reader = AaFrameReader(DataInputStream(socket.getInputStream()))
        }

        /** Returns (channel, flags, message). */
        fun read(): Triple<Int, Int, ByteArray> {
            while (true) {
                val frame = reader.read()
                val payload = if (frame.isEncrypted) tls.decrypt(frame.payload) else frame.payload
                val msg = assembler.accept(frame.channel, frame.flags, payload) ?: continue
                return Triple(frame.channel, frame.flags, msg)
            }
        }

        fun send(channel: Int, id: Int, body: ByteArray, flags: Int = 0) {
            var payload = AaFrame.message(id, body)
            var f = flags
            if (encrypted) {
                payload = tls.encrypt(payload)
                f = f or AaFrame.FLAG_ENCRYPTED
            }
            out.write(AaFrame.encode(channel, f, payload))
            out.flush()
        }

        fun handshake() {
            val (ch, _, version) = read()
            assertEquals(AaChannel.CONTROL, ch)
            assertEquals(AaControl.VERSION_REQUEST, version.u16be(0))
            send(AaChannel.CONTROL, AaControl.VERSION_RESPONSE, byteArrayOf(0, 1, 0, 1, 0, 0))
            tls.begin()
            while (true) {
                val (_, _, msg) = read()
                when (msg.u16be(0)) {
                    AaControl.SSL_HANDSHAKE -> {
                        val reply = tls.onHandshakeData(msg.copyOfRange(2, msg.size))
                        if (reply.isNotEmpty()) send(AaChannel.CONTROL, AaControl.SSL_HANDSHAKE, reply)
                    }
                    AaControl.AUTH_COMPLETE -> {
                        assertTrue(tls.isHandshakeComplete, "phone side handshake finished")
                        encrypted = true
                        return
                    }
                    else -> error("Unexpected message during handshake: ${msg.u16be(0)}")
                }
            }
        }

        override fun close() {
            runCatching { socket.close() }
            server.close()
        }
    }

    @Test
    fun `full session - handshake, discovery, video, input, phone exit`() {
        FakePhone().use { phone ->
            val recorder = Recorder()
            val config = HeadUnitConfig(
                video = VideoConfig(1280, 720, marginWidth = 160, marginHeight = 360, density = 160),
                port = phone.server.localPort,
            )
            val session = AaHeadUnitSession({ TestKeys.headUnitIdentity() }, config, recorder)
            assertTrue(session.start())

            phone.accept()
            phone.handshake()

            // Service discovery
            phone.send(AaChannel.CONTROL, AaControl.SERVICE_DISCOVERY_REQUEST, ProtoWriter().string(4, "Test phone").toByteArray())
            val (_, _, discovery) = phone.read()
            assertEquals(AaControl.SERVICE_DISCOVERY_RESPONSE, discovery.u16be(0))
            val services = ProtoReader.parse(discovery, 2, discovery.size - 2).messages(1)
            assertEquals(5, services.size)

            // Open video channel and set it up
            phone.send(AaChannel.VIDEO, AaControl.CHANNEL_OPEN_REQUEST, ProtoWriter().varint(1, 0).varint(2, 2).toByteArray(), AaFrame.FLAG_CONTROL)
            val (openCh, openFlags, openResp) = phone.read()
            assertEquals(AaChannel.VIDEO, openCh)
            assertTrue(openFlags and AaFrame.FLAG_CONTROL != 0)
            assertEquals(AaControl.CHANNEL_OPEN_RESPONSE, openResp.u16be(0))

            phone.send(AaChannel.VIDEO, AaMedia.SETUP_REQUEST, ProtoWriter().varint(1, 3).toByteArray())
            assertEquals(AaMedia.CONFIG_RESPONSE, phone.read().third.u16be(0))
            assertEquals(AaMedia.VIDEO_FOCUS_NOTIFICATION, phone.read().third.u16be(0))

            phone.send(AaChannel.VIDEO, AaMedia.START, ProtoWriter().varint(1, 7).varint(2, 0).toByteArray())
            assertEquals(AaMedia.VIDEO_FOCUS_NOTIFICATION, phone.read().third.u16be(0))

            // Codec config + one frame with timestamp
            val sps = byteArrayOf(0, 0, 0, 1, 0x67, 1, 2, 3)
            phone.send(AaChannel.VIDEO, AaMedia.CODEC_CONFIG, sps)
            val ack1 = phone.read().third
            assertEquals(AaMedia.ACK, ack1.u16be(0))
            assertEquals(7L, ProtoReader.parse(ack1, 2, ack1.size - 2).varint(1))

            val ts = 123_456_789L
            val tsBytes = ByteArray(8) { i -> (ts ushr (56 - 8 * i)).toByte() }
            val nal = byteArrayOf(0, 0, 0, 1, 0x65, 9, 9, 9)
            phone.send(AaChannel.VIDEO, AaMedia.DATA, tsBytes + nal)
            assertEquals(AaMedia.ACK, phone.read().third.u16be(0))

            assertContentEquals(sps, recorder.configs.poll(2, TimeUnit.SECONDS))
            val frame = recorder.frames.poll(2, TimeUnit.SECONDS)!!
            assertContentEquals(nal, frame.first)
            assertEquals(ts, frame.second)

            // Ping
            phone.send(AaChannel.CONTROL, AaControl.PING_REQUEST, ProtoWriter().varint(1, 99).toByteArray())
            val pong = phone.read().third
            assertEquals(AaControl.PING_RESPONSE, pong.u16be(0))
            assertEquals(99L, ProtoReader.parse(pong, 2, pong.size - 2).varint(1))

            // Input binding then a key press (down + up)
            phone.send(AaChannel.INPUT, AaInput.BINDING_REQUEST, ProtoWriter().varint(1, 19).toByteArray())
            assertEquals(AaInput.BINDING_RESPONSE, phone.read().third.u16be(0))
            assertTrue(recorder.inputBound.await(2, TimeUnit.SECONDS))
            session.sendKey(AaKeyCode.DPAD_CENTER)
            repeat(2) { assertEquals(AaInput.EVENT, phone.read().third.u16be(0)) }

            // A tap from the phone preview arrives as an encrypted touch event on the input channel
            session.sendTouch(AaTouchAction.DOWN, 640, 360)
            val (channel, _, touchMsg) = phone.read()
            assertEquals(AaChannel.INPUT, channel)
            assertEquals(AaInput.EVENT, touchMsg.u16be(0))
            val pointer = ProtoReader.parse(touchMsg, 2, touchMsg.size - 2).message(3)!!.message(1)!!
            assertEquals(640L to 360L, pointer.varint(1) to pointer.varint(2))

            // Phone exits Android Auto
            phone.send(AaChannel.CONTROL, AaControl.SHUTDOWN_REQUEST, ProtoWriter().varint(1, 1).toByteArray())
            assertEquals(AaControl.SHUTDOWN_RESPONSE, phone.read().third.u16be(0))
            assertTrue(recorder.ended.await(5, TimeUnit.SECONDS))
            assertEquals(AaHeadUnitSession.EndReason.PHONE_EXIT, recorder.endReason)
        }
    }

    @Test
    fun `missing keys end the session immediately`() {
        val recorder = Recorder()
        val session = AaHeadUnitSession({ null }, HeadUnitConfig(VideoConfig(1280, 720, 160, 360, 160)), recorder)
        assertEquals(false, session.start())
        assertEquals(AaHeadUnitSession.EndReason.NO_KEYS, recorder.endReason)
    }

    /**
     * A frozen Android Auto (seen on HyperOS / Android 16) accepts the TCP connection but never
     * answers. The session must give up after one connection instead of piling up retries.
     */
    @Test
    fun `server that never answers ends with NOT_ANSWERING after a single connection`() {
        val server = ServerSocket(0, 8, InetAddress.getLoopbackAddress())
        val accepted = java.util.concurrent.atomic.AtomicInteger()
        val held = java.util.Collections.synchronizedList(mutableListOf<Socket>())
        val acceptor = Thread {
            try {
                while (true) {
                    held += server.accept() // keep it open, read nothing, answer nothing
                    accepted.incrementAndGet()
                }
            } catch (_: Exception) {
            }
        }.apply { isDaemon = true; start() }
        try {
            val recorder = Recorder()
            val session = AaHeadUnitSession(
                { TestKeys.headUnitIdentity() },
                HeadUnitConfig(VideoConfig(1280, 720, 160, 360, 160), port = server.localPort),
                recorder,
            )
            val startedAt = System.nanoTime()
            assertTrue(session.start())
            assertTrue(recorder.ended.await(10, TimeUnit.SECONDS), "session should end on its own")
            val seconds = (System.nanoTime() - startedAt) / 1e9
            assertEquals(AaHeadUnitSession.EndReason.NOT_ANSWERING, recorder.endReason)
            assertEquals(1, accepted.get(), "no retries against a server that does not answer")
            assertTrue(seconds in 3.5..8.0, "gave up after the version timeout, took $seconds s")
        } finally {
            server.close()
            held.forEach { runCatching { it.close() } }
            acceptor.interrupt()
        }
    }
}
