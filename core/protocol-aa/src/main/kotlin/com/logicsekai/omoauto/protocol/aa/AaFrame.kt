// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.protocol.aa

import java.io.ByteArrayOutputStream
import java.io.DataInputStream

/**
 * Android Auto transport framing.
 *
 * ```
 * byte 0      channel id
 * byte 1      flags  (FIRST=1, LAST=2, CONTROL=4, ENCRYPTED=8)
 * bytes 2..3  payload length, big endian
 * [bytes 4..7 total message length, only when FIRST is set without LAST]
 * payload     (TLS record when ENCRYPTED)
 * ```
 * A decoded message payload starts with a 16-bit big-endian message id.
 */
object AaFrame {
    const val FLAG_FIRST = 0x01
    const val FLAG_LAST = 0x02
    const val FLAG_CONTROL = 0x04
    const val FLAG_ENCRYPTED = 0x08

    const val MAX_PAYLOAD = 0xFFFF

    /** Encodes a single, unfragmented frame. */
    fun encode(channel: Int, flags: Int, payload: ByteArray): ByteArray {
        require(payload.size <= MAX_PAYLOAD) { "Frame payload too large: ${payload.size}" }
        val f = flags or FLAG_FIRST or FLAG_LAST
        val frame = ByteArray(payload.size + 4)
        frame[0] = channel.toByte()
        frame[1] = f.toByte()
        frame[2] = (payload.size shr 8).toByte()
        frame[3] = payload.size.toByte()
        System.arraycopy(payload, 0, frame, 4, payload.size)
        return frame
    }

    /** Prefixes [body] with the 16-bit big-endian [messageId]. */
    fun message(messageId: Int, body: ByteArray): ByteArray {
        val out = ByteArray(body.size + 2)
        out[0] = (messageId shr 8).toByte()
        out[1] = messageId.toByte()
        System.arraycopy(body, 0, out, 2, body.size)
        return out
    }
}

/** One frame as it came off the wire (payload still encrypted when [flags] has ENCRYPTED). */
class RawFrame(val channel: Int, val flags: Int, val payload: ByteArray) {
    val isEncrypted: Boolean get() = flags and AaFrame.FLAG_ENCRYPTED != 0
}

class AaFrameReader(private val input: DataInputStream) {
    fun read(): RawFrame {
        val channel = input.readUnsignedByte()
        val flags = input.readUnsignedByte()
        val length = input.readUnsignedShort()
        val first = flags and AaFrame.FLAG_FIRST != 0
        val last = flags and AaFrame.FLAG_LAST != 0
        if (first && !last) input.readInt() // total length of the fragmented message; not needed
        val payload = ByteArray(length)
        input.readFully(payload)
        return RawFrame(channel, flags, payload)
    }
}

/** Joins fragmented messages per channel. Feed decrypted payloads in arrival order. */
class FrameAssembler {
    private val partial = HashMap<Int, ByteArrayOutputStream>()

    /** Returns the complete message, or null while more fragments are expected. */
    fun accept(channel: Int, flags: Int, payload: ByteArray): ByteArray? {
        val first = flags and AaFrame.FLAG_FIRST != 0
        val last = flags and AaFrame.FLAG_LAST != 0
        if (first && last) {
            partial.remove(channel)
            return payload
        }
        var buffer = partial[channel]
        if (first || buffer == null) {
            buffer = ByteArrayOutputStream(payload.size * 4)
            partial[channel] = buffer
        }
        buffer.write(payload)
        if (!last) return null
        partial.remove(channel)
        return buffer.toByteArray()
    }

    fun clear() = partial.clear()
}
