// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.logicsekai.omoauto.data.update.UpdateNotifier
import com.logicsekai.omoauto.navigation.OmoAutoApp
import com.logicsekai.omoauto.navigation.Routes
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as OmoAutoApplication).container
        handleOpenRequest(intent)
        var ready = false
        splash.setKeepOnScreenCondition { !ready }

        // Wait for the first settings read so we open the right start screen without flashing.
        lifecycleScope.launch {
            val initial = container.settings.current()
            setContent {
                OmoAutoApp(container = container, startWithOnboarding = !initial.onboardingDone)
            }
            ready = true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpenRequest(intent)
    }

    /** The update notification asks to open the Update screen. */
    private fun handleOpenRequest(intent: Intent?) {
        if (intent?.getStringExtra(UpdateNotifier.EXTRA_OPEN) == UpdateNotifier.OPEN_UPDATE) {
            intent.removeExtra(UpdateNotifier.EXTRA_OPEN)
            (application as OmoAutoApplication).container.pendingRoute.value = Routes.UPDATE
        }
    }
}
