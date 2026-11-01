// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.logicsekai.omoauto.streaming.AaFailure
import com.logicsekai.omoauto.streaming.AaPhase
import com.logicsekai.omoauto.streaming.DisplayPhase
import com.logicsekai.omoauto.streaming.ProjectionState
import com.logicsekai.omoauto.streaming.link.WifiDirectLink

/** One-line link status under the main action. */
@Composable
internal fun linkLine(state: ProjectionState): String = when (val link = state.link) {
    is WifiDirectLink.State.Connected -> when (state.display.phase) {
        DisplayPhase.STREAMING -> stringResource(R.string.home_status_on_bike, link.peer.displayName)
        DisplayPhase.HIDDEN -> stringResource(R.string.home_status_display_busy)
        DisplayPhase.CONNECTING, DisplayPhase.WAITING -> stringResource(R.string.home_status_display_waiting)
        DisplayPhase.RETRYING -> stringResource(R.string.home_status_display_retrying)
        DisplayPhase.BUSY -> stringResource(R.string.home_status_display_taken)
        DisplayPhase.IDLE -> stringResource(R.string.home_status_linked, link.peer.displayName)
    }
    is WifiDirectLink.State.Connecting -> stringResource(R.string.home_status_connecting, link.peer.displayName)
    is WifiDirectLink.State.Searching -> stringResource(R.string.home_status_searching)
    is WifiDirectLink.State.Failed -> linkFailure(link.reason)
    WifiDirectLink.State.Unavailable -> stringResource(R.string.home_link_unavailable)
    WifiDirectLink.State.Idle -> stringResource(R.string.home_status_not_linked)
}

@Composable
internal fun linkFailure(reason: WifiDirectLink.Reason): String = stringResource(
    when (reason) {
        WifiDirectLink.Reason.WIFI_OFF -> R.string.home_link_wifi_off
        WifiDirectLink.Reason.PERMISSION -> R.string.home_link_permission
        WifiDirectLink.Reason.CONNECT_FAILED -> R.string.home_link_failed
        WifiDirectLink.Reason.TIMEOUT -> R.string.home_link_timeout
        WifiDirectLink.Reason.OTHER_GROUP_ACTIVE -> R.string.home_link_other_group
        WifiDirectLink.Reason.LOST -> R.string.home_link_lost
    },
)

@Composable
internal fun aaPhaseLine(phase: AaPhase): String = stringResource(
    when (phase) {
        AaPhase.OFF -> R.string.home_aa_off
        AaPhase.STARTING -> R.string.home_aa_starting
        AaPhase.CONNECTING, AaPhase.HANDSHAKING -> R.string.home_aa_connecting
        AaPhase.CONNECTED -> R.string.home_aa_waiting_video
        AaPhase.STREAMING -> R.string.home_aa_running
        AaPhase.RECOVERING -> R.string.home_aa_recovering
        AaPhase.FAILED -> R.string.home_aa_failed
    },
)

@Composable
internal fun aaFailureText(failure: AaFailure): String = stringResource(
    when (failure) {
        AaFailure.NOT_INSTALLED -> R.string.home_fail_not_installed
        AaFailure.NO_KEYS -> R.string.home_fail_no_keys
        AaFailure.KEYS_REJECTED -> R.string.home_fail_keys_rejected
        AaFailure.SERVER_NOT_RUNNING -> R.string.home_fail_server
        AaFailure.NOT_ANSWERING -> R.string.home_fail_not_answering
        AaFailure.ENCODER_UNAVAILABLE -> R.string.home_fail_encoder
        AaFailure.PHONE_EXIT -> R.string.home_fail_phone_exit
        AaFailure.PROTOCOL -> R.string.home_fail_protocol
    },
)
