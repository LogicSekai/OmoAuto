// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.feature.home

import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.logicsekai.omoauto.data.keys.HeadUnitKeyRepository
import com.logicsekai.omoauto.data.settings.OmoSettings
import com.logicsekai.omoauto.data.settings.SettingsRepository
import com.logicsekai.omoauto.data.update.UpdateChecker
import com.logicsekai.omoauto.data.update.UpdateManager
import com.logicsekai.omoauto.streaming.ProjectionEngine
import com.logicsekai.omoauto.streaming.ProjectionState
import com.logicsekai.omoauto.streaming.StartResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val projection: ProjectionState = ProjectionState(),
    val settings: OmoSettings = OmoSettings(),
    val keysReady: Boolean = false,
    val update: UpdateChecker.Release? = null,
)

class HomeViewModel(
    private val engine: ProjectionEngine,
    private val settings: SettingsRepository,
    keys: HeadUnitKeyRepository,
    private val updates: UpdateManager,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(engine.state, settings.settings, keys.state, updates.state) { p, s, k, u ->
        HomeUiState(
            projection = p,
            settings = s,
            keysReady = k is HeadUnitKeyRepository.KeyState.Ready,
            // Any state that carries a release means a newer version is known.
            update = u.release?.takeIf { it.version != s.skippedVersion },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        updates.check()
    }

    fun startSearch() = engine.startSearch()
    fun stopSearch() = engine.stopSearch()
    fun connect(address: String, name: String) = engine.connectBike(address, name)
    fun disconnect() = engine.disconnectBike()

    fun startAndroidAuto(): StartResult = engine.startAndroidAuto()
    fun stopAndroidAuto() = engine.stopAndroidAuto()

    fun setPreviewSurface(surface: Surface, width: Int, height: Int) = engine.setPreviewSurface(surface, width, height)
    fun clearPreviewSurface(surface: Surface) = engine.clearPreviewSurface(surface)

    fun touchPreview(action: Int, x: Float, y: Float, width: Int, height: Int) =
        engine.touchPreview(action, x, y, width, height)

    fun skipUpdate(version: String) {
        viewModelScope.launch { settings.setSkippedVersion(version) }
    }

    fun forgetBike(address: String) {
        viewModelScope.launch { settings.forgetBike(address) }
    }
}
