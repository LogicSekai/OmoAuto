// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.streaming.video

import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class H264Test {

    private val sps = byteArrayOf(0x67, 0x42, 0x00, 0x1F)
    private val pps = byteArrayOf(0x68, 0xCE.toByte(), 0x3C)
    private val idr = byteArrayOf(0x65, 1, 2, 3)

    @Test
    fun `splits annex b with 3 and 4 byte start codes`() {
        val stream = byteArrayOf(0, 0, 0, 1) + sps + byteArrayOf(0, 0, 1) + pps + byteArrayOf(0, 0, 0, 1) + idr
        val nals = H264.splitNalUnits(stream)
        assertEquals(3, nals.size)
        assertEquals(listOf(7, 8, 5), nals.map(H264::nalType))
        assertContentEquals(pps, H264.findNal(stream, H264.NAL_PPS))
    }

    @Test
    fun `avcc length prefixed input is understood`() {
        val avcc = byteArrayOf(0, 0, 0, sps.size.toByte()) + sps + byteArrayOf(0, 0, 0, idr.size.toByte()) + idr
        assertEquals(listOf(7, 5), H264.splitNalUnits(avcc).map(H264::nalType))
    }

    @Test
    fun `to annex b re-adds 4 byte start codes`() {
        val out = H264.toAnnexB(listOf(sps, idr))
        assertContentEquals(byteArrayOf(0, 0, 0, 1) + sps + byteArrayOf(0, 0, 0, 1) + idr, out)
    }
}
