// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.feature.setup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.logicsekai.omoauto.data.keys.HeadUnitKeyRepository
import com.logicsekai.omoauto.data.phone.AndroidAutoHelper
import com.logicsekai.omoauto.data.phone.PhoneSettings
import com.logicsekai.omoauto.data.phone.PhoneVendor
import com.logicsekai.omoauto.data.settings.SettingsRepository
import com.logicsekai.omoauto.protocol.aa.HeadUnitKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SetupStep { INSTALL, DEVELOPER_MODE, HEAD_UNIT_SERVER, KEYS, LOCATION, BACKGROUND }

data class PhoneChecks(
    val androidAutoVersion: String? = null,
    val server: AndroidAutoHelper.ServerState = AndroidAutoHelper.ServerState.UNKNOWN,
    /** Location service switch (Android 8–12 need it on to find the motorcycle). */
    val locationEnabled: Boolean = true,
)

data class SetupUiState(
    val checks: PhoneChecks = PhoneChecks(),
    val keys: HeadUnitKeyRepository.KeyState = HeadUnitKeyRepository.KeyState.Missing,
    val developerModeConfirmed: Boolean = false,
    val vendor: PhoneVendor = PhoneVendor.OTHER,
    val needsLocationStep: Boolean = false,
    val needsBackgroundStep: Boolean = false,
    val backgroundConfirmed: Boolean = false,
    val busy: Boolean = false,
    val message: SetupMessage? = null,
) {
    val steps: List<SetupStep> = buildList {
        add(SetupStep.INSTALL)
        add(SetupStep.DEVELOPER_MODE)
        add(SetupStep.HEAD_UNIT_SERVER)
        add(SetupStep.KEYS)
        if (needsLocationStep) add(SetupStep.LOCATION)
        if (needsBackgroundStep) add(SetupStep.BACKGROUND)
    }

    fun isDone(step: SetupStep): Boolean = when (step) {
        SetupStep.INSTALL -> checks.androidAutoVersion != null
        SetupStep.DEVELOPER_MODE -> developerModeConfirmed || checks.server == AndroidAutoHelper.ServerState.RUNNING
        SetupStep.HEAD_UNIT_SERVER -> checks.server == AndroidAutoHelper.ServerState.RUNNING
        SetupStep.KEYS -> keys is HeadUnitKeyRepository.KeyState.Ready
        SetupStep.LOCATION -> checks.locationEnabled
        SetupStep.BACKGROUND -> backgroundConfirmed
    }

    val current: SetupStep? get() = steps.firstOrNull { !isDone(it) }
    val progress: Float get() = steps.count { isDone(it) } / steps.size.toFloat()
}

sealed interface SetupMessage {
    data object KeysSaved : SetupMessage
    data class Network(val detail: String) : SetupMessage
    data object Fingerprint : SetupMessage
    data class KeyProblem(val problem: HeadUnitKeys.Problem) : SetupMessage
}

class SetupViewModel(
    context: Context,
    private val keys: HeadUnitKeyRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val appContext = context.applicationContext
    private val checks = MutableStateFlow(PhoneChecks())
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<SetupMessage?>(null)

    val uiState: StateFlow<SetupUiState> = combine(checks, keys.state, settings.settings, busy, message) { c, k, s, b, m ->
        SetupUiState(
            checks = c,
            keys = k,
            developerModeConfirmed = s.developerModeConfirmed,
            vendor = PhoneVendor.current,
            needsLocationStep = PhoneSettings.locationServiceRequired,
            needsBackgroundStep = PhoneVendor.current.needsBackgroundStep,
            backgroundConfirmed = s.backgroundConfirmed,
            busy = b,
            message = m,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SetupUiState())

    /** Re-checks the phone every 2 s while the screen is visible (call from a LaunchedEffect). */
    suspend fun pollChecks() {
        while (true) {
            refresh()
            delay(2_000)
        }
    }

    suspend fun refresh() {
        val result = withContext(Dispatchers.IO) {
            PhoneChecks(
                androidAutoVersion = AndroidAutoHelper.installedVersion(appContext),
                server = AndroidAutoHelper.headUnitServerState(),
                locationEnabled = PhoneSettings.isLocationEnabled(appContext),
            )
        }
        checks.value = result
    }

    fun confirmDeveloperMode() {
        viewModelScope.launch { settings.setDeveloperModeConfirmed(true) }
    }

    fun confirmBackground() {
        viewModelScope.launch { settings.setBackgroundConfirmed(true) }
    }

    fun downloadKeys() {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            message.value = when (val result = keys.downloadSampleKeys()) {
                HeadUnitKeyRepository.DownloadResult.Success -> SetupMessage.KeysSaved
                HeadUnitKeyRepository.DownloadResult.FingerprintMismatch -> SetupMessage.Fingerprint
                is HeadUnitKeyRepository.DownloadResult.NetworkError -> SetupMessage.Network(result.message)
                is HeadUnitKeyRepository.DownloadResult.Rejected -> SetupMessage.KeyProblem(result.problem)
            }
            busy.value = false
        }
    }

    fun importKeys(uris: List<Uri>) {
        if (uris.isEmpty() || busy.value) return
        viewModelScope.launch {
            busy.value = true
            message.value = when (val state = keys.importFromUris(uris)) {
                is HeadUnitKeyRepository.KeyState.Ready -> SetupMessage.KeysSaved
                is HeadUnitKeyRepository.KeyState.Invalid -> SetupMessage.KeyProblem(state.problem)
                HeadUnitKeyRepository.KeyState.Missing -> SetupMessage.KeyProblem(HeadUnitKeys.Problem.MISSING_PARTS)
            }
            busy.value = false
        }
    }

    fun removeKeys() = keys.clear()

    fun messageShown() {
        message.value = null
    }
}
