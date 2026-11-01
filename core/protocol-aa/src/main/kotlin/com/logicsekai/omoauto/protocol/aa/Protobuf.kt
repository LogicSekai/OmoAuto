// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.protocol.aa

import java.io.ByteArrayOutputStream

/**
 * Minimal protobuf (proto2 wire format) writer. Android Auto messages are small and we only need
 * varint and length-delimited fields, so a full protobuf runtime is unnecessary.
 */
class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun varint(field: Int, value: Long): ProtoWriter = apply {
        writeTag(field, WIRE_VARINT)
        writeVarint(value)
    }

    fun varint(field: Int, value: Int): ProtoWriter = varint(field, value.toLong())

    fun bool(field: Int, value: Boolean): ProtoWriter = varint(field, if (value) 1L else 0L)

    fun bytes(field: Int, value: ByteArray): ProtoWriter = apply {
        writeTag(field, WIRE_LENGTH_DELIMITED)
        writeVarint(value.size.toLong())
        out.write(value)
    }

    fun string(field: Int, value: String): ProtoWriter = bytes(field, value.toByteArray(Charsets.UTF_8))

    fun message(field: Int, block: ProtoWriter.() -> Unit): ProtoWriter =
        bytes(field, ProtoWriter().apply(block).toByteArray())

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun writeTag(field: Int, wireType: Int) = writeVarint(((field shl 3) or wireType).toLong())

    private fun writeVarint(value: Long) {
        var v = value
        while (true) {
            if (v and 0x7FL.inv() == 0L) {
                out.write(v.toInt())
                return
            }
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
    }

    companion object {
        const val WIRE_VARINT = 0
        const val WIRE_FIXED64 = 1
        const val WIRE_LENGTH_DELIMITED = 2
        const val WIRE_FIXED32 = 5
    }
}

/** Parsed protobuf message: every field number maps to the values seen, in order. */
class ProtoMessage internal constructor(private val fields: Map<Int, List<Any>>) {

    fun varint(field: Int, default: Long = 0L): Long =
        (fields[field]?.firstOrNull { it is Long } as Long?) ?: default

    fun bytes(field: Int): ByteArray? = fields[field]?.firstOrNull { it is ByteArray } as ByteArray?

    fun string(field: Int): String? = bytes(field)?.toString(Charsets.UTF_8)

    fun message(field: Int): ProtoMessage? = bytes(field)?.let { ProtoReader.parse(it) }

    fun messages(field: Int): List<ProtoMessage> =
        fields[field].orEmpty().filterIsInstance<ByteArray>().map { ProtoReader.parse(it) }

    fun has(field: Int): Boolean = fields.containsKey(field)
}

object ProtoReader {

    /** Parses [length] bytes of [data] starting at [offset]. Throws [IllegalArgumentException] if malformed. */
    fun parse(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): ProtoMessage {
        val fields = LinkedHashMap<Int, MutableList<Any>>()
        var pos = offset
        val end = offset + length
        require(offset >= 0 && end <= data.size) { "Bad protobuf bounds" }

        fun readVarint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                require(pos < end) { "Truncated varint" }
                val b = data[pos++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
                require(shift < 70) { "Varint too long" }
            }
        }

        while (pos < end) {
            val tag = readVarint()
            val field = (tag ushr 3).toInt()
            val value: Any = when ((tag and 7).toInt()) {
                ProtoWriter.WIRE_VARINT -> readVarint()
                ProtoWriter.WIRE_FIXED64 -> {
                    require(pos + 8 <= end) { "Truncated fixed64" }
                    var v = 0L
                    for (i in 0 until 8) v = v or ((data[pos + i].toLong() and 0xFF) shl (8 * i))
                    pos += 8
                    v
                }
                ProtoWriter.WIRE_LENGTH_DELIMITED -> {
                    val len = readVarint().toInt()
                    require(len >= 0 && pos + len <= end) { "Truncated bytes field" }
                    data.copyOfRange(pos, pos + len).also { pos += len }
                }
                ProtoWriter.WIRE_FIXED32 -> {
                    require(pos + 4 <= end) { "Truncated fixed32" }
                    var v = 0L
                    for (i in 0 until 4) v = v or ((data[pos + i].toLong() and 0xFF) shl (8 * i))
                    pos += 4
                    v
                }
                else -> throw IllegalArgumentException("Unsupported wire type in tag $tag")
            }
            fields.getOrPut(field) { mutableListOf() }.add(value)
        }
        return ProtoMessage(fields)
    }
}
