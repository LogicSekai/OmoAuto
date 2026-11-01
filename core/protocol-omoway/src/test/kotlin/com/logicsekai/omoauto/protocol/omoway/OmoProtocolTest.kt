// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.protocol.omoway

import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OmoProtocolTest {

    @Test
    fun `control packet layout is little endian with u16 id`() {
        val packet = OmoProtocol.encodeControl(7, 41, """{"foreground":"1"}""".toByteArray())
        val bb = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(7, bb.int)
        assertEquals(20, bb.int) // 2 id bytes + 18 body bytes
        assertEquals(41, bb.short.toInt())

        val decoded = OmoControlReader(DataInputStream(ByteArrayInputStream(packet))).read()
        assertEquals(7, decoded.type)
        assertEquals(41, decoded.id)
        assertEquals("""{"foreground":"1"}""", String(decoded.body))
    }

    @Test
    fun `key frame video packet carries sps and pps`() {
        val sps = byteArrayOf(0x67, 1, 2)
        val pps = byteArrayOf(0x68, 3)
        val frame = byteArrayOf(0, 0, 0, 1, 0x65, 7)
        val packet = OmoProtocol.encodeVideo(frame, keyFrame = true, ptsUs = 1_000, sps = sps, pps = pps)
        val bb = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(2, bb.int)
        // SPS/PPS go on the wire with a 00 00 00 01 start code, as WayXAuto sent them.
        val spsWire = byteArrayOf(0, 0, 0, 1) + sps
        val ppsWire = byteArrayOf(0, 0, 0, 1) + pps
        assertEquals(10 + 4 + spsWire.size + ppsWire.size + frame.size, bb.int)
        assertEquals(1, bb.get().toInt())
        assertEquals(1, bb.get().toInt())
        assertEquals(1_000L, bb.long)
        assertEquals(spsWire.size, bb.short.toInt())
        assertContentEquals(spsWire, ByteArray(spsWire.size).also { bb.get(it) })
        assertEquals(ppsWire.size, bb.short.toInt())
        assertContentEquals(ppsWire, ByteArray(ppsWire.size).also { bb.get(it) })
        assertContentEquals(frame, ByteArray(bb.remaining()).also { bb.get(it) })
    }

    @Test
    fun `delta frame has no parameter sets`() {
        val packet = OmoProtocol.encodeVideo(byteArrayOf(1, 2, 3), keyFrame = false, ptsUs = 5, sps = null, pps = null)
        assertEquals(18 + 3, packet.size)
        assertEquals(2, packet[9].toInt())
    }

    @Test
    fun `key frame without parameter sets is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            OmoProtocol.encodeVideo(byteArrayOf(1), keyFrame = true, ptsUs = 0, sps = null, pps = null)
        }
    }

    @Test
    fun `hello json matches org json indent 4`() {
        assertEquals(
            "{\n    \"platform\": \"Android\",\n    \"screenWidth\": \"960\",\n    \"screenHeight\": \"360\"\n}",
            OmoProtocol.helloJson(960, 360),
        )
    }

    @Test
    fun `parameter sets that already have a start code are not prefixed twice`() {
        val annexB = byteArrayOf(0, 0, 0, 1, 0x67, 1)
        assertContentEquals(annexB, OmoProtocol.withStartCode(annexB))
        assertContentEquals(byteArrayOf(0, 0, 1, 0x68), OmoProtocol.withStartCode(byteArrayOf(0, 0, 1, 0x68)))
        assertContentEquals(byteArrayOf(0, 0, 0, 1, 0x68, 2), OmoProtocol.withStartCode(byteArrayOf(0x68, 2)))
    }
}
