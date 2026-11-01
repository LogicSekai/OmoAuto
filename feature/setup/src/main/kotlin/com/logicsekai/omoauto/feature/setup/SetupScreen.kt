// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.feature.setup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.logicsekai.omoauto.data.keys.HeadUnitKeyRepository
import com.logicsekai.omoauto.data.phone.AndroidAutoHelper
import com.logicsekai.omoauto.data.phone.PhoneSettings
import com.logicsekai.omoauto.data.phone.PhoneVendor
import com.logicsekai.omoauto.designsystem.component.OmoCard
import com.logicsekai.omoauto.designsystem.component.OmoPrimaryButton
import com.logicsekai.omoauto.designsystem.component.OmoSecondaryButton
import com.logicsekai.omoauto.designsystem.component.OmoTopBar
import com.logicsekai.omoauto.designsystem.component.ProgressRing
import com.logicsekai.omoauto.designsystem.component.ScooterArt
import com.logicsekai.omoauto.designsystem.component.StepCard
import com.logicsekai.omoauto.designsystem.component.StepState
import com.logicsekai.omoauto.designsystem.theme.OmoPalette
import com.logicsekai.omoauto.protocol.aa.HeadUnitKeys
import java.text.DateFormat

@Composable
fun SetupRoute(viewModel: SetupViewModel, onBack: () -> Unit) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var selected by rememberSaveable { mutableStateOf<SetupStep?>(null) }
    var showKeyInfo by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.importKeys(uris)
    }

    LaunchedEffect(Unit) { viewModel.pollChecks() }

    val messageText = ui.message?.let { messageText(it) }
    LaunchedEffect(ui.message) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            viewModel.messageShown()
        }
    }

    val focus = selected ?: ui.current
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            OmoTopBar(title = stringResource(R.string.setup_title), onBack = onBack)
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ProgressHeader(ui)
                ui.steps.forEachIndexed { index, step ->
                    val state = when {
                        ui.isDone(step) -> StepState.DONE
                        step == focus -> StepState.CURRENT
                        else -> StepState.PENDING
                    }
                    StepCard(
                        index = index + 1,
                        title = stepTitle(step),
                        state = state,
                        trailing = stepTrailing(step, ui),
                        onClick = { selected = step },
                    )
                    if (step == focus) {
                        StepDetail(
                            step = step,
                            ui = ui,
                            onOpenAaSettings = { AndroidAutoHelper.openAndroidAutoSettings(context) },
                            onOpenAppInfo = { AndroidAutoHelper.openAppInfo(context) },
                            onOpenAutostart = {
                                // Vendor screens move between OS versions; fall back to app info.
                                if (!PhoneSettings.openAutostart(context, ui.vendor)) AndroidAutoHelper.openAppInfo(context)
                            },
                            onOpenBatteryList = { PhoneSettings.openBatteryOptimisation(context) },
                            onOpenLocation = { PhoneSettings.openLocationSettings(context) },
                            onInstall = { AndroidAutoHelper.openPlayStore(context) },
                            onConfirmDeveloperMode = { viewModel.confirmDeveloperMode(); selected = null },
                            onConfirmBackground = { viewModel.confirmBackground(); selected = null },
                            onDownloadKeys = viewModel::downloadKeys,
                            onImportKeys = { picker.launch(arrayOf("*/*")) },
                            onRemoveKeys = viewModel::removeKeys,
                            onKeyInfo = { showKeyInfo = true },
                        )
                    }
                }
                Text(
                    stringResource(if (ui.current == null) R.string.setup_all_done else R.string.setup_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    if (showKeyInfo) {
        AlertDialog(
            onDismissRequest = { showKeyInfo = false },
            title = { Text(stringResource(R.string.setup_keys_info_title)) },
            text = { Text(stringResource(R.string.setup_keys_info_body)) },
            confirmButton = { TextButton(onClick = { showKeyInfo = false }) { Text(stringResource(R.string.setup_ok)) } },
            dismissButton = {
                TextButton(onClick = { AndroidAutoHelper.openUrl(context, HeadUnitKeyRepository.SAMPLE_KEYS_BROWSE_URL) }) {
                    Text(stringResource(R.string.setup_keys_view_github))
                }
            },
        )
    }
}

@Composable
private fun ProgressHeader(ui: SetupUiState) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                ProgressRing(progress = ui.progress, label = "${(ui.progress * 100).toInt()}%")
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.setup_progress, ui.steps.count { ui.isDone(it) }, ui.steps.size),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.setup_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ScooterArt(Modifier.size(120.dp), bodyColor = OmoPalette.SkyBright, accentColor = OmoPalette.Orange)
        }
    }
}

@Composable
private fun stepTitle(step: SetupStep): String = stringResource(
    when (step) {
        SetupStep.INSTALL -> R.string.setup_step_install
        SetupStep.DEVELOPER_MODE -> R.string.setup_step_developer
        SetupStep.HEAD_UNIT_SERVER -> R.string.setup_step_server
        SetupStep.KEYS -> R.string.setup_step_keys
        SetupStep.LOCATION -> R.string.setup_step_location
        SetupStep.BACKGROUND -> R.string.setup_step_background
    },
)

