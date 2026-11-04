// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.streaming

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class EdgeSwipeGuardTest {
    // UI area of the default layout (1280 frame, margins on), density 160.
    private val guard = EdgeSwipeGuard(width = 1120, height = 360, density = 160)

    @Test
    fun `edge zones are 100 dp plus margin`() {
        assertEquals(104, guard.zone)
        assertTrue(guard.inEdgeZone(560, 50))   // top
        assertTrue(guard.inEdgeZone(560, 300))  // bottom
        assertTrue(guard.inEdgeZone(20, 180))   // left
        assertTrue(guard.inEdgeZone(1100, 180)) // right
        assertFalse(guard.inEdgeZone(560, 180)) // middle
    }

    @Test
    fun `drag start moves to the nearest safe point`() {
        assertEquals(560 to 104, guard.safeStart(560, 10))
        assertEquals(560 to 255, guard.safeStart(560, 350))
        assertEquals(104 to 104, guard.safeStart(0, 0))
        assertEquals(1015 to 180, guard.safeStart(1119, 180))
    }

    @Test
    fun `axis too small for two zones is left alone`() {
        val small = EdgeSwipeGuard(width = 1120, height = 200, density = 160)
        assertEquals(560 to 10, small.safeStart(560, 10))
    }
}
