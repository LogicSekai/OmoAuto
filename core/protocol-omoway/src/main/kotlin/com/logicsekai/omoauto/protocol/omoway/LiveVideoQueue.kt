// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.protocol.omoway

import java.util.ArrayDeque

/**
 * Small "live" queue between the encoder and the video socket.
 *
 * Old frames are worthless on a moving bike, so when the link falls behind (oldest frame older
 * than [maxAgeNanos], more than [maxFrames] queued, or more than [maxBytes] buffered) the whole
 * queue is dropped and delta frames are discarded until the next key frame arrives.
 */
class LiveVideoQueue(
    private val maxAgeNanos: Long = 100_000_000L,
    private val maxFrames: Int = 6,
    private val maxBytes: Int = 256 * 1024,
) {
    class Item(val packet: ByteArray, val keyFrame: Boolean, val ptsUs: Long, val enqueuedAtNanos: Long)

    private val items = ArrayDeque<Item>()
    private var bytes = 0
    private var waitingForKeyFrame = true

    /** Set when the queue was flushed; the encoder should produce a key frame. */
    private var keyFrameNeeded = true

    var droppedFrames = 0L
        private set
    var recoveries = 0L
        private set
    var maxDequeuedAgeNanos = 0L
        private set

    @Synchronized
    fun offer(packet: ByteArray, keyFrame: Boolean, ptsUs: Long, nowNanos: Long = System.nanoTime()) {
        if (packet.size > maxBytes) {
            flush()
            droppedFrames++
            return
        }
        val head = items.peekFirst()
        if (head != null && (nowNanos - head.enqueuedAtNanos > maxAgeNanos || items.size >= maxFrames || bytes + packet.size > maxBytes)) {
            flush()
        }
        if (waitingForKeyFrame && !keyFrame) {
            droppedFrames++
            return
        }
        if (keyFrame) {
            waitingForKeyFrame = false
            keyFrameNeeded = false
        }
        items.addLast(Item(packet, keyFrame, ptsUs, nowNanos))
        bytes += packet.size
    }

    @Synchronized
    fun poll(nowNanos: Long = System.nanoTime()): Item? {
        val head = items.peekFirst()
        if (head != null && nowNanos - head.enqueuedAtNanos > maxAgeNanos) flush()
        val item = items.pollFirst() ?: return null
        bytes -= item.packet.size
        maxDequeuedAgeNanos = maxOf(maxDequeuedAgeNanos, nowNanos - item.enqueuedAtNanos)
        return item
    }

    /** Returns true once after a flush, so the caller can request a key frame from the encoder. */
    @Synchronized
    fun consumeKeyFrameRequest(): Boolean {
        val needed = keyFrameNeeded
        keyFrameNeeded = false
        return needed
    }

    @Synchronized
    fun flush() {
        droppedFrames += items.size
        items.clear()
        bytes = 0
        waitingForKeyFrame = true
        keyFrameNeeded = true
        recoveries++
    }

    @Synchronized
    fun size(): Int = items.size
}