@Composable
private fun stepTrailing(step: SetupStep, ui: SetupUiState): String? = when (step) {
    SetupStep.INSTALL -> ui.checks.androidAutoVersion?.substringBefore(' ')?.let { "v$it" }
    SetupStep.HEAD_UNIT_SERVER -> stringResource(
        if (ui.checks.server == AndroidAutoHelper.ServerState.RUNNING) R.string.setup_server_on else R.string.setup_server_off,
    )
    SetupStep.KEYS -> if (ui.keys is HeadUnitKeyRepository.KeyState.Ready) stringResource(R.string.setup_keys_ok) else null
    SetupStep.LOCATION -> stringResource(if (ui.checks.locationEnabled) R.string.setup_location_on else R.string.setup_location_off)
    else -> null
}

@Composable
private fun StepDetail(
    step: SetupStep,
    ui: SetupUiState,
    onOpenAaSettings: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onOpenAutostart: () -> Unit,
    onOpenBatteryList: () -> Unit,
    onOpenLocation: () -> Unit,
    onInstall: () -> Unit,
    onConfirmDeveloperMode: () -> Unit,
    onConfirmBackground: () -> Unit,
    onDownloadKeys: () -> Unit,
    onImportKeys: () -> Unit,
    onRemoveKeys: () -> Unit,
    onKeyInfo: () -> Unit,
) {
    OmoCard(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (step) {
                SetupStep.INSTALL -> {
                    Body(
                        if (ui.checks.androidAutoVersion != null) {
                            stringResource(R.string.setup_install_done, ui.checks.androidAutoVersion)
                        } else {
                            stringResource(R.string.setup_install_body)
                        },
                    )
                    if (ui.checks.androidAutoVersion == null) {
                        OmoPrimaryButton(stringResource(R.string.setup_install_button), onInstall, icon = Icons.AutoMirrored.Rounded.OpenInNew)
                    }
                }
                SetupStep.DEVELOPER_MODE -> {
                    Numbered(
                        stringResource(R.string.setup_dev_1),
                        stringResource(R.string.setup_dev_2),
                        stringResource(R.string.setup_dev_3),
                    )
                    OmoPrimaryButton(stringResource(R.string.setup_open_aa_settings), onOpenAaSettings, icon = Icons.AutoMirrored.Rounded.OpenInNew)
                    OmoSecondaryButton(stringResource(R.string.setup_done), onConfirmDeveloperMode, icon = Icons.Rounded.Done)
                }
                SetupStep.HEAD_UNIT_SERVER -> {
                    Numbered(
                        stringResource(R.string.setup_server_1),
                        stringResource(R.string.setup_server_2),
                        stringResource(R.string.setup_server_3),
                        stringResource(R.string.setup_server_4),
                    )
                    Body(
                        stringResource(
                            when (ui.checks.server) {
                                AndroidAutoHelper.ServerState.RUNNING -> R.string.setup_server_running
                                AndroidAutoHelper.ServerState.NOT_RUNNING -> R.string.setup_server_not_running
                                AndroidAutoHelper.ServerState.UNKNOWN -> R.string.setup_server_unknown
                            },
                        ),
                        color = if (ui.checks.server == AndroidAutoHelper.ServerState.RUNNING) OmoPalette.Good else MaterialTheme.colorScheme.primary,
                    )
                    OmoPrimaryButton(stringResource(R.string.setup_open_aa_settings), onOpenAaSettings, icon = Icons.AutoMirrored.Rounded.OpenInNew)
                }
                SetupStep.KEYS -> {
                    Body(stringResource(R.string.setup_keys_body))
                    when (val keys = ui.keys) {
                        is HeadUnitKeyRepository.KeyState.Ready -> Body(
                            stringResource(
                                R.string.setup_keys_ready,
                                keys.name,
                                DateFormat.getDateInstance(DateFormat.MEDIUM).format(keys.validUntil),
                            ),
                            color = OmoPalette.Good,
                        )
                        is HeadUnitKeyRepository.KeyState.Invalid -> Body(problemText(keys.problem), color = MaterialTheme.colorScheme.error)
                        HeadUnitKeyRepository.KeyState.Missing -> Unit
                    }
                    if (ui.busy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(10.dp))
                            Body(stringResource(R.string.setup_keys_downloading))
                        }
                    }
                    OmoPrimaryButton(stringResource(R.string.setup_keys_get), onDownloadKeys, icon = Icons.Rounded.CloudDownload, enabled = !ui.busy)
                    OmoSecondaryButton(stringResource(R.string.setup_keys_import), onImportKeys, icon = Icons.Rounded.FileOpen, enabled = !ui.busy)
                    Row {
                        TextButton(onClick = onKeyInfo) { Text(stringResource(R.string.setup_keys_info_title)) }
                        if (ui.keys is HeadUnitKeyRepository.KeyState.Ready) {
                            TextButton(onClick = onRemoveKeys) { Text(stringResource(R.string.setup_keys_remove), color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                SetupStep.LOCATION -> {
                    Body(stringResource(R.string.setup_location_body))
                    Body(
                        stringResource(if (ui.checks.locationEnabled) R.string.setup_location_status_on else R.string.setup_location_status_off),
                        color = if (ui.checks.locationEnabled) OmoPalette.Good else MaterialTheme.colorScheme.primary,
                    )
                    if (!ui.checks.locationEnabled) {
                        OmoPrimaryButton(stringResource(R.string.setup_location_button), onOpenLocation, icon = Icons.Rounded.LocationOn)
                    }
                }
                SetupStep.BACKGROUND -> {
                    val guide = backgroundGuide(ui.vendor)
                    Body(stringResource(R.string.setup_bg_body, guide.brands))
                    Numbered(guide.first, guide.second, stringResource(R.string.setup_bg_done_line))
                    if (ui.vendor.autostartScreens.isNotEmpty()) {
                        OmoPrimaryButton(stringResource(R.string.setup_open_autostart), onOpenAutostart, icon = Icons.Rounded.PlayCircle)
                        OmoSecondaryButton(stringResource(R.string.setup_open_app_info), onOpenAppInfo, icon = Icons.AutoMirrored.Rounded.OpenInNew)
                    } else {
                        OmoPrimaryButton(stringResource(R.string.setup_open_app_info), onOpenAppInfo, icon = Icons.AutoMirrored.Rounded.OpenInNew)
                    }
                    OmoSecondaryButton(stringResource(R.string.setup_done), onConfirmBackground, icon = Icons.Rounded.Refresh)
                    TextButton(onClick = onOpenBatteryList) { Text(stringResource(R.string.setup_open_battery_list)) }
                }
            }
        }
    }
}

private class BackgroundGuide(val brands: String, val first: String, val second: String)

/** Brand-specific wording for the "Keep Android Auto running" step. */
@Composable
private fun backgroundGuide(vendor: PhoneVendor): BackgroundGuide {
    val (brands, first, second) = when (vendor) {
        PhoneVendor.XIAOMI -> Triple(R.string.setup_bg_brand_xiaomi, R.string.setup_bg_xiaomi_1, R.string.setup_bg_xiaomi_2)
        PhoneVendor.SAMSUNG -> Triple(R.string.setup_bg_brand_samsung, R.string.setup_bg_samsung_1, R.string.setup_bg_samsung_2)
        PhoneVendor.OPPO_REALME_ONEPLUS -> Triple(R.string.setup_bg_brand_oppo, R.string.setup_bg_oppo_1, R.string.setup_bg_oppo_2)
        PhoneVendor.VIVO -> Triple(R.string.setup_bg_brand_vivo, R.string.setup_bg_vivo_1, R.string.setup_bg_vivo_2)
        PhoneVendor.HUAWEI_HONOR -> Triple(R.string.setup_bg_brand_huawei, R.string.setup_bg_huawei_1, R.string.setup_bg_huawei_2)
        PhoneVendor.TRANSSION -> Triple(R.string.setup_bg_brand_transsion, R.string.setup_bg_transsion_1, R.string.setup_bg_transsion_2)
        PhoneVendor.OTHER -> Triple(R.string.setup_bg_brand_other, R.string.setup_bg_other_1, R.string.setup_bg_other_2)
    }
    return BackgroundGuide(stringResource(brands), stringResource(first), stringResource(second))
}

@Composable
private fun Body(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
}

@Composable
private fun Numbered(vararg lines: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lines.forEachIndexed { i, line ->
            Row {
                Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(8.dp))
                Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun problemText(problem: HeadUnitKeys.Problem): String = stringResource(
    when (problem) {
        HeadUnitKeys.Problem.MISSING_PARTS -> R.string.setup_problem_missing
        HeadUnitKeys.Problem.CERTIFICATE_UNREADABLE -> R.string.setup_problem_cert_unreadable
        HeadUnitKeys.Problem.CERTIFICATE_EXPIRED -> R.string.setup_problem_cert_expired
        HeadUnitKeys.Problem.CERTIFICATE_NOT_YET_VALID -> R.string.setup_problem_cert_not_yet
        HeadUnitKeys.Problem.KEY_PASSWORD_PROTECTED -> R.string.setup_problem_key_password
        HeadUnitKeys.Problem.KEY_UNSUPPORTED -> R.string.setup_problem_key_unsupported
        HeadUnitKeys.Problem.KEY_UNREADABLE -> R.string.setup_problem_key_unreadable
        HeadUnitKeys.Problem.KEY_MISMATCH -> R.string.setup_problem_key_mismatch
    },
)

@Composable
private fun messageText(message: SetupMessage): String = when (message) {
    SetupMessage.KeysSaved -> stringResource(R.string.setup_msg_saved)
    SetupMessage.Fingerprint -> stringResource(R.string.setup_msg_fingerprint)
    is SetupMessage.Network -> stringResource(R.string.setup_msg_network, message.detail)
    is SetupMessage.KeyProblem -> problemText(message.problem)
}
