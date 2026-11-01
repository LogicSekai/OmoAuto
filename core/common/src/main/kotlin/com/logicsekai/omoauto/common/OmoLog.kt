// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.common

/**
 * Tiny logging facade so pure-Kotlin modules can log without depending on android.util.Log.
 * The app installs a Logcat sink at startup; unit tests keep the default stdout sink.
 */
object OmoLog {

    enum class Level { DEBUG, INFO, WARN, ERROR }

    fun interface Sink {
        fun log(level: Level, tag: String, message: String, error: Throwable?)
    }

    @Volatile
    var sink: Sink = Sink { level, tag, message, error ->
        println("${level.name.first()}/$tag: $message")
        error?.printStackTrace()
    }

    /** Recent log lines, shown in Diagnostics and attached to shared reports. */
    val history = EventHistory(capacity = 400)

    fun d(tag: String, message: String) = write(Level.DEBUG, tag, message, null)
    fun i(tag: String, message: String) = write(Level.INFO, tag, message, null)
    fun w(tag: String, message: String, error: Throwable? = null) = write(Level.WARN, tag, message, error)
    fun e(tag: String, message: String, error: Throwable? = null) = write(Level.ERROR, tag, message, error)

    private fun write(level: Level, tag: String, message: String, error: Throwable?) {
        if (level != Level.DEBUG) {
            val suffix = error?.let { " (${it.javaClass.simpleName}: ${it.message})" }.orEmpty()
            history.add("${level.name.first()}/$tag: $message$suffix")
        }
        sink.log(level, tag, message, error)
    }
}
