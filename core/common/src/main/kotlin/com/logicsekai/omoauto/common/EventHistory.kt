// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.common

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/** Thread-safe ring buffer of timestamped text lines. */
class EventHistory(private val capacity: Int) {

    private val lines = ArrayDeque<String>(capacity)
    private val format = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)

    fun add(line: String, atMillis: Long = System.currentTimeMillis()) {
        val stamped = synchronized(format) { format.format(Date(atMillis)) } + "  " + line
        synchronized(lines) {
            if (lines.size == capacity) lines.removeFirst()
            lines.addLast(stamped)
        }
    }

    fun snapshot(): List<String> = synchronized(lines) { lines.toList() }

    fun clear() = synchronized(lines) { lines.clear() }
}
