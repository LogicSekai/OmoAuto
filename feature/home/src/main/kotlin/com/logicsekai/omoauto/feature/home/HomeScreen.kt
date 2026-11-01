// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.logicsekai.omoauto.data.phone.AndroidAutoHelper
import com.logicsekai.omoauto.data.phone.CastingApps
import com.logicsekai.omoauto.data.phone.PhoneSettings
import com.logicsekai.omoauto.data.settings.SavedBike
import com.logicsekai.omoauto.designsystem.component.LivePreview
import com.logicsekai.omoauto.designsystem.component.AccentRow
import com.logicsekai.omoauto.designsystem.component.ActionPill
import com.logicsekai.omoauto.designsystem.component.AddTileCard
import com.logicsekai.omoauto.designsystem.component.BarChart
import com.logicsekai.omoauto.designsystem.component.BikeCard
import com.logicsekai.omoauto.designsystem.component.OmoIconButton
import com.logicsekai.omoauto.designsystem.component.ScooterArt
import com.logicsekai.omoauto.designsystem.component.SectionHeader
import com.logicsekai.omoauto.designsystem.component.StatusChip
import com.logicsekai.omoauto.designsystem.component.TickSparkline
import com.logicsekai.omoauto.designsystem.component.WidgetCard
import com.logicsekai.omoauto.designsystem.theme.OmoPalette
import com.logicsekai.omoauto.designsystem.theme.OmoThemeExtras
import com.logicsekai.omoauto.streaming.AaFailure
import com.logicsekai.omoauto.streaming.AaPhase
import com.logicsekai.omoauto.streaming.DisplayPhase
import com.logicsekai.omoauto.streaming.StartResult
import com.logicsekai.omoauto.streaming.link.WifiDirectLink
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    onOpenDrawer: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenControls: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenUpdate: () -> Unit,
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showSearch by remember { mutableStateOf(false) }
    var bikeMenu by remember { mutableStateOf<SavedBike?>(null) }
    var askLocation by remember { mutableStateOf(false) }
    val permissionDenied = stringResource(R.string.home_permission_denied)
    val encoderFailed = stringResource(R.string.home_fail_encoder)
    val withPermissions = rememberWithLinkPermissions(
        onDenied = { scope.launch { snackbar.showSnackbar(permissionDenied) } },
        onLocationOff = { askLocation = true },
    )

    val state = ui.projection
    val connectedAddress = state.linkedPeer?.address

    fun startAa() {
        when (viewModel.startAndroidAuto()) {
            StartResult.NO_KEYS -> onOpenSetup()
            StartResult.NOT_INSTALLED -> AndroidAutoHelper.openPlayStore(context)
            StartResult.ENCODER_UNAVAILABLE -> scope.launch { snackbar.showSnackbar(encoderFailed) }
            StartResult.STARTED, StartResult.ALREADY_RUNNING -> Unit
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Header(onOpenDrawer = onOpenDrawer, streaming = state.isStreamingToBike, linked = state.isLinked)
            }

            if (ui.update != null) {
                item {
                    val release = ui.update!!
                    UpdateBanner(
                        version = release.version,
                        onOpen = onOpenUpdate,
                        onSkip = { viewModel.skipUpdate(release.version) },
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }

            item { Column {
                SectionHeader(
                    title = stringResource(R.string.home_my_bikes, ui.settings.savedBikes.size),
                    actionLabel = stringResource(R.string.home_search_action),
                    onAction = { withPermissions { viewModel.startSearch(); showSearch = true } },
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(4.dp))
                val colors = listOf(OmoThemeExtras.colors.cardPink, OmoThemeExtras.colors.cardSky, OmoThemeExtras.colors.cardLilac)
                LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(ui.settings.savedBikes, key = { it.address }) { bike ->
                        val index = ui.settings.savedBikes.indexOf(bike)
                        val connected = bike.address.equals(connectedAddress, ignoreCase = true)
                        BikeCard(
                            name = bike.name,
                            caption = if (connected) stringResource(R.string.home_bike_connected) else stringResource(R.string.home_bike_tap_to_connect),
                            color = colors[index % colors.size],
                            connected = connected,
                            onClick = {
                                if (connected) bikeMenu = bike
                                else withPermissions { viewModel.connect(bike.address, bike.name) }
                            },
                        )
                    }
                    item {
                        AddTileCard(
                            title = stringResource(R.string.home_add_bike),
                            caption = stringResource(R.string.home_add_bike_caption),
                            onClick = { withPermissions { viewModel.startSearch(); showSearch = true } },
                        )
                    }
                }
            } }

            item {
                MainAction(
                    ui = ui,
                    onStart = ::startAa,
                    onStop = viewModel::stopAndroidAuto,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }

            item {
                Attention(
                    ui = ui,
                    onOpenSetup = onOpenSetup,
                    onOpenPlayStore = { AndroidAutoHelper.openPlayStore(context) },
                    onWakeAndroidAuto = { AndroidAutoHelper.openAndroidAutoSettings(context) },
                    onOpenAppInfo = { packageName -> CastingApps.openAppInfo(context, packageName) },
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }

            item {
                AccentRow(
                    text = stringResource(if (ui.keysReady) R.string.home_setup_ready else R.string.home_setup_needed),
                    caption = stringResource(R.string.home_setup_caption),
                    accent = if (ui.keysReady) OmoThemeExtras.colors.good else MaterialTheme.colorScheme.primary,
                    onClick = onOpenSetup,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }

            if (state.isAndroidAutoOn) {
                item {
                    LivePreview(
                        caption = stringResource(
                            if (state.isStreamingToBike) R.string.home_preview_on_bike else R.string.home_preview_phone_only,
                        ),
                        onSurfaceAvailable = viewModel::setPreviewSurface,
                        onSurfaceDestroyed = viewModel::clearPreviewSurface,
                        onTouch = viewModel::touchPreview,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
                item {
                    AccentRow(
                        text = stringResource(R.string.home_controls),
                        caption = stringResource(R.string.home_controls_caption),
                        onClick = onOpenControls,
                        accent = OmoPalette.Blue,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }

            item { Column {
                SectionHeader(
                    title = stringResource(R.string.home_widgets),
                    actionLabel = stringResource(R.string.home_details),
                    onAction = onOpenDiagnostics,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(4.dp))
                Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    WidgetCard(
                        title = stringResource(R.string.home_widget_fps),
                        modifier = Modifier.weight(1f),
                        trailing = { Icon(Icons.Rounded.Bolt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                    ) {
                        TickSparkline(values = state.stats.fpsHistory.ifEmpty { List(8) { 0.1f } })
                        Spacer(Modifier.height(10.dp))
                        Text(
                            String.format(Locale.ROOT, "%.0f/60", state.stats.fps),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(stringResource(R.string.home_widget_fps_unit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    WidgetCard(
                        title = stringResource(R.string.home_widget_bitrate),
                        modifier = Modifier.weight(1f),
                        trailing = { Icon(Icons.Rounded.BarChart, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                    ) {
                        val history = state.stats.mbpsHistory.takeLast(5)
                        val bars = (List(5 - history.size) { 0f } + history)
                        BarChart(
                            values = bars,
                            labels = listOf("-4", "-3", "-2", "-1", stringResource(R.string.home_widget_now)),
                            topLabels = bars.map { String.format(Locale.ROOT, "%.0f", it * 10) },
                            height = 84.dp,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.home_widget_target, ui.settings.bitrateBps / 1_000_000),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    if (showSearch) {
        SearchSheet(
            state = state,
            onSearchAgain = { withPermissions { viewModel.startSearch() } },
            onPick = { peer ->
                showSearch = false
                withPermissions { viewModel.connect(peer.address, peer.displayName) }
            },
            onDismiss = {
                showSearch = false
                viewModel.stopSearch()
            },
        )
    }

    if (askLocation) {
        AlertDialog(
            onDismissRequest = { askLocation = false },
            title = { Text(stringResource(R.string.home_location_title)) },
            text = { Text(stringResource(R.string.home_location_body)) },
            confirmButton = {
                TextButton(onClick = {
                    askLocation = false
                    PhoneSettings.openLocationSettings(context)
                }) { Text(stringResource(R.string.home_location_open)) }
            },
            dismissButton = { TextButton(onClick = { askLocation = false }) { Text(stringResource(R.string.home_location_later)) } },
        )
    }

    bikeMenu?.let { bike ->
        AlertDialog(
            onDismissRequest = { bikeMenu = null },
            title = { Text(bike.name) },
            text = { Text(stringResource(R.string.home_bike_menu_text)) },
            confirmButton = {
                TextButton(onClick = { viewModel.disconnect(); bikeMenu = null }) { Text(stringResource(R.string.home_disconnect)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.disconnect(); viewModel.forgetBike(bike.address); bikeMenu = null }) {
                    Text(stringResource(R.string.home_forget))
                }
            },
        )
    }
}

@Composable
private fun Header(onOpenDrawer: () -> Unit, streaming: Boolean, linked: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OmoIconButton(Icons.AutoMirrored.Rounded.Sort, stringResource(R.string.home_menu), onOpenDrawer)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.home_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            StatusChip(
                text = stringResource(
                    when {
                        streaming -> R.string.home_chip_live
                        linked -> R.string.home_chip_linked
                        else -> R.string.home_chip_offline
                    },
                ),
                color = when {
                    streaming -> OmoThemeExtras.colors.good
                    linked -> MaterialTheme.colorScheme.secondary
                    else -> OmoThemeExtras.colors.muted
                },
            )
        }
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            ScooterArt(Modifier.size(40.dp), bodyColor = MaterialTheme.colorScheme.primary, accentColor = Color.White)
        }
    }
}

@Composable
private fun MainAction(ui: HomeUiState, onStart: () -> Unit, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val state = ui.projection
    val running = state.isAndroidAutoOn
    ActionPill(
        title = stringResource(if (running) R.string.home_stop_aa else R.string.home_start_aa),
        subtitle = if (running) "${aaPhaseLine(state.androidAuto.phase)} · ${linkLine(state)}" else linkLine(state),
        trailingTop = if (state.isStreamingToBike) "fps" else null,
        trailingBottom = if (state.isStreamingToBike) String.format(Locale.ROOT, "%.0f/60", state.stats.fps) else null,
        color = if (running) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
        onClick = if (running) onStop else onStart,
        modifier = modifier,
        leading = {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (running) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White)
            }
        },
    )
}

/** Explains failures and offers the right next step. */
@Composable
private fun Attention(
    ui: HomeUiState,
    onOpenSetup: () -> Unit,
    onOpenPlayStore: () -> Unit,
    onWakeAndroidAuto: () -> Unit,
    onOpenAppInfo: (packageName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = ui.projection
    val failure = state.androidAuto.failure
    val link = state.link
    val message: String?
    val action: Pair<String, () -> Unit>?
    when {
        failure != null && state.androidAuto.phase != AaPhase.STREAMING -> {
            message = aaFailureText(failure)
            action = when (failure) {
                AaFailure.NOT_INSTALLED -> stringResource(R.string.home_action_install) to onOpenPlayStore
                // Bringing Android Auto to the front unfreezes it; its menu also restarts the server.
                AaFailure.NOT_ANSWERING -> stringResource(R.string.home_action_wake_aa) to onWakeAndroidAuto
                AaFailure.NO_KEYS, AaFailure.KEYS_REJECTED, AaFailure.SERVER_NOT_RUNNING ->
                    stringResource(R.string.home_action_setup) to onOpenSetup
                else -> null
            }
        }
        state.display.phase == DisplayPhase.BUSY -> {
            // The display serves one phone app at a time; name the app that still holds it.
            val culprit = state.display.blockingApps.firstOrNull()
            if (culprit != null) {
                message = stringResource(R.string.home_display_taken_by, culprit.label)
                action = stringResource(R.string.home_action_app_info, culprit.label) to { onOpenAppInfo(culprit.packageName) }
            } else {
                message = stringResource(R.string.home_display_taken)
                action = null
            }
        }
        state.display.phase == DisplayPhase.HIDDEN -> {
            message = stringResource(R.string.home_display_busy_help)
            action = null
        }
        link is WifiDirectLink.State.Failed -> {
            message = linkFailure(link.reason)
            action = null
        }
        else -> {
            message = null
            action = null
        }
    }
    AnimatedVisibility(visible = message != null, modifier = modifier) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (link is WifiDirectLink.State.Failed) Icons.Rounded.LinkOff else Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    message.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                if (action != null) {
                    TextButton(onClick = action.second) { Text(action.first) }
                }
            }
        }
    }
}

@Composable
private fun UpdateBanner(version: String, onOpen: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.home_update_available, version),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onSkip) { Text(stringResource(R.string.home_update_skip)) }
            TextButton(onClick = onOpen) { Text(stringResource(R.string.home_update_open)) }
        }
    }
}
