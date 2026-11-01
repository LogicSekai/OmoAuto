// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.navigation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.logicsekai.omoauto.AppContainer
import com.logicsekai.omoauto.BuildConfig
import com.logicsekai.omoauto.data.settings.AppTheme
import com.logicsekai.omoauto.designsystem.theme.OmoTheme
import com.logicsekai.omoauto.feature.about.AboutRoute
import com.logicsekai.omoauto.feature.controls.ControlsRoute
import com.logicsekai.omoauto.feature.controls.ControlsViewModel
import com.logicsekai.omoauto.feature.diagnostics.DiagnosticsRoute
import com.logicsekai.omoauto.feature.diagnostics.DiagnosticsViewModel
import com.logicsekai.omoauto.feature.home.HomeRoute
import com.logicsekai.omoauto.feature.home.HomeViewModel
import com.logicsekai.omoauto.feature.onboarding.OnboardingRoute
import com.logicsekai.omoauto.feature.onboarding.OnboardingViewModel
import com.logicsekai.omoauto.feature.settings.SettingsRoute
import com.logicsekai.omoauto.feature.settings.SettingsViewModel
import com.logicsekai.omoauto.feature.setup.SetupRoute
import com.logicsekai.omoauto.feature.setup.SetupViewModel
import com.logicsekai.omoauto.feature.support.SupportRoute
import com.logicsekai.omoauto.feature.update.UpdateRoute
import com.logicsekai.omoauto.feature.update.UpdateViewModel

/** Route names. Keep them stable: they are also used as drawer keys. */
object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SETUP = "setup"
    const val CONTROLS = "controls"
    const val DIAGNOSTICS = "diagnostics"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val SUPPORT = "support"
    const val UPDATE = "update"
}

@Composable
fun OmoAutoApp(container: AppContainer, startWithOnboarding: Boolean) {
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val dark = when (settings.appTheme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }
    OmoTheme(darkTheme = dark) {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val current = entry?.destination?.route ?: Routes.HOME
        var drawerOpen by remember { mutableStateOf(false) }

        // Opened from outside the UI, e.g. by tapping the update notification.
        val pendingRoute by container.pendingRoute.collectAsStateWithLifecycle()
        LaunchedEffect(pendingRoute, entry) {
            val route = pendingRoute ?: return@LaunchedEffect
            if (entry == null || current == Routes.ONBOARDING) return@LaunchedEffect
            container.pendingRoute.value = null
            drawerOpen = false
            nav.navigateSingleTop(route)
        }

        OmoDrawerScaffold(
            open = drawerOpen,
            selectedRoute = current,
            onClose = { drawerOpen = false },
            onNavigate = { route ->
                drawerOpen = false
                nav.navigateSingleTop(route)
            },
            onStopAll = {
                drawerOpen = false
                container.engine.stopAll()
            },
        ) {
            NavHost(navController = nav, startDestination = if (startWithOnboarding) Routes.ONBOARDING else Routes.HOME) {
                composable(Routes.ONBOARDING) {
                    val vm = viewModel { OnboardingViewModel(container.settings) }
                    OnboardingRoute(vm, onFinished = {
                        nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                    })
                }
                composable(Routes.HOME) {
                    val vm = viewModel { HomeViewModel(container.engine, container.settings, container.keys, container.updates) }
                    HomeRoute(
                        viewModel = vm,
                        onOpenDrawer = { drawerOpen = true },
                        onOpenSetup = { nav.navigateSingleTop(Routes.SETUP) },
                        onOpenControls = { nav.navigateSingleTop(Routes.CONTROLS) },
                        onOpenDiagnostics = { nav.navigateSingleTop(Routes.DIAGNOSTICS) },
                        onOpenUpdate = { nav.navigateSingleTop(Routes.UPDATE) },
                    )
                }
                composable(Routes.SETUP) {
                    val vm = viewModel { SetupViewModel(container.appContext, container.keys, container.settings) }
                    SetupRoute(vm, onBack = { nav.popBackStack() })
                }
                composable(Routes.CONTROLS) {
                    val vm = viewModel { ControlsViewModel(container.engine) }
                    ControlsRoute(vm, onBack = { nav.popBackStack() })
                }
                composable(Routes.DIAGNOSTICS) {
                    val vm = viewModel { DiagnosticsViewModel(container.engine, "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") }
                    DiagnosticsRoute(vm, onBack = { nav.popBackStack() })
                }
                composable(Routes.SETTINGS) {
                    val vm = viewModel { SettingsViewModel(container.settings) }
                    SettingsRoute(vm, onBack = { nav.popBackStack() })
                }
                composable(Routes.ABOUT) {
                    AboutRoute(
                        info = container.aboutInfo,
                        onBack = { nav.popBackStack() },
                        onOpenSupport = { nav.navigateSingleTop(Routes.SUPPORT) },
                        onOpenUpdate = { nav.navigateSingleTop(Routes.UPDATE) },
                    )
                }
                composable(Routes.SUPPORT) {
                    SupportRoute(container.supportInfo, onBack = { nav.popBackStack() })
                }
                composable(Routes.UPDATE) {
                    val vm = viewModel { UpdateViewModel(container.updates) }
                    UpdateRoute(vm, versionLabel = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", onBack = { nav.popBackStack() })
                }
            }
        }
    }
}

/** Navigates without stacking duplicates; Home is always the root. */
private fun NavHostController.navigateSingleTop(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) {
        launchSingleTop = true
        popUpTo(Routes.HOME) { saveState = false }
    }
}
