// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.data.settings

/** Everything the user can configure. Defaults match the proven WayXAuto 0.2.7 behaviour. */
data class OmoSettings(
    val onboardingDone: Boolean = false,
    val disclaimerAccepted: Boolean = false,
    /** Video bitrate sent to the motorcycle. */
    val bitrateBps: Int = DEFAULT_BITRATE,
    /** Width of the picture sent to the display (height = width × 3/8). */
    val outputWidth: Int = 960,
    /** Resolution Android Auto renders at. */
    val sourceWidth: Int = 1280,
    /** Space kept free on the right of the display, in 960-px units. */
    val rightMarginPx: Int = 120,
    /** Space kept free at the top and bottom, in 960-px units. */
    val verticalMarginPx: Int = 45,
    /** Use [customMargins] (all four edges typed in by the user) instead of the presets above. */
    val customFit: Boolean = false,
    val customMargins: FitMargins = FitMargins.DEFAULT,
    /** Drop stale video instead of buffering (lower latency). */
    val boundedLiveQueue: Boolean = true,
    /** Theme answered to the motorcycle display. */
    val displayNightTheme: Boolean = true,
    /** Night-mode sensor value reported to Android Auto. */
    val androidAutoNightMode: Boolean = false,
    val appTheme: AppTheme = AppTheme.SYSTEM,
    val savedBikes: List<SavedBike> = emptyList(),
    val lastBikeAddress: String? = null,
    /** Reconnect to the last motorcycle automatically when the link drops. */
    val autoReconnect: Boolean = true,
    /** User confirmed the vendor auto-start / battery step (see PhoneVendor). */
    val backgroundConfirmed: Boolean = false,
    /** User confirmed Android Auto developer settings are unlocked (cannot be detected). */
    val developerModeConfirmed: Boolean = false,
    val skippedVersion: String? = null,
) {
    /** The edges actually used for the picture on the display. */
    val fitMargins: FitMargins
        get() = if (customFit) customMargins.clamped() else FitMargins(0, verticalMarginPx, rightMarginPx, verticalMarginPx)

    companion object {
        /** WayXAuto's proven value; frequent key frames need the extra headroom. */
        const val DEFAULT_BITRATE = 6_000_000
        val BITRATE_OPTIONS = listOf(2_000_000, 4_000_000, 6_000_000, 8_000_000, 10_000_000)
        val OUTPUT_WIDTH_OPTIONS = listOf(960, 1280)
        val SOURCE_WIDTH_OPTIONS = listOf(1280, 1920)
        val RIGHT_MARGIN_OPTIONS = listOf(96, 120, 144)
        val VERTICAL_MARGIN_OPTIONS = listOf(36, 45, 54)
    }
}

/**
 * Free space around Android Auto's picture on the motorcycle display, per edge, in 960-px units
 * (the display is 960 × 360 in these units, whatever the output resolution).
 */
data class FitMargins(val left: Int, val top: Int, val right: Int, val bottom: Int) {

    /** Keeps at least half the width and a third of the height for the picture. */
    fun clamped(): FitMargins {
        val l = left.coerceIn(0, MAX_HORIZONTAL)
        val r = right.coerceIn(0, MAX_HORIZONTAL - l)
        val t = top.coerceIn(0, MAX_VERTICAL)
        val b = bottom.coerceIn(0, MAX_VERTICAL - t)
        return FitMargins(l, t, r, b)
    }

    val isValid: Boolean get() = this == clamped()

    companion object {
        const val UNIT_WIDTH = 960
        const val UNIT_HEIGHT = 360
        /** left + right may use at most half the width. */
        const val MAX_HORIZONTAL = 480
        /** top + bottom may use at most two thirds of the height. */
        const val MAX_VERTICAL = 240
        val DEFAULT = FitMargins(left = 0, top = 45, right = 120, bottom = 45)
    }
}

enum class AppTheme { SYSTEM, LIGHT, DARK }

data class SavedBike(val address: String, val name: String)
