// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.streaming.video

import com.logicsekai.omoauto.protocol.aa.VideoConfig

/**
 * Maps Android Auto's picture onto the motorcycle display.
 *
 * The display is wide (width × 3/8). We ask Android Auto for margins so that the UI it draws
 * has exactly the aspect of the display area we use: the output minus the free edges (left, top,
 * right, bottom; given in 960-px units and scaled to the output).
 */
data class VideoGeometry(
    val outputWidth: Int,
    val outputHeight: Int,
    val aa: VideoConfig,
    /** Where AA's UI is drawn on the output, top-left origin. */
    val destLeft: Int,
    val destTop: Int,
    val destWidth: Int,
    val destHeight: Int,
    /** Area of AA's frame that contains the UI, top-left origin. */
    val cropLeft: Int,
    val cropTop: Int,
    val cropWidth: Int,
    val cropHeight: Int,
) {
    /**
     * Maps a point on a preview of the output (a view of [viewWidth] x [viewHeight] that shows the
     * whole output scaled to fit and centred, like the phone preview) to Android Auto's video
     * frame coordinates. Points outside the UI area return null, or are clamped to its edge when
     * [clamp] is true (to finish a drag that left the area).
     */
    fun previewToSource(x: Float, y: Float, viewWidth: Int, viewHeight: Int, clamp: Boolean = false): Pair<Int, Int>? {
        if (viewWidth <= 0 || viewHeight <= 0) return null
        val scale = minOf(viewWidth / outputWidth.toFloat(), viewHeight / outputHeight.toFloat())
        val offsetX = (viewWidth - outputWidth * scale) / 2f
        val offsetY = (viewHeight - outputHeight * scale) / 2f
        var ox = (x - offsetX) / scale - destLeft
        var oy = (y - offsetY) / scale - destTop
        val inside = ox >= 0 && oy >= 0 && ox < destWidth && oy < destHeight
        if (!inside) {
            if (!clamp) return null
            ox = ox.coerceIn(0f, destWidth - 1f)
            oy = oy.coerceIn(0f, destHeight - 1f)
        }
        val sx = cropLeft + ox * cropWidth / destWidth
        val sy = cropTop + oy * cropHeight / destHeight
        return sx.toInt() to sy.toInt()
    }

    companion object {
        /** Preset layout: nothing on the left, [rightMargin] on the right, [verticalMargin] above and below. */
        fun compute(outputWidth: Int, sourceWidth: Int, rightMargin: Int, verticalMargin: Int): VideoGeometry =
            compute(outputWidth, sourceWidth, left = 0, top = verticalMargin, right = rightMargin, bottom = verticalMargin)

        /** Free edges in 960-px units; the picture fills the rest of the display. */
        fun compute(outputWidth: Int, sourceWidth: Int, left: Int, top: Int, right: Int, bottom: Int): VideoGeometry {
            require(outputWidth == 960 || outputWidth == 1280) { "Unsupported output width $outputWidth" }
            require(sourceWidth == 1280 || sourceWidth == 1920) { "Unsupported Android Auto width $sourceWidth" }
            require(left >= 0 && top >= 0 && right >= 0 && bottom >= 0) { "Negative margin" }
            require(left + right <= 480 && top + bottom <= 240) { "Margins leave too little picture" }
            val outputHeight = outputWidth * 3 / 8
            val sourceHeight = sourceWidth * 9 / 16
            fun scaled(units: Int) = units * outputWidth / 960

            val destLeft = scaled(left)
            val destWidth = outputWidth - destLeft - scaled(right)
            val cropWidth = destWidth * sourceWidth / outputWidth
            val marginWidth = sourceWidth - cropWidth

            val destTop = scaled(top)
            val destHeight = outputHeight - destTop - scaled(bottom)
            val cropHeight = destHeight * sourceWidth / outputWidth
            val marginHeight = sourceHeight - cropHeight

            return VideoGeometry(
                outputWidth = outputWidth,
                outputHeight = outputHeight,
                aa = VideoConfig(
                    sourceWidth = sourceWidth,
                    sourceHeight = sourceHeight,
                    marginWidth = marginWidth,
                    marginHeight = marginHeight,
                    density = if (sourceWidth == 1920) 240 else 160,
                ),
                destLeft = destLeft,
                destTop = destTop,
                destWidth = destWidth,
                destHeight = destHeight,
                cropLeft = marginWidth / 2,
                cropTop = marginHeight / 2,
                cropWidth = cropWidth,
                cropHeight = cropHeight,
            )
        }
    }
}
