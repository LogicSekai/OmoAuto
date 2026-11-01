// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.streaming.video

import android.view.Surface
import com.logicsekai.omoauto.common.OmoLog

/**
 * Android Auto H.264 → decoder → GL crop/scale (60 fps) → H.264 encoder → [onEncoded].
 *
 * Create one per Android Auto session; call [release] when the session ends.
 */
class VideoPipeline(
    val geometry: VideoGeometry,
    bitrate: Int,
    private val onEncoded: (AvcEncoder.EncodedFrame) -> Unit,
) {
    data class Stats(
        val decodedFrames: Long,
        val renderedFrames: Long,
        val encodedFrames: Long,
        val keyFrames: Long,
        val droppedInputs: Long,
        val decodedWidth: Int,
        val decodedHeight: Int,
        val encoder: String,
        val bitrate: Int,
    )

    private val compositor = GlCompositor(geometry)
    private val encoder = AvcEncoder(
        width = geometry.outputWidth,
        height = geometry.outputHeight,
        requestedBitrate = bitrate,
        keyFrameEveryFrames = KEY_FRAME_EVERY_FRAMES,
    ) { frame -> onEncoded(frame) }
    private var decoder: AvcDecoder? = null
    @Volatile private var started = false

    fun start() {
        encoder.start()
        try {
            compositor.start(encoder.inputSurface)
        } catch (e: Exception) {
            encoder.release()
            throw e
        }
        decoder = AvcDecoder(geometry.aa.sourceWidth, geometry.aa.sourceHeight, compositor.inputSurface)
        started = true
    }

    fun onCodecConfig(data: ByteArray, offset: Int, length: Int) {
        if (!started) return
        try {
            decoder?.configure(data, offset, length)
        } catch (e: Exception) {
            OmoLog.e(TAG, "Decoder configuration failed", e)
        }
    }

    fun onFrame(data: ByteArray, offset: Int, length: Int, ptsUs: Long) {
        if (started) decoder?.decode(data, offset, length, ptsUs)
    }

    fun requestKeyFrame() = encoder.requestKeyFrame()

    fun setPreviewSurface(surface: Surface?, width: Int, height: Int) = compositor.setPreviewSurface(surface, width, height)

    fun stats(): Stats {
        val d = decoder
        return Stats(
            decodedFrames = d?.decodedFrames ?: 0,
            renderedFrames = compositor.renderedFrames,
            encodedFrames = encoder.encodedFrames,
            keyFrames = encoder.keyFrames,
            droppedInputs = d?.droppedInputs ?: 0,
            decodedWidth = d?.decodedWidth ?: 0,
            decodedHeight = d?.decodedHeight ?: 0,
            encoder = encoder.codecDescription,
            bitrate = encoder.bitrate,
        )
    }

    fun release() {
        started = false
        decoder?.release()
        decoder = null
        compositor.release()
        encoder.release()
    }

    private companion object {
        const val TAG = "OmoPipeline"
        /** Same cadence as WayXAuto, which the OMOWAY display is known to work with. */
        const val KEY_FRAME_EVERY_FRAMES = 6
    }
}
