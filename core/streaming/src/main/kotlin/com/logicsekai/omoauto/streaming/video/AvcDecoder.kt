// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.streaming.video

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Process
import android.view.Surface
import com.logicsekai.omoauto.common.OmoLog

/** Decodes Android Auto's H.264 stream straight into [outputSurface] (the compositor input). */
class AvcDecoder(
    private val width: Int,
    private val height: Int,
    private val outputSurface: Surface,
) {
    private var codec: MediaCodec? = null
    private var outputThread: Thread? = null
    @Volatile private var running = false

    @Volatile var decodedFrames = 0L
        private set
    @Volatile var droppedInputs = 0L
        private set
    @Volatile var decodedWidth = 0
        private set
    @Volatile var decodedHeight = 0
        private set

    val isStarted: Boolean get() = codec != null

    /** Creates the decoder on the first codec configuration (SPS/PPS) from Android Auto. */
    @Synchronized
    fun configure(csd: ByteArray, offset: Int, length: Int) {
        if (codec == null) {
            check(outputSurface.isValid) { "Decoder output surface is not valid" }
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 2 * 1024 * 1024)
            }
            val c = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            c.configure(format, outputSurface, null, 0)
            c.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT)
            c.start()
            codec = c
            running = true
            outputThread = Thread(::drain, "omo-decoder-out").apply { start() }
            OmoLog.i(TAG, "Decoder ${c.name} started for ${width}x$height")
        }
        queue(csd, offset, length, 0, MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
    }

    fun decode(data: ByteArray, offset: Int, length: Int, ptsUs: Long) {
        if (codec == null) return
        queue(data, offset, length, ptsUs, 0)
    }

    @Synchronized
    private fun queue(data: ByteArray, offset: Int, length: Int, ptsUs: Long, flags: Int) {
        val c = codec ?: return
        try {
            val index = c.dequeueInputBuffer(10_000)
            if (index < 0) {
                droppedInputs++
                return
            }
            val buffer = c.getInputBuffer(index) ?: return
            buffer.clear()
            if (length > buffer.capacity()) {
                droppedInputs++
                c.queueInputBuffer(index, 0, 0, ptsUs, 0)
                return
            }
            buffer.put(data, offset, length)
            c.queueInputBuffer(index, 0, length, ptsUs, flags)
        } catch (e: IllegalStateException) {
            OmoLog.w(TAG, "Decoder input failed", e)
        }
    }

    @Synchronized
    fun release() {
        running = false
        outputThread?.join(500)
        codec?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        codec = null
    }

    private fun drain() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY)
        val info = MediaCodec.BufferInfo()
        while (running) {
            val c = codec ?: break
            val index = try {
                c.dequeueOutputBuffer(info, 20_000)
            } catch (e: IllegalStateException) {
                if (running) OmoLog.w(TAG, "Decoder output failed", e)
                break
            }
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val f = c.outputFormat
                    var w = f.getInteger(MediaFormat.KEY_WIDTH)
                    var h = f.getInteger(MediaFormat.KEY_HEIGHT)
                    if (f.containsKey("crop-left") && f.containsKey("crop-right")) {
                        w = f.getInteger("crop-right") - f.getInteger("crop-left") + 1
                    }
                    if (f.containsKey("crop-top") && f.containsKey("crop-bottom")) {
                        h = f.getInteger("crop-bottom") - f.getInteger("crop-top") + 1
                    }
                    decodedWidth = w
                    decodedHeight = h
                }
                index >= 0 -> {
                    val render = info.size > 0
                    try {
                        c.releaseOutputBuffer(index, render)
                    } catch (e: IllegalStateException) {
                        if (running) OmoLog.w(TAG, "Release to surface failed", e)
                    }
                    if (render) decodedFrames++
                }
            }
        }
    }

    private companion object {
        const val TAG = "OmoDecoder"
    }
}
