// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.json.JSONArray
import org.json.JSONObject

private val Context.omoSettingsStore: DataStore<Preferences> by preferencesDataStore(name = "omo_settings")

/** DataStore-backed settings. Read [settings] for the live value; call the setters to change it. */
class SettingsRepository(context: Context, scope: CoroutineScope) {

    private val store = context.applicationContext.omoSettingsStore

    val settings: StateFlow<OmoSettings> = store.data
        .map(::fromPreferences)
        .stateIn(scope, SharingStarted.Eagerly, OmoSettings())

    suspend fun current(): OmoSettings = store.data.map(::fromPreferences).first()

    suspend fun setOnboardingDone(done: Boolean) = store.edit { it[Keys.ONBOARDING_DONE] = done }
    suspend fun setDisclaimerAccepted(accepted: Boolean) = store.edit { it[Keys.DISCLAIMER] = accepted }
    suspend fun setBitrate(bps: Int) = store.edit { it[Keys.BITRATE] = bps }
    suspend fun setOutputWidth(px: Int) = store.edit { it[Keys.OUTPUT_WIDTH] = px }
    suspend fun setSourceWidth(px: Int) = store.edit { it[Keys.SOURCE_WIDTH] = px }
    suspend fun setRightMargin(px: Int) = store.edit { it[Keys.RIGHT_MARGIN] = px }
    suspend fun setVerticalMargin(px: Int) = store.edit { it[Keys.VERTICAL_MARGIN] = px }
    suspend fun setCustomFit(enabled: Boolean) = store.edit { it[Keys.CUSTOM_FIT] = enabled }
    suspend fun setCustomMargins(margins: FitMargins) = store.edit {
        val m = margins.clamped()
        it[Keys.FIT_LEFT] = m.left
        it[Keys.FIT_TOP] = m.top
        it[Keys.FIT_RIGHT] = m.right
        it[Keys.FIT_BOTTOM] = m.bottom
    }
    suspend fun setBoundedLiveQueue(enabled: Boolean) = store.edit { it[Keys.BOUNDED_QUEUE] = enabled }
    suspend fun setDisplayNightTheme(night: Boolean) = store.edit { it[Keys.DISPLAY_NIGHT] = night }
    suspend fun setAndroidAutoNightMode(night: Boolean) = store.edit { it[Keys.AA_NIGHT] = night }
    suspend fun setAppTheme(theme: AppTheme) = store.edit { it[Keys.APP_THEME] = theme.name }
    suspend fun setAutoReconnect(enabled: Boolean) = store.edit { it[Keys.AUTO_RECONNECT] = enabled }
    suspend fun setBackgroundConfirmed(confirmed: Boolean) = store.edit { it[Keys.BACKGROUND_CONFIRMED] = confirmed }
    suspend fun setDeveloperModeConfirmed(confirmed: Boolean) = store.edit { it[Keys.DEV_MODE_CONFIRMED] = confirmed }
    suspend fun setSkippedVersion(version: String?) = store.edit {
        if (version == null) it.remove(Keys.SKIPPED_VERSION) else it[Keys.SKIPPED_VERSION] = version
    }

    /** Adds or renames a motorcycle and marks it as the last used one. */
    suspend fun rememberBike(address: String, name: String) = store.edit { prefs ->
        val bikes = decodeBikes(prefs[Keys.SAVED_BIKES]).filterNot { it.address.equals(address, ignoreCase = true) }
        prefs[Keys.SAVED_BIKES] = encodeBikes(listOf(SavedBike(address, name)) + bikes)
        prefs[Keys.LAST_BIKE] = address
    }

    suspend fun forgetBike(address: String) = store.edit { prefs ->
        prefs[Keys.SAVED_BIKES] = encodeBikes(decodeBikes(prefs[Keys.SAVED_BIKES]).filterNot { it.address.equals(address, true) })
        if (prefs[Keys.LAST_BIKE].equals(address, ignoreCase = true)) prefs.remove(Keys.LAST_BIKE)
    }

    suspend fun renameBike(address: String, name: String) = store.edit { prefs ->
        prefs[Keys.SAVED_BIKES] = encodeBikes(
            decodeBikes(prefs[Keys.SAVED_BIKES]).map { if (it.address.equals(address, true)) it.copy(name = name) else it },
        )
    }

