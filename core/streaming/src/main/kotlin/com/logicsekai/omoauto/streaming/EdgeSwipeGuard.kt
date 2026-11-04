// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.streaming

/**
 * Keeps drags from waking Android Auto's Back / Exit toolbar.
 *
 * Phone apps on the car screen (OmoWeb, other browsers) run "immersive". Android Auto 17.x then
 * watches the head unit's touches with an edge-swipe detector (CAR.EdgeGestDetect): a touch that
 * goes down within 100 dp of any edge and moves 30 dp away from that edge within 500 ms counts as
 * a system swipe and shows the toolbar. On a 360 px tall display the top and bottom zones cover
 * more than half the height, so nearly every vertical scroll woke the toolbar.
 *
 * Only where a drag *starts* matters, so a drag that starts in an edge zone is started at the
 * nearest point outside the zones and every later point is shifted by the same amount: the page
 * scrolls exactly as far, and the detector never arms. Taps and long presses keep their real
 * position.
 *
 * [width] x [height] is the touch area (pixels of the UI area), [density] Android Auto's dpi.
 */
class EdgeSwipeGuard(private val width: Int, private val height: Int, density: Int) {

    /** Edge zone size in touch pixels (Android Auto: 100 dp), plus a small safety margin. */
    val zone: Int = 100 * density / 160 + 4

    fun inEdgeZone(x: Int, y: Int): Boolean =
        x < zone || y < zone || x >= width - zone || y >= height - zone

    /** Nearest point outside every edge zone (unchanged on an axis too small to have one). */
    fun safeStart(x: Int, y: Int): Pair<Int, Int> {
        val sx = if (width > 2 * zone) x.coerceIn(zone, width - zone - 1) else x
        val sy = if (height > 2 * zone) y.coerceIn(zone, height - zone - 1) else y
        return sx to sy
    }

    companion object {
        /** Movement (touch px) that turns a pending touch into a drag. */
        const val SLOP = 8
        /** A still touch this long is sent where it is (long press). */
        const val HOLD_MS = 300L
    }
}
