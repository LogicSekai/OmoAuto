// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.data.settings

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FitMarginsTest {
    @Test
    fun `clamping keeps enough picture`() {
        assertTrue(FitMargins.DEFAULT.isValid)
        assertEquals(FitMargins(400, 200, 80, 40), FitMargins(400, 200, 300, 100).clamped())
        assertEquals(FitMargins(0, 0, 0, 0), FitMargins(-5, -1, -9, -2).clamped())
        assertFalse(FitMargins(0, 0, 500, 0).isValid)
    }

    @Test
    fun `presets map to edges unless custom is on`() {
        val preset = OmoSettings(rightMarginPx = 144, verticalMarginPx = 36)
        assertEquals(FitMargins(0, 36, 144, 36), preset.fitMargins)
        val custom = preset.copy(customFit = true, customMargins = FitMargins(10, 20, 30, 40))
        assertEquals(FitMargins(10, 20, 30, 40), custom.fitMargins)
    }
}
