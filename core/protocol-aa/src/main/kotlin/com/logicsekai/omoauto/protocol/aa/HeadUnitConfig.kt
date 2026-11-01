// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.protocol.aa

/**
 * What our virtual head unit tells Android Auto about itself.
 *
 * The phone renders at [VideoConfig.sourceWidth] x [VideoConfig.sourceHeight] but lays out its UI
 * only inside the centre area left after the margins, so we can crop exactly that area and scale
 * it onto the motorcycle's wide display.
 */
data class HeadUnitConfig(
    val video: VideoConfig,
    val info: HeadUnitInfo = HeadUnitInfo(),
    /** Reported to Android Auto through the night-mode sensor. */
    val nightMode: Boolean = false,
    /** Announce a touch screen (driven from the phone preview) in addition to keys/rotary. */
    val touchscreen: Boolean = true,
    val host: String = "127.0.0.1",
    val port: Int = DEFAULT_PORT,
) {
    companion object {
        /** Android Auto's "head unit server" (developer settings) listens here. */
        const val DEFAULT_PORT = 5277
    }
}

data class VideoConfig(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val marginWidth: Int,
    val marginHeight: Int,
    val density: Int,
) {
    /** AA resolution enum: 2 = 1280x720, 3 = 1920x1080. */
    val resolutionCode: Int get() = if (sourceWidth == 1920) 3 else 2

    /** Size of the area Android Auto actually draws its UI into. */
    val contentWidth: Int get() = sourceWidth - marginWidth
    val contentHeight: Int get() = sourceHeight - marginHeight
}

data class HeadUnitInfo(
    val make: String = "LogicSekai",
    val model: String = "OmoAuto",
    val year: String = "2026",
    val vehicleId: String = "LOGICSEKAI-OMOAUTO",
    val headUnitMake: String = "LogicSekai",
    val headUnitModel: String = "OmoAuto",
    val softwareBuild: String = "1",
    val softwareVersion: String = "1.0",
)
