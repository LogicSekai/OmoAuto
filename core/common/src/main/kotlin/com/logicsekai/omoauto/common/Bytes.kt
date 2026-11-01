// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.common

import java.security.MessageDigest

/** Lower-case hex of [bytes]. */
fun ByteArray.toHex(): String = joinToString(separator = "") { "%02x".format(it.toInt() and 0xFF) }

fun ByteArray.sha256Hex(): String = MessageDigest.getInstance("SHA-256").digest(this).toHex()

/** Reads an unsigned big-endian 16-bit value at [offset]. */
fun ByteArray.u16be(offset: Int): Int = ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)
