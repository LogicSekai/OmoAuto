// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.feature.update

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.logicsekai.omoauto.data.phone.AndroidAutoHelper
import com.logicsekai.omoauto.data.update.UpdateManager
import com.logicsekai.omoauto.data.update.UpdateManager.State
import com.logicsekai.omoauto.designsystem.component.OmoCard
import com.logicsekai.omoauto.designsystem.component.OmoPrimaryButton
import com.logicsekai.omoauto.designsystem.component.OmoSecondaryButton
import com.logicsekai.omoauto.designsystem.component.OmoTopBar
import com.logicsekai.omoauto.designsystem.theme.OmoPalette
import java.util.Locale

class UpdateViewModel(private val manager: UpdateManager) : ViewModel() {
    val state = manager.state
    val autoCheck = manager.autoCheck
    val isEnabled get() = manager.isEnabled
    val canSelfInstall get() = manager.canSelfInstall
    val releasesUrl get() = manager.releasesUrl
    val lastCheckMillis get() = manager.lastCheckMillis

    init {
        manager.check()
    }

    fun check() = manager.check(force = true)
    fun download() = manager.download()
    fun cancelDownload() = manager.cancelDownload()
    fun install(context: Context) = manager.install(context)
    fun setAutoCheck(enabled: Boolean) = manager.setAutoCheck(enabled)
}

/** [versionLabel] is e.g. "0.3.0 (300)". */
@Composable
fun UpdateRoute(viewModel: UpdateViewModel, versionLabel: String, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val autoCheck by viewModel.autoCheck.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var needsPermission by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        OmoTopBar(title = stringResource(R.string.update_title), onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(shape = RoundedCornerShape(30.dp), color = OmoPalette.Orange, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(stringResource(R.string.update_installed), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
                        Text("OmoAuto $versionLabel", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    }
                }
            }

            if (!viewModel.isEnabled) {
                OmoCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.update_disabled_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.update_disabled_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                StatusCard(
                    state = state,
                    canSelfInstall = viewModel.canSelfInstall,
                    lastCheckMillis = viewModel.lastCheckMillis,
                    needsPermission = needsPermission,
                    onCheck = viewModel::check,
                    onDownload = viewModel::download,
                    onCancel = viewModel::cancelDownload,
                    onInstall = {
                        needsPermission = viewModel.install(context) == UpdateManager.InstallResult.NEEDS_PERMISSION
                    },
                    onOpenPage = { url -> AndroidAutoHelper.openUrl(context, url.ifBlank { viewModel.releasesUrl }) },
                )

                OmoCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.update_auto_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                            Text(stringResource(R.string.update_auto_caption), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(checked = autoCheck, onCheckedChange = viewModel::setAutoCheck)
                    }
                }

                Text(
                    stringResource(R.string.update_safety_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatusCard(
    state: State,
    canSelfInstall: Boolean,
    lastCheckMillis: Long,
    needsPermission: Boolean,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onOpenPage: (String) -> Unit,
) {
    OmoCard(Modifier.fillMaxWidth()) {
        when (state) {
            State.Idle, State.Checking -> {
                Title(stringResource(R.string.update_checking))
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            State.UpToDate -> {
                Title(stringResource(R.string.update_up_to_date))
                if (lastCheckMillis > 0) {
                    Caption(stringResource(R.string.update_last_checked, relativeTime(lastCheckMillis)))
                }
                Spacer(Modifier.height(14.dp))
                OmoSecondaryButton(stringResource(R.string.update_check_again), onClick = onCheck, icon = Icons.Rounded.Refresh)
            }

            is State.Available -> {
                val release = state.release
                Title(stringResource(R.string.update_available, release.version))
                if (release.apkSize > 0) Caption(stringResource(R.string.update_size, megabytes(release.apkSize)))
                ReleaseNotes(release.notes)
                Spacer(Modifier.height(14.dp))
                if (canSelfInstall && release.apkUrl != null) {
                    OmoPrimaryButton(stringResource(R.string.update_download), onClick = onDownload, icon = Icons.Rounded.Download)
                    Spacer(Modifier.height(10.dp))
                    OmoSecondaryButton(stringResource(R.string.update_open_page), onClick = { onOpenPage(release.pageUrl) }, icon = Icons.AutoMirrored.Rounded.OpenInNew)
                } else {
                    OmoPrimaryButton(stringResource(R.string.update_open_page), onClick = { onOpenPage(release.pageUrl) }, icon = Icons.AutoMirrored.Rounded.OpenInNew)
                }
            }

            is State.Downloading -> {
                Title(stringResource(R.string.update_downloading, state.release.version))
                Spacer(Modifier.height(12.dp))
                val progress = state.progress
                if (progress == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Caption("${(progress * 100).toInt()}%")
                }
                Spacer(Modifier.height(14.dp))
                OmoSecondaryButton(stringResource(R.string.update_cancel), onClick = onCancel)
            }

            is State.ReadyToInstall -> {
                Title(stringResource(R.string.update_ready, state.release.version))
                Caption(stringResource(R.string.update_ready_body))
                if (needsPermission) {
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.update_needs_permission), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(14.dp))
                OmoPrimaryButton(stringResource(R.string.update_install), onClick = onInstall, icon = Icons.Rounded.CheckCircle)
            }

            is State.Failed -> {
                Title(stringResource(R.string.update_failed_title))
                Caption(
                    stringResource(
                        when (state.problem) {
                            UpdateManager.Problem.NETWORK -> R.string.update_failed_network
                            UpdateManager.Problem.CHECKSUM -> R.string.update_failed_checksum
                            UpdateManager.Problem.INVALID_APK -> R.string.update_failed_invalid
                            UpdateManager.Problem.STORAGE -> R.string.update_failed_storage
                        },
                    ),
                )
                Spacer(Modifier.height(14.dp))
                val release = state.release
                if (release != null && canSelfInstall && release.apkUrl != null && state.problem == UpdateManager.Problem.NETWORK) {
                    OmoPrimaryButton(stringResource(R.string.update_retry), onClick = onDownload, icon = Icons.Rounded.Refresh)
                } else {
                    OmoPrimaryButton(stringResource(R.string.update_retry), onClick = onCheck, icon = Icons.Rounded.Refresh)
                }
                Spacer(Modifier.height(10.dp))
                OmoSecondaryButton(stringResource(R.string.update_open_page), onClick = { onOpenPage(release?.pageUrl.orEmpty()) }, icon = Icons.AutoMirrored.Rounded.OpenInNew)
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun Caption(text: String) {
    Spacer(Modifier.height(4.dp))
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ReleaseNotes(notes: String) {
    if (notes.isBlank()) return
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.update_whats_new), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(4.dp))
    Text(
        notes.replace("\r", "").take(MAX_NOTES),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 16,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun relativeTime(millis: Long): String =
    DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

private fun megabytes(bytes: Long): String = String.format(Locale.getDefault(), "%.1f MB", bytes / 1_048_576f)

private const val MAX_NOTES = 2_000
