// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.streaming

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent

/**
 * Standard media buttons (like a Bluetooth headset or a steering-wheel button). Android delivers
 * them to the app whose media session is playing, so they work with Spotify, YouTube Music,
 * podcast apps, local players and OmoWeb, with no app-specific code.
 */
class MediaKeys(context: Context) {

    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)

    fun press(keyCode: Int) {
        val now = SystemClock.uptimeMillis()
        audio?.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        audio?.dispatchMediaKeyEvent(KeyEvent(now, now + 50, KeyEvent.ACTION_UP, keyCode, 0))
    }

    companion object {
        const val PLAY_PAUSE = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        const val NEXT = KeyEvent.KEYCODE_MEDIA_NEXT
        const val PREVIOUS = KeyEvent.KEYCODE_MEDIA_PREVIOUS
        const val REWIND = KeyEvent.KEYCODE_MEDIA_REWIND
        const val FAST_FORWARD = KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
    }
}
