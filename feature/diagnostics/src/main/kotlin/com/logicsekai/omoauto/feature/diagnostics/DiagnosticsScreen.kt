// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.feature.diagnostics

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.logicsekai.omoauto.common.OmoLog
import com.logicsekai.omoauto.designsystem.component.MeterRow
import com.logicsekai.omoauto.designsystem.component.OmoCard
import com.logicsekai.omoauto.designsystem.component.OmoSecondaryButton
import com.logicsekai.omoauto.designsystem.component.OmoTopBar
import com.logicsekai.omoauto.designsystem.component.ScooterArt
import com.logicsekai.omoauto.designsystem.theme.OmoPalette
import com.logicsekai.omoauto.designsystem.theme.OmoThemeExtras
import com.logicsekai.omoauto.streaming.ProjectionEngine
import com.logicsekai.omoauto.streaming.ProjectionState
import com.logicsekai.omoauto.streaming.link.WifiDirectLink
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.util.Locale

class DiagnosticsViewModel(private val engine: ProjectionEngine, private val appVersion: String) : ViewModel() {
    val state: StateFlow<ProjectionState> = engine.state

    val events: StateFlow<List<String>> = flow {
        while (true) {
            emit(OmoLog.history.snapshot().takeLast(80))
            delay(2_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun report(): String = engine.diagnosticsReport(appVersion)
}

/** Connection quality score 0..100 from what the phone can measure. */
internal fun qualityScore(state: ProjectionState): Int {
    val s = state.stats
    if (!state.isStreamingToBike || s.framesSent == 0L) return 0
    val fps = (s.fps / 60.0).coerceIn(0.0, 1.0)
    val write = (1.0 - s.writeMeanMs / 16.7).coerceIn(0.0, 1.0)
    val stability = (1.0 - s.droppedFrames.toDouble() / (s.framesSent + s.droppedFrames).coerceAtLeast(1)).coerceIn(0.0, 1.0)
    return ((fps * 0.45 + write * 0.25 + stability * 0.30) * 100).toInt()
}

@Composable
fun DiagnosticsRoute(viewModel: DiagnosticsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val score = qualityScore(state)
    val stars = when {
        score >= 80 -> 3
        score >= 55 -> 2
        score >= 30 -> 1
        else -> 0
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        OmoTopBar(title = stringResource(R.string.diag_title), onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(
                    when (stars) {
                        3 -> R.string.diag_excellent
                        2 -> R.string.diag_good
                        1 -> R.string.diag_fair
                        else -> if (state.isStreamingToBike) R.string.diag_poor else R.string.diag_idle
                    },
                ),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { i ->
                    Icon(
                        Icons.Rounded.Star,
                        contentDescription = null,
                        tint = if (i < stars) OmoPalette.Fair else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(if (i == 1) 34.dp else 28.dp),
                    )
                }
            }
            ScoreBadge(score, active = state.isStreamingToBike)

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                OmoSecondaryButton(
                    stringResource(R.string.diag_share),
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, "OmoAuto diagnostics")
                            .putExtra(Intent.EXTRA_TEXT, viewModel.report())
                        context.startActivity(Intent.createChooser(send, null))
                    },
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Share,
                )
                OmoSecondaryButton(
                    stringResource(R.string.diag_copy),
                    onClick = { clipboard.setText(AnnotatedString(viewModel.report())) },
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.ContentCopy,
                )
            }

            Meters(state)
            Details(state)

            OmoCard(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.diag_events), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (events.isEmpty()) stringResource(R.string.diag_no_events) else events.joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 14.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(R.string.diag_scope_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ScoreBadge(score: Int, active: Boolean) {
    Box(contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier
                .size(132.dp)
                .clip(CircleShape)
                .border(4.dp, MaterialTheme.colorScheme.primary, CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            ScooterArt(Modifier.size(100.dp), bodyColor = MaterialTheme.colorScheme.primary, accentColor = Color.White)
        }
        Surface(
            modifier = Modifier.offset(y = 18.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary,
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (active) "$score" else "—", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(stringResource(R.string.diag_score), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.9f))
            }
        }
    }
    Spacer(Modifier.height(18.dp))
}

@Composable
private fun Meters(state: ProjectionState) {
    val s = state.stats
    val target = (s.bitrate.takeIf { it > 0 } ?: 4_000_000) / 1e6
    val stability = 1.0 - s.droppedFrames.toDouble() / (s.framesSent + s.droppedFrames).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MeterRow(
            label = stringResource(R.string.diag_meter_fps),
            value = String.format(Locale.ROOT, "%.0f fps", s.fps),
            fraction = (s.fps / 60.0).toFloat(),
            color = OmoThemeExtras.colors.good,
            icon = Icons.Rounded.Speed,
        )
        MeterRow(
            label = stringResource(R.string.diag_meter_bitrate),
            value = String.format(Locale.ROOT, "%.1f Mbps", s.mbps),
            fraction = (s.mbps / target).toFloat(),
            color = OmoThemeExtras.colors.moderate,
            icon = Icons.Rounded.Wifi,
        )
        MeterRow(
            label = stringResource(R.string.diag_meter_write),
            value = String.format(Locale.ROOT, "%.1f ms", s.writeMeanMs),
            fraction = (1.0 - s.writeMeanMs / 16.7).toFloat(),
            color = OmoThemeExtras.colors.fair,
            icon = Icons.Rounded.Timer,
        )
        MeterRow(
            label = stringResource(R.string.diag_meter_stability),
            value = String.format(Locale.ROOT, "%.0f%%", stability * 100),
            fraction = stability.toFloat(),
            color = OmoThemeExtras.colors.poor,
            icon = Icons.Rounded.Layers,
        )
    }
}

@Composable
private fun Details(state: ProjectionState) {
    val s = state.stats
    val link = state.link
    val rows = listOf(
        stringResource(R.string.diag_row_link) to when (link) {
            is WifiDirectLink.State.Connected -> "${link.peer.displayName} · ${link.hostAddress}"
            is WifiDirectLink.State.Connecting -> "→ ${link.peer.displayName}"
            is WifiDirectLink.State.Failed -> link.reason.name
            is WifiDirectLink.State.Searching -> "…"
            WifiDirectLink.State.Unavailable -> "unavailable"
            WifiDirectLink.State.Idle -> "—"
        },
        stringResource(R.string.diag_row_aa) to "${state.androidAuto.phase.name.lowercase()} ${state.androidAuto.detail}".trim(),
        stringResource(R.string.diag_row_display) to "${state.display.phase.name.lowercase()} ${state.display.detail}".trim(),
        stringResource(R.string.diag_row_encoder) to s.encoder.ifBlank { "—" },
        stringResource(R.string.diag_row_output) to s.outputSize.ifBlank { "—" },
        stringResource(R.string.diag_row_frames) to "${s.framesSent} / ${s.renderedFrames} / ${s.decodedFrames}",
        stringResource(R.string.diag_row_keyframes) to "${s.keyFrameRequests}",
        stringResource(R.string.diag_row_session) to "${s.sessionSeconds / 60}:${String.format(Locale.ROOT, "%02d", s.sessionSeconds % 60)}",
    )
    OmoCard(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.diag_details), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(10.dp))
        rows.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
                Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
