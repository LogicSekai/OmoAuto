// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto

import android.content.Context
import com.logicsekai.omoauto.data.keys.HeadUnitKeyRepository
import com.logicsekai.omoauto.data.settings.SettingsRepository
import com.logicsekai.omoauto.data.update.UpdateChecker
import com.logicsekai.omoauto.data.update.UpdateManager
import com.logicsekai.omoauto.feature.about.AboutInfo
import com.logicsekai.omoauto.feature.support.SupportInfo
import com.logicsekai.omoauto.streaming.ProjectionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Manual dependency container: one place that builds the app's long-lived objects.
 * Feature ViewModels receive what they need from here (see navigation/OmoNavHost.kt).
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    /** Process-wide scope; lives as long as the app. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings = SettingsRepository(appContext, appScope)
    val keys = HeadUnitKeyRepository(appContext)
    val updates = UpdateManager(
        context = appContext,
        checker = UpdateChecker(BuildConfig.UPDATE_REPO, BuildConfig.VERSION_NAME),
        canSelfInstall = BuildConfig.SELF_UPDATE,
        scope = appScope,
    )
    val engine = ProjectionEngine(appContext, settings, keys, appScope)

    val aboutInfo = AboutInfo(
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        buildType = BuildConfig.BUILD_TYPE,
        communityUrl = BuildConfig.COMMUNITY_URL,
        sourceUrl = BuildConfig.SOURCE_URL,
    )

    val supportInfo = SupportInfo(
        sponsorUrl = BuildConfig.SPONSOR_URL,
        kofiUrl = BuildConfig.KOFI_URL,
        trakteerUrl = BuildConfig.TRAKTEER_URL,
        sourceUrl = BuildConfig.SOURCE_URL,
        communityUrl = BuildConfig.COMMUNITY_URL,
    )

    /** Screen to open from outside the UI (e.g. the update notification); consumed by OmoAutoApp. */
    val pendingRoute = MutableStateFlow<String?>(null)
}
