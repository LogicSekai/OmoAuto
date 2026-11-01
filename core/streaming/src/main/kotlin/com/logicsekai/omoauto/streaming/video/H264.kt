// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.streaming.video

import java.io.ByteArrayOutputStream

/** Helpers for H.264 elementary streams. */
object H264 {
    const val NAL_IDR = 5
    const val NAL_SPS = 7
    const val NAL_PPS = 8

    private val START_CODE = byteArrayOf(0, 0, 0, 1)

    /**
     * Splits an Annex-B buffer into NAL units (without start codes). If no start code is found the
     * buffer is treated as AVCC (4-byte big-endian length prefixes).
     */
    fun splitNalUnits(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): List<ByteArray> {
        val end = offset + length
        val starts = ArrayList<Pair<Int, Int>>() // (startCodeIndex, payloadIndex)
        var i = offset
        while (i + 3 <= end) {
            if (data[i].toInt() == 0 && data[i + 1].toInt() == 0) {
                if (data[i + 2].toInt() == 1) {
                    starts += i to i + 3
                    i += 3
                    continue
                }
                if (i + 4 <= end && data[i + 2].toInt() == 0 && data[i + 3].toInt() == 1) {
                    starts += i to i + 4
                    i += 4
                    continue
                }
            }
            i++
        }
        if (starts.isEmpty()) return splitAvcc(data, offset, end)
        return starts.mapIndexed { index, (_, payload) ->
            val stop = if (index + 1 < starts.size) starts[index + 1].first else end
            data.copyOfRange(payload, stop)
        }.filter { it.isNotEmpty() }
    }

    private fun splitAvcc(data: ByteArray, offset: Int, end: Int): List<ByteArray> {
        val out = ArrayList<ByteArray>()
        var pos = offset
        while (pos + 4 <= end) {
            val len = ((data[pos].toInt() and 0xFF) shl 24) or ((data[pos + 1].toInt() and 0xFF) shl 16) or
                ((data[pos + 2].toInt() and 0xFF) shl 8) or (data[pos + 3].toInt() and 0xFF)
            pos += 4
            if (len <= 0 || pos + len > end) break
            out += data.copyOfRange(pos, pos + len)
            pos += len
        }
        if (out.isEmpty() && end > offset) out += data.copyOfRange(offset, end)
        return out
    }

    fun nalType(nal: ByteArray): Int = if (nal.isEmpty()) -1 else nal[0].toInt() and 0x1F

    /** Re-emits NAL units with 4-byte start codes. */
    fun toAnnexB(nals: List<ByteArray>): ByteArray {
        val out = ByteArrayOutputStream(nals.sumOf { it.size + 4 })
        nals.forEach {
            out.write(START_CODE)
            out.write(it)
        }
        return out.toByteArray()
    }

    /** First NAL of [type] inside [data] (Annex-B or AVCC), without start code. */
    fun findNal(data: ByteArray, type: Int): ByteArray? = splitNalUnits(data).firstOrNull { nalType(it) == type }
}
