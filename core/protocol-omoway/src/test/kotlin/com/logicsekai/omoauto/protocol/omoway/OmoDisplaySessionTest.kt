// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.protocol.omoway

import org.junit.Test
import java.io.DataInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Plays the motorcycle display on two loopback ports and checks the whole projection handshake. */
class OmoDisplaySessionTest {

    private class Recorder : OmoDisplaySession.Listener {
        val ready = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val hidden = CountDownLatch(1)
        val busy = CountDownLatch(1)
        @Volatile var keyFrameRequests = 0
        @Volatile var closeReason = ""
        @Volatile var closedWhileReady: Boolean? = null
        override fun onProjectionReady() = ready.countDown()
        override fun onKeyFrameRequested() { keyFrameRequests++ }
        override fun onVisibilityChanged(visible: Boolean) { if (!visible) hidden.countDown() }
        override fun onBusy() = busy.countDown()
        override fun onClosed(reason: String, wasReady: Boolean) {
            closeReason = reason
            closedWhileReady = wasReady
            closed.countDown()
        }
    }

    @Test
    fun `hello, key exchange, echo, theme, video and hide`() {
        val loopback = InetAddress.getLoopbackAddress()
        ServerSocket(0, 1, loopback).use { controlServer ->
            ServerSocket(0, 1, loopback).use { videoServer ->
                controlServer.soTimeout = 5_000
                videoServer.soTimeout = 5_000
                val recorder = Recorder()
                val session = OmoDisplaySession(
                    host = loopback.hostAddress,
                    config = OmoDisplaySession.Config(
                        width = 960, height = 360, theme = "night", boundedLiveQueue = false,
                        controlPort = controlServer.localPort, videoPort = videoServer.localPort,
                    ),
                    listener = recorder,
                )
                session.start()

                val control = controlServer.accept().apply { soTimeout = 5_000 }
                val video = videoServer.accept().apply { soTimeout = 5_000 }
                val reader = OmoControlReader(DataInputStream(control.getInputStream()))
                val out = control.getOutputStream()

                val hello = reader.read()
                assertEquals(OmoProtocol.TYPE_SYSTEM to OmoProtocol.SYS_HELLO, hello.type to hello.id)
                assertTrue(String(hello.body).contains("\"screenWidth\": \"960\""))
                val foreground = reader.read()
                assertEquals(OmoProtocol.TYPE_PROJECTION to OmoProtocol.PRJ_FOREGROUND, foreground.type to foreground.id)

                // Display sends its EC public key → we answer with ours and become ready.
                val displayKeys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
                out.write(OmoProtocol.encodeControl(OmoProtocol.TYPE_SYSTEM, OmoProtocol.SYS_KEY_EXCHANGE, displayKeys.public.encoded))
                out.flush()
                val keyReply = reader.read()
                assertEquals(OmoProtocol.SYS_KEY_EXCHANGE, keyReply.id)
                KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(keyReply.body)) // parses
                val readyPacket = reader.read()
                assertEquals(OmoProtocol.TYPE_PROJECTION to OmoProtocol.PRJ_READY, readyPacket.type to readyPacket.id)
                assertEquals("""{"code":0}""", String(readyPacket.body))
                assertTrue(recorder.ready.await(2, TimeUnit.SECONDS))

                // Heartbeat echo
                out.write(OmoProtocol.encodeControl(OmoProtocol.TYPE_SYSTEM, OmoProtocol.SYS_ECHO, byteArrayOf(5, 6)))
                out.flush()
                val echo = reader.read()
                assertEquals(OmoProtocol.SYS_ECHO, echo.id)
                assertEquals(listOf<Byte>(5, 6), echo.body.toList())

                // Theme query
                out.write(OmoProtocol.encodeControl(OmoProtocol.TYPE_PROJECTION, OmoProtocol.PRJ_THEME_QUERY, ByteArray(0)))
                out.flush()
                val theme = reader.read()
                assertEquals(OmoProtocol.PRJ_THEME, theme.id)
                assertEquals("""{"mode":"night"}""", String(theme.body))

                // A delta frame before any key frame is suppressed; the key frame goes through.
                session.submitFrame(byteArrayOf(0, 0, 0, 1, 0x41), keyFrame = false, ptsUs = 1, sps = null, pps = null)
                session.submitFrame(byteArrayOf(0, 0, 0, 1, 0x65), keyFrame = true, ptsUs = 2, sps = byteArrayOf(0x67), pps = byteArrayOf(0x68))
                val videoIn = DataInputStream(video.getInputStream())
                val header = ByteArray(8).also { videoIn.readFully(it) }
                val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                assertEquals(2, bb.int)
                val length = bb.int
                val body = ByteArray(length).also { videoIn.readFully(it) }
                assertEquals(1, body[1].toInt()) // key frame marker
                // Wire pts is the encoder's timestamp, as WayXAuto sent it.
                assertEquals(2L, ByteBuffer.wrap(body, 2, 8).order(ByteOrder.LITTLE_ENDIAN).long)
                assertEquals(1L, session.stats().framesSent)
                assertEquals(1L, session.stats().framesSuppressed)

                // A later frame carries its own timestamp.
                session.submitFrame(byteArrayOf(0, 0, 0, 1, 0x41), keyFrame = false, ptsUs = 1_000_002, sps = null, pps = null)
                val header2 = ByteArray(8).also { videoIn.readFully(it) }
                val body2 = ByteArray(ByteBuffer.wrap(header2, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int).also { videoIn.readFully(it) }
                assertEquals(2, body2[1].toInt())
                assertEquals(1_000_002L, ByteBuffer.wrap(body2, 2, 8).order(ByteOrder.LITTLE_ENDIAN).long)

                // Hide → frames are suppressed
                out.write(OmoProtocol.encodeControl(OmoProtocol.TYPE_PROJECTION, OmoProtocol.PRJ_HIDE, ByteArray(0)))
                out.flush()
                assertTrue(recorder.hidden.await(2, TimeUnit.SECONDS))
                session.submitFrame(byteArrayOf(0, 0, 0, 1, 0x41), keyFrame = false, ptsUs = 3, sps = null, pps = null)
                assertEquals(2L, session.stats().framesSent)

                control.close()
                assertTrue(recorder.closed.await(5, TimeUnit.SECONDS))
                assertEquals(true, recorder.closedWhileReady)
                video.close()
            }
        }
    }

