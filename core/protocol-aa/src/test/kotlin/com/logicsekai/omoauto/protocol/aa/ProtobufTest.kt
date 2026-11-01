// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.protocol.aa

import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProtobufTest {

    @Test
    fun `varint, string and nested message round trip`() {
        val bytes = ProtoWriter()
            .varint(1, 150)
            .string(2, "OmoAuto")
            .message(3) { varint(1, 7).bool(2, true) }
            .message(3) { varint(1, 8) }
            .toByteArray()

        val msg = ProtoReader.parse(bytes)
        assertEquals(150L, msg.varint(1))
        assertEquals("OmoAuto", msg.string(2))
        val nested = msg.messages(3)
        assertEquals(2, nested.size)
        assertEquals(7L, nested[0].varint(1))
        assertEquals(1L, nested[0].varint(2))
        assertEquals(8L, nested[1].varint(1))
        assertNull(msg.bytes(9))
    }

    @Test
    fun `known encoding of field 1 = 150`() {
        assertContentEquals(byteArrayOf(0x08, 0x96.toByte(), 0x01), ProtoWriter().varint(1, 150).toByteArray())
    }

    @Test
    fun `negative int32 uses ten byte two's complement varint`() {
        val bytes = ProtoWriter().varint(2, -1L).toByteArray()
        assertEquals(11, bytes.size)
        assertEquals(-1L, ProtoReader.parse(bytes).varint(2))
    }

    @Test
    fun `parse with offset skips message id`() {
        val body = ProtoWriter().varint(1, 42).toByteArray()
        val msg = AaFrame.message(0x8001, body)
        assertEquals(42L, ProtoReader.parse(msg, 2, msg.size - 2).varint(1))
    }

    @Test
    fun `service discovery response announces five services and video margins`() {
        val config = HeadUnitConfig(VideoConfig(1280, 720, marginWidth = 160, marginHeight = 360, density = 160))
        val msg = ProtoReader.parse(AaMessages.serviceDiscoveryResponse(config))
        val services = msg.messages(1)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), services.map { it.varint(1) })
        val video = services[1].message(3)!!.message(4)!!
        assertEquals(2L, video.varint(1))
        assertEquals(160L, video.varint(3))
        assertEquals(360L, video.varint(4))
        assertEquals("OmoAuto", msg.string(3))
        assertTrue(services[2].message(4)!!.has(1))
        // Touch screen announced with the video frame size
        val touch = services[2].message(4)!!.message(2)!!
        assertEquals(1280L, touch.varint(1))
        assertEquals(720L, touch.varint(2))
        // ...and left out when disabled
        val noTouch = ProtoReader.parse(AaMessages.serviceDiscoveryResponse(config.copy(touchscreen = false)))
        assertEquals(false, noTouch.messages(1)[2].message(4)!!.has(2))
    }

    @Test
    fun `touch event carries one pointer and the action`() {
        val msg = ProtoReader.parse(AaMessages.touchEvent(timestamp = 99, action = AaTouchAction.UP, x = 640, y = 300))
        assertEquals(99L, msg.varint(1))
        val touch = msg.message(3)!!
        val pointer = touch.message(1)!!
        assertEquals(640L, pointer.varint(1))
        assertEquals(300L, pointer.varint(2))
        assertEquals(0L, pointer.varint(3))
        assertEquals(AaTouchAction.UP.toLong(), touch.varint(3))
    }
}
