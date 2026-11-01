// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.protocol.omoway

import java.io.DataInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * OMOWAY display ("head unit") projection protocol over Wi-Fi Direct.
 *
 * The motorcycle is the Wi-Fi Direct group owner and listens on two TCP ports:
 *  - [CONTROL_PORT] 9527: little-endian control packets
 *  - [VIDEO_PORT]   9528: little-endian H.264 video packets
 *
 * Control packet: `int32 type | int32 length | u16 id | body(length-2)` (all little endian).
 * See docs/PROTOCOL.md for the full message table.
 */
object OmoProtocol {
    const val CONTROL_PORT = 9527
    const val VIDEO_PORT = 9528

    const val TYPE_SYSTEM = 1
    const val TYPE_VIDEO = 2
    const val TYPE_PROJECTION = 7

    // TYPE_SYSTEM ids
    const val SYS_HELLO = 1
    /**
     * Connection status from the display, 1 byte. Status [STATUS_BUSY] (3) = another phone app
     * (usually the official OMOWAY app) holds the display; the display then closes the connection.
     * WayXAuto showed this as "last reply 7 / 3".
     */
    const val SYS_STATUS = 7
    const val STATUS_BUSY = 3
    const val SYS_ECHO = 9
    const val SYS_KEYFRAME_REQUEST = 40
    const val SYS_KEY_EXCHANGE = 49

    // TYPE_PROJECTION ids
    const val PRJ_READY = 1
    /** Display → phone: another phone app holds the display; the display closes this session. */
    const val PRJ_BUSY = 3
    const val PRJ_SHOW = 10
    const val PRJ_HIDE = 11
    const val PRJ_THEME_QUERY = 37
    const val PRJ_THEME = 39
    const val PRJ_FOREGROUND = 41

    const val MAX_CONTROL_BODY = 65_536

    /** Encodes one control packet. */
    fun encodeControl(type: Int, id: Int, body: ByteArray): ByteArray {
        val inner = body.size + 2
        return ByteBuffer.allocate(8 + inner).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(type)
            .putInt(inner)
            .putShort(id.toShort())
            .put(body)
            .array()
    }

    /**
     * Encodes one video packet.
     * ```
     * int32 type=2 | int32 length | u8 1 | u8 (1=key frame, 2=delta) | int64 ptsUs
     * [key frames only: u16 spsLen | sps | u16 ppsLen | pps]   (each WITH a 00 00 00 01 start code)
     * h264 access unit (Annex-B, 00 00 00 01 start codes)
     * ```
     * The parameter sets carry their 4-byte start code (lengths include it), exactly as WayXAuto
     * sent them. Without it a freshly started display cannot configure its decoder and stays
     * black; it only worked after WayXAuto had configured the decoder first. [sps] / [pps] may be
     * given with or without start code.
     */
    fun encodeVideo(frame: ByteArray, keyFrame: Boolean, ptsUs: Long, sps: ByteArray?, pps: ByteArray?): ByteArray {
        val spsAnnexB = sps?.let(::withStartCode)
        val ppsAnnexB = pps?.let(::withStartCode)
        if (keyFrame) {
            require(sps != null && pps != null && sps.isNotEmpty() && pps.isNotEmpty()) { "Key frame needs SPS and PPS" }
            require(spsAnnexB!!.size <= 0xFFFF && ppsAnnexB!!.size <= 0xFFFF) { "Oversized H.264 parameter set" }
        }
        @Suppress("NAME_SHADOWING") val sps = spsAnnexB
        @Suppress("NAME_SHADOWING") val pps = ppsAnnexB
        val config = if (keyFrame) 4 + sps!!.size + pps!!.size else 0
        val buffer = ByteBuffer.allocate(18 + config + frame.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(TYPE_VIDEO)
        buffer.putInt(10 + config + frame.size)
        buffer.put(1.toByte())
        buffer.put((if (keyFrame) 1 else 2).toByte())
        buffer.putLong(ptsUs)
        if (keyFrame) {
            buffer.putShort(sps!!.size.toShort()).put(sps)
            buffer.putShort(pps!!.size.toShort()).put(pps)
        }
        buffer.put(frame)
        return buffer.array()
    }

    private val START_CODE = byteArrayOf(0, 0, 0, 1)

    /** Prefixes a NAL unit with 00 00 00 01 unless it already starts with a 3- or 4-byte start code. */
    fun withStartCode(nal: ByteArray): ByteArray {
        val hasStart = (nal.size >= 4 && nal[0].toInt() == 0 && nal[1].toInt() == 0 && nal[2].toInt() == 0 && nal[3].toInt() == 1) ||
            (nal.size >= 3 && nal[0].toInt() == 0 && nal[1].toInt() == 0 && nal[2].toInt() == 1)
        return if (hasStart) nal else START_CODE + nal
    }

    /** The `hello` JSON exactly as the display expects it (org.json style, 4-space indent). */
    fun helloJson(width: Int, height: Int): String =
        "{\n    \"platform\": \"Android\",\n    \"screenWidth\": \"$width\",\n    \"screenHeight\": \"$height\"\n}"
}

/** One decoded control packet. */
class OmoControlPacket(val type: Int, val id: Int, val body: ByteArray) {
    override fun toString(): String = "OmoControlPacket(type=$type, id=$id, body=${body.size}B)"
}

class OmoControlReader(private val input: DataInputStream) {
    private val header = ByteArray(8)

    fun read(): OmoControlPacket {
        input.readFully(header)
        val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val type = bb.int
        val length = bb.int
        if (length < 0 || length > OmoProtocol.MAX_CONTROL_BODY) throw IOException("Unexpected control length $length")
        val payload = ByteArray(length)
        input.readFully(payload)
        if (length < 2) return OmoControlPacket(type, -1, payload)
        val id = ByteBuffer.wrap(payload, 0, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
        return OmoControlPacket(type, id, payload.copyOfRange(2, length))
    }
}