    /** Another phone app holds the display: it answers the hello with 7/3 and hangs up. */
    @Test
    fun `busy display reports onBusy and closes before ready`() {
        val loopback = InetAddress.getLoopbackAddress()
        ServerSocket(0, 1, loopback).use { controlServer ->
            ServerSocket(0, 1, loopback).use { videoServer ->
                controlServer.soTimeout = 5_000
                videoServer.soTimeout = 5_000
                val recorder = Recorder()
                val session = OmoDisplaySession(
                    host = loopback.hostAddress,
                    config = OmoDisplaySession.Config(
                        width = 960, height = 360,
                        controlPort = controlServer.localPort, videoPort = videoServer.localPort,
                    ),
                    listener = recorder,
                )
                session.start()
                val control = controlServer.accept().apply { soTimeout = 5_000 }
                val video = videoServer.accept()
                val reader = OmoControlReader(DataInputStream(control.getInputStream()))
                reader.read() // hello
                reader.read() // foreground
                control.getOutputStream().apply {
                    write(OmoProtocol.encodeControl(OmoProtocol.TYPE_PROJECTION, OmoProtocol.PRJ_BUSY, ByteArray(0)))
                    flush()
                }
                assertTrue(recorder.busy.await(2, TimeUnit.SECONDS))
                control.close()
                assertTrue(recorder.closed.await(5, TimeUnit.SECONDS))
                assertEquals(false, recorder.closedWhileReady)
                assertTrue(recorder.closeReason.contains("7/3"), recorder.closeReason)
                video.close()
            }
        }
    }

    /** What the real display sends when the OMOWAY app holds it: system 7 with status byte 3. */
    @Test
    fun `status 1-7 with code 3 is reported as busy`() {
        val loopback = InetAddress.getLoopbackAddress()
        ServerSocket(0, 1, loopback).use { controlServer ->
            ServerSocket(0, 1, loopback).use { videoServer ->
                controlServer.soTimeout = 5_000
                videoServer.soTimeout = 5_000
                val recorder = Recorder()
                val session = OmoDisplaySession(
                    host = loopback.hostAddress,
                    config = OmoDisplaySession.Config(
                        width = 960, height = 360,
                        controlPort = controlServer.localPort, videoPort = videoServer.localPort,
                    ),
                    listener = recorder,
                )
                session.start()
                val control = controlServer.accept().apply { soTimeout = 5_000 }
                val video = videoServer.accept()
                val reader = OmoControlReader(DataInputStream(control.getInputStream()))
                reader.read() // hello
                reader.read() // foreground
                control.getOutputStream().apply {
                    write(OmoProtocol.encodeControl(OmoProtocol.TYPE_SYSTEM, OmoProtocol.SYS_STATUS, byteArrayOf(3)))
                    flush()
                }
                assertTrue(recorder.busy.await(2, TimeUnit.SECONDS))
                control.close()
                assertTrue(recorder.closed.await(5, TimeUnit.SECONDS))
                assertEquals(false, recorder.closedWhileReady)
                assertTrue(recorder.closeReason.contains("1/7"), recorder.closeReason)
                video.close()
            }
        }
    }
}
