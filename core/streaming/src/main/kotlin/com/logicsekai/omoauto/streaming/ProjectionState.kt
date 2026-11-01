// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.streaming

import com.logicsekai.omoauto.data.phone.CastingApps
import com.logicsekai.omoauto.streaming.link.WifiDirectLink

/** Everything the UI needs to render the projection status. */
data class ProjectionState(
    val link: WifiDirectLink.State = WifiDirectLink.State.Idle,
    val peers: List<WifiDirectLink.Peer> = emptyList(),
    val androidAuto: AaStatus = AaStatus(),
    val display: DisplayStatus = DisplayStatus(),
    val stats: StreamStats = StreamStats(),
) {
    val isLinked: Boolean get() = link is WifiDirectLink.State.Connected
    val linkedPeer: WifiDirectLink.Peer? get() = (link as? WifiDirectLink.State.Connected)?.peer
    val isAndroidAutoOn: Boolean get() = androidAuto.phase != AaPhase.OFF && androidAuto.phase != AaPhase.FAILED
    val isStreamingToBike: Boolean get() = display.phase == DisplayPhase.STREAMING
}

enum class AaPhase { OFF, STARTING, CONNECTING, HANDSHAKING, CONNECTED, STREAMING, RECOVERING, FAILED }

enum class AaFailure {
    NOT_INSTALLED,
    NO_KEYS,
    KEYS_REJECTED,
    SERVER_NOT_RUNNING,
    /** Android Auto took the connection but never answered (frozen in the background / server stuck). */
    NOT_ANSWERING,
    ENCODER_UNAVAILABLE,
    PHONE_EXIT,
    PROTOCOL,
}

data class AaStatus(
    val phase: AaPhase = AaPhase.OFF,
    val failure: AaFailure? = null,
    val detail: String = "",
)

enum class DisplayPhase {
    IDLE, CONNECTING, WAITING, STREAMING, HIDDEN, RETRYING,

    /** Another phone app holds the display (it answers 7/3 or keeps refusing for 10 s). */
    BUSY,
}

data class DisplayStatus(
    val phase: DisplayPhase = DisplayPhase.IDLE,
    val detail: String = "",
    /** When [phase] is BUSY: known casting apps installed on this phone (likely culprits). */
    val blockingApps: List<CastingApps.App> = emptyList(),
)

data class StreamStats(
    val fps: Double = 0.0,
    val mbps: Double = 0.0,
    val framesSent: Long = 0,
    val renderedFrames: Long = 0,
    val decodedFrames: Long = 0,
    val encoder: String = "",
    val bitrate: Int = 0,
    val outputSize: String = "",
    val writeMeanMs: Double = 0.0,
    val writeMaxMs: Double = 0.0,
    val droppedFrames: Long = 0,
    val keyFrameRequests: Long = 0,
    val fpsHistory: List<Float> = emptyList(),
    val mbpsHistory: List<Float> = emptyList(),
    val sessionSeconds: Long = 0,
)

/** Result of asking the engine to start Android Auto. */
enum class StartResult { STARTED, ALREADY_RUNNING, NOT_INSTALLED, NO_KEYS, ENCODER_UNAVAILABLE }