    private fun fromPreferences(p: Preferences) = OmoSettings(
        onboardingDone = p[Keys.ONBOARDING_DONE] ?: false,
        disclaimerAccepted = p[Keys.DISCLAIMER] ?: false,
        bitrateBps = (p[Keys.BITRATE] ?: OmoSettings.DEFAULT_BITRATE).takeIf { it in OmoSettings.BITRATE_OPTIONS }
            ?: OmoSettings.DEFAULT_BITRATE,
        outputWidth = (p[Keys.OUTPUT_WIDTH] ?: 960).takeIf { it in OmoSettings.OUTPUT_WIDTH_OPTIONS } ?: 960,
        sourceWidth = (p[Keys.SOURCE_WIDTH] ?: 1280).takeIf { it in OmoSettings.SOURCE_WIDTH_OPTIONS } ?: 1280,
        rightMarginPx = (p[Keys.RIGHT_MARGIN] ?: 120).takeIf { it in OmoSettings.RIGHT_MARGIN_OPTIONS } ?: 120,
        verticalMarginPx = (p[Keys.VERTICAL_MARGIN] ?: 45).takeIf { it in OmoSettings.VERTICAL_MARGIN_OPTIONS } ?: 45,
        customFit = p[Keys.CUSTOM_FIT] ?: false,
        customMargins = FitMargins(
            left = p[Keys.FIT_LEFT] ?: FitMargins.DEFAULT.left,
            top = p[Keys.FIT_TOP] ?: FitMargins.DEFAULT.top,
            right = p[Keys.FIT_RIGHT] ?: FitMargins.DEFAULT.right,
            bottom = p[Keys.FIT_BOTTOM] ?: FitMargins.DEFAULT.bottom,
        ).clamped(),
        boundedLiveQueue = p[Keys.BOUNDED_QUEUE] ?: true,
        displayNightTheme = p[Keys.DISPLAY_NIGHT] ?: true,
        androidAutoNightMode = p[Keys.AA_NIGHT] ?: false,
        appTheme = runCatching { AppTheme.valueOf(p[Keys.APP_THEME] ?: "SYSTEM") }.getOrDefault(AppTheme.SYSTEM),
        savedBikes = decodeBikes(p[Keys.SAVED_BIKES]),
        lastBikeAddress = p[Keys.LAST_BIKE],
        autoReconnect = p[Keys.AUTO_RECONNECT] ?: true,
        backgroundConfirmed = p[Keys.BACKGROUND_CONFIRMED] ?: false,
        developerModeConfirmed = p[Keys.DEV_MODE_CONFIRMED] ?: false,
        skippedVersion = p[Keys.SKIPPED_VERSION],
    )

    private fun encodeBikes(bikes: List<SavedBike>): String = JSONArray().apply {
        bikes.forEach { put(JSONObject().put("address", it.address).put("name", it.name)) }
    }.toString()

    private fun decodeBikes(raw: String?): List<SavedBike> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map {
                val o = array.getJSONObject(it)
                SavedBike(o.getString("address"), o.optString("name", o.getString("address")))
            }
        }.getOrDefault(emptyList())
    }

    private object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val DISCLAIMER = booleanPreferencesKey("disclaimer_accepted_v1")
        val BITRATE = intPreferencesKey("video_bitrate_bps")
        val OUTPUT_WIDTH = intPreferencesKey("output_width_px")
        val SOURCE_WIDTH = intPreferencesKey("source_width_px")
        val RIGHT_MARGIN = intPreferencesKey("right_margin_px")
        val VERTICAL_MARGIN = intPreferencesKey("vertical_margin_px")
        val CUSTOM_FIT = booleanPreferencesKey("custom_fit")
        val FIT_LEFT = intPreferencesKey("fit_left")
        val FIT_TOP = intPreferencesKey("fit_top")
        val FIT_RIGHT = intPreferencesKey("fit_right")
        val FIT_BOTTOM = intPreferencesKey("fit_bottom")
        val BOUNDED_QUEUE = booleanPreferencesKey("bounded_live_queue")
        val DISPLAY_NIGHT = booleanPreferencesKey("display_night_theme")
        val AA_NIGHT = booleanPreferencesKey("aa_night_mode")
        val APP_THEME = stringPreferencesKey("app_theme")
        val SAVED_BIKES = stringPreferencesKey("saved_bikes")
        val LAST_BIKE = stringPreferencesKey("last_bike_address")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        // Key name kept from 0.3.0 so existing confirmations survive the update.
        val BACKGROUND_CONFIRMED = booleanPreferencesKey("xiaomi_background_confirmed_v1")
        val DEV_MODE_CONFIRMED = booleanPreferencesKey("developer_mode_confirmed")
        val SKIPPED_VERSION = stringPreferencesKey("skipped_version")
    }
}
