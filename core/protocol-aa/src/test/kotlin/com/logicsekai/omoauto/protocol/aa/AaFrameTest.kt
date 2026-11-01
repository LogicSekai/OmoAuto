// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.protocol.aa

import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AaFrameTest {

    @Test
    fun `single frame encodes header and round trips`() {
        val payload = AaFrame.message(AaControl.VERSION_REQUEST, AaMessages.versionRequest())
        val frame = AaFrame.encode(AaChannel.CONTROL, 0, payload)
        assertContentEquals(byteArrayOf(0, 3, 0, 6, 0, 1, 0, 1, 0, 1), frame)

        val read = AaFrameReader(DataInputStream(ByteArrayInputStream(frame))).read()
        assertEquals(0, read.channel)
        assertEquals(3, read.flags)
        assertContentEquals(payload, read.payload)
    }

    @Test
    fun `fragmented frame carries total length and is reassembled`() {
        val first = byteArrayOf(2, AaFrame.FLAG_FIRST.toByte(), 0, 2, 0, 0, 0, 5, 10, 11)
        val middle = byteArrayOf(2, 0, 0, 2, 12, 13)
        val last = byteArrayOf(2, AaFrame.FLAG_LAST.toByte(), 0, 1, 14)
        val reader = AaFrameReader(DataInputStream(ByteArrayInputStream(first + middle + last)))
        val assembler = FrameAssembler()

        val f1 = reader.read()
        assertNull(assembler.accept(f1.channel, f1.flags, f1.payload))
        val f2 = reader.read()
        assertNull(assembler.accept(f2.channel, f2.flags, f2.payload))
        val f3 = reader.read()
        assertContentEquals(byteArrayOf(10, 11, 12, 13, 14), assembler.accept(f3.channel, f3.flags, f3.payload))
    }
}
