// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.streaming.video

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.view.Surface
import com.logicsekai.omoauto.common.OmoLog

/**
 * H.264 encoder fed through an input [Surface]. Tries the default encoder first, then other
 * hardware encoders, each with progressively simpler settings, because vendor encoders differ
 * in which low-latency keys they accept.
 */
class AvcEncoder(
    private val width: Int,
    private val height: Int,
    private val requestedBitrate: Int,
    private val fps: Int = 60,
    /**
     * Ask for a new key frame once this many frames passed since the last one (0 = only the
     * encoder's 1 s interval). The OMOWAY display recovers from dropped frames only at key
     * frames; WayXAuto used 6 (about 10 key frames per second at 60 fps).
     */
    private val keyFrameEveryFrames: Int = 0,
    private val onFrame: (EncodedFrame) -> Unit,
) {
    class EncodedFrame(val annexB: ByteArray, val keyFrame: Boolean, val ptsUs: Long, val sps: ByteArray?, val pps: ByteArray?)

    private var codec: MediaCodec? = null
    private var outputThread: Thread? = null
    @Volatile private var running = false
    @Volatile private var sps: ByteArray? = null
    @Volatile private var pps: ByteArray? = null

    lateinit var inputSurface: Surface
        private set
    var codecDescription: String = ""
        private set
    var bitrate: Int = requestedBitrate
        private set
    @Volatile var encodedFrames = 0L
        private set
    @Volatile var keyFrames = 0L
        private set
    private var framesSinceKey = 0
    private var keyRequestPending = false

    fun start() {
        var lastError: Exception? = null
        for (name in candidateEncoders()) {
            for (variant in Variant.entries) {
                val candidate = try {
                    MediaCodec.createByCodecName(name)
                } catch (e: Exception) {
                    lastError = e
                    break
                }
                try {
                    val clamped = candidate.codecInfo.getCapabilitiesForType(MIME).videoCapabilities
                        .bitrateRange.clamp(requestedBitrate)
                    val format = MediaFormat.createVideoFormat(MIME, width, height).apply {
                        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                        setInteger(MediaFormat.KEY_BIT_RATE, clamped)
                        setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                        if (variant.baseline) {
                            setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
                            setInteger("max-bframes", 0)
                        }
                        if (variant.lowLatency) {
                            if (Build.VERSION.SDK_INT >= 30) setInteger(MediaFormat.KEY_LATENCY, 0)
                            setInteger(MediaFormat.KEY_PRIORITY, 0)
                        }
                    }
                    candidate.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    inputSurface = candidate.createInputSurface()
                    candidate.start()
                    codec = candidate
                    bitrate = clamped
                    codecDescription = "$name · ${variant.label}"
                    OmoLog.i(TAG, "Encoder $codecDescription ${width}x$height @ ${clamped / 1_000_000.0} Mbps")
                    running = true
                    outputThread = Thread(::drain, "omo-encoder-out").apply { start() }
                    return
                } catch (e: Exception) {
                    lastError = e
                    runCatching { candidate.release() }
                }
            }
        }
        throw IllegalStateException("No usable H.264 encoder: ${lastError?.message}", lastError)
    }

    fun requestKeyFrame() {
        val c = codec ?: return
        try {
            c.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) })
        } catch (e: Exception) {
            OmoLog.w(TAG, "Key frame request failed", e)
        }
    }

    fun release() {
        running = false
        outputThread?.join(500)
        codec?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        codec = null
        if (::inputSurface.isInitialized) inputSurface.release()
    }

    private fun drain() {
        val c = codec ?: return
        val info = MediaCodec.BufferInfo()
        while (running) {
            val index = try {
                c.dequeueOutputBuffer(info, 10_000)
            } catch (e: IllegalStateException) {
                if (running) OmoLog.w(TAG, "Encoder output failed", e)
                break
            }
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> readParameterSets(c.outputFormat)
                index >= 0 -> try {
                    val buffer = c.getOutputBuffer(index)
                    if (buffer != null && info.size > 0) {
                        val bytes = ByteArray(info.size)
                        buffer.position(info.offset)
                        buffer.get(bytes, 0, info.size)
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            H264.findNal(bytes, H264.NAL_SPS)?.let { sps = it }
                            H264.findNal(bytes, H264.NAL_PPS)?.let { pps = it }
                        } else {
                            val nals = H264.splitNalUnits(bytes)
                            nals.firstOrNull { H264.nalType(it) == H264.NAL_SPS }?.let { sps = it }
                            nals.firstOrNull { H264.nalType(it) == H264.NAL_PPS }?.let { pps = it }
                            val key = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0 ||
                                nals.any { H264.nalType(it) == H264.NAL_IDR }
                            encodedFrames++
                            if (key) {
                                keyFrames++
                                framesSinceKey = 0
                                keyRequestPending = false
                            } else if (keyFrameEveryFrames > 0 && ++framesSinceKey >= keyFrameEveryFrames && !keyRequestPending) {
                                keyRequestPending = true
                                requestKeyFrame()
                            }
                            try {
                                onFrame(EncodedFrame(H264.toAnnexB(nals), key, info.presentationTimeUs, sps, pps))
                            } catch (e: Exception) {
                                OmoLog.w(TAG, "Frame consumer failed", e)
                            }
                        }
                    }
                    c.releaseOutputBuffer(index, false)
                } catch (e: IllegalStateException) {
                    // The codec was stopped while this buffer was in flight (session ending).
                    if (running) OmoLog.w(TAG, "Encoder buffer handling failed", e)
                    break
                }
            }
        }
    }

    private fun readParameterSets(format: MediaFormat) {
        fun bytesOf(key: String): ByteArray? = format.getByteBuffer(key)?.let { b ->
            val copy = b.duplicate()
            ByteArray(copy.remaining()).also { copy.get(it) }
        }
        val csd0 = bytesOf("csd-0")
        val csd1 = bytesOf("csd-1")
        csd0?.let { H264.findNal(it, H264.NAL_SPS) }?.let { sps = it }
        (csd1?.let { H264.findNal(it, H264.NAL_PPS) } ?: csd0?.let { H264.findNal(it, H264.NAL_PPS) })?.let { pps = it }
    }

    private fun candidateEncoders(): List<String> {
        val names = LinkedHashSet<String>()
        runCatching {
            MediaCodec.createEncoderByType(MIME).also { names += it.name }.release()
        }
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { info -> info.isEncoder && info.supportedTypes.any { it.equals(MIME, ignoreCase = true) } }
            .filterNot { it.name.lowercase().endsWith(".secure") }
            .sortedBy { if (isSoftware(it)) 1 else 0 }
            .forEach { names += it.name }
        return names.toList()
    }

    private fun isSoftware(info: MediaCodecInfo): Boolean {
        if (Build.VERSION.SDK_INT >= 29) return info.isSoftwareOnly
        val name = info.name.lowercase()
        return name.startsWith("omx.google.") || name.startsWith("c2.android.")
    }

    private enum class Variant(val baseline: Boolean, val lowLatency: Boolean, val label: String) {
        LOW_LATENCY(true, true, "baseline low-latency"),
        BASELINE(true, false, "baseline"),
        PLAIN(false, false, "default profile"),
    }

    private companion object {
        const val TAG = "OmoEncoder"
        const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC
    }
}
