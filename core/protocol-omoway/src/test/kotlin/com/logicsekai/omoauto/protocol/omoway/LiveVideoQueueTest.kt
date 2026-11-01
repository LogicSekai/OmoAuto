// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.protocol.omoway

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveVideoQueueTest {

    private val ms = 1_000_000L

    @Test
    fun `delta frames are dropped until the first key frame`() {
        val queue = LiveVideoQueue()
        queue.offer(byteArrayOf(1), keyFrame = false, ptsUs = 0, nowNanos = 0)
        assertEquals(0, queue.size())
        queue.offer(byteArrayOf(2), keyFrame = true, ptsUs = 1, nowNanos = 1)
        queue.offer(byteArrayOf(3), keyFrame = false, ptsUs = 2, nowNanos = 2)
        assertEquals(2, queue.size())
        assertTrue(queue.poll(nowNanos = 3)!!.keyFrame)
    }

    @Test
    fun `stale queue is flushed and waits for a key frame again`() {
        val queue = LiveVideoQueue(maxAgeNanos = 100 * ms)
        queue.offer(byteArrayOf(1), keyFrame = true, ptsUs = 0, nowNanos = 0)
        queue.consumeKeyFrameRequest()
        queue.offer(byteArrayOf(2), keyFrame = false, ptsUs = 1, nowNanos = 150 * ms)
        assertEquals(0, queue.size())
        assertTrue(queue.consumeKeyFrameRequest())
        assertEquals(1L, queue.recoveries)
        assertNull(queue.poll(nowNanos = 151 * ms))
    }

    @Test
    fun `too many queued frames trigger recovery`() {
        val queue = LiveVideoQueue(maxFrames = 3)
        queue.offer(byteArrayOf(0), keyFrame = true, ptsUs = 0, nowNanos = 0)
        repeat(2) { queue.offer(byteArrayOf(1), keyFrame = false, ptsUs = 1, nowNanos = 1) }
        queue.offer(byteArrayOf(2), keyFrame = false, ptsUs = 2, nowNanos = 2)
        assertEquals(0, queue.size())
        assertEquals(4L, queue.droppedFrames)
    }
}
