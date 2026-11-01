// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.streaming.video

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

class VideoGeometryTest {

    @Test
    fun `default geometry matches the proven WayXAuto values`() {
        val g = VideoGeometry.compute(outputWidth = 960, sourceWidth = 1280, rightMargin = 120, verticalMargin = 45)
        assertEquals(960 to 360, g.outputWidth to g.outputHeight)
        assertEquals(1280 to 720, g.aa.sourceWidth to g.aa.sourceHeight)
        // Android Auto margins announced in service discovery
        assertEquals(160, g.aa.marginWidth)
        assertEquals(360, g.aa.marginHeight)
        assertEquals(160, g.aa.density)
        // UI area placed on the display
        assertEquals(listOf(0, 45, 840, 270), listOf(g.destLeft, g.destTop, g.destWidth, g.destHeight))
        // and cropped from Android Auto's frame
        assertEquals(listOf(80, 180, 1120, 360), listOf(g.cropLeft, g.cropTop, g.cropWidth, g.cropHeight))
    }

    @Test
    fun `aspect of crop equals aspect of destination`() {
        for (out in listOf(960, 1280)) for (src in listOf(1280, 1920)) for (r in listOf(96, 120, 144)) for (v in listOf(36, 45, 54)) {
            val g = VideoGeometry.compute(out, src, r, v)
            val dest = g.destWidth.toDouble() / g.destHeight
            val crop = g.cropWidth.toDouble() / g.cropHeight
            assertEquals(dest, crop, 0.02, "out=$out src=$src r=$r v=$v")
            assertEquals(g.aa.sourceWidth, g.cropWidth + g.aa.marginWidth)
        }
    }

    @Test
    fun `custom edges place the picture and keep its aspect`() {
        val g = VideoGeometry.compute(960, 1280, left = 30, top = 20, right = 90, bottom = 60)
        assertEquals(listOf(30, 20, 840, 280), listOf(g.destLeft, g.destTop, g.destWidth, g.destHeight))
        assertEquals(g.destWidth.toDouble() / g.destHeight, g.cropWidth.toDouble() / g.cropHeight, 0.02)
        // The preset overload is the same as custom edges with nothing on the left.
        assertEquals(VideoGeometry.compute(960, 1280, 120, 45), VideoGeometry.compute(960, 1280, 0, 45, 120, 45))
        // Scaled to a 1280-wide output.
        val big = VideoGeometry.compute(1280, 1920, left = 30, top = 20, right = 90, bottom = 60)
        assertEquals(listOf(40, 26), listOf(big.destLeft, big.destTop))
        assertFailsWith<IllegalArgumentException> { VideoGeometry.compute(960, 1280, 300, 0, 300, 0) }
    }

    @Test
    fun `full hd source uses higher density`() {
        assertEquals(240, VideoGeometry.compute(1280, 1920, 120, 45).aa.density)
    }

    @Test
    fun `unsupported sizes are rejected`() {
        assertFailsWith<IllegalArgumentException> { VideoGeometry.compute(800, 1280, 120, 45) }
    }

    @Test
    fun `preview touches map into the Android Auto frame`() {
        val geo = VideoGeometry.compute(960, 1280, 120, 45)
        // Preview exactly the output size: UI area starts at (0,45) and maps to the crop origin (80,180).
        assertEquals(80 to 180, geo.previewToSource(0f, 45f, 960, 360))
        // Centre of the UI area (420,180) → centre of the crop (80+560, 180+180)
        assertEquals(640 to 360, geo.previewToSource(420f, 180f, 960, 360))
        // Same point on a half-size preview
        assertEquals(640 to 360, geo.previewToSource(210f, 90f, 480, 180))
        // Right margin and top margin are outside the UI
        assertNull(geo.previewToSource(900f, 180f, 960, 360))
        assertNull(geo.previewToSource(100f, 10f, 960, 360))
        // ...unless clamped (end of a drag)
        assertEquals(1198 to 180, geo.previewToSource(900f, 0f, 960, 360, clamp = true))
        // Letterboxed preview (taller view): content is centred vertically
        assertEquals(640 to 360, geo.previewToSource(420f, 180f + 100f, 960, 560))
    }
}
