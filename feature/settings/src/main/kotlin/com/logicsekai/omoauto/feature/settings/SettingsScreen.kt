// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.logicsekai.omoauto.data.settings.AppTheme
import com.logicsekai.omoauto.data.settings.FitMargins
import com.logicsekai.omoauto.data.settings.OmoSettings
import com.logicsekai.omoauto.data.settings.SavedBike
import com.logicsekai.omoauto.data.settings.SettingsRepository
import com.logicsekai.omoauto.designsystem.component.OmoCard
import com.logicsekai.omoauto.designsystem.component.OmoTopBar
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    val settings: StateFlow<OmoSettings> = repository.settings

    private fun edit(block: suspend SettingsRepository.() -> Unit) {
        viewModelScope.launch { repository.block() }
    }

    fun setBitrate(value: Int) = edit { setBitrate(value) }
    fun setOutputWidth(value: Int) = edit { setOutputWidth(value) }
    fun setSourceWidth(value: Int) = edit { setSourceWidth(value) }
    fun setRightMargin(value: Int) = edit { setRightMargin(value) }
    fun setVerticalMargin(value: Int) = edit { setVerticalMargin(value) }
    fun setCustomFit(value: Boolean) = edit { setCustomFit(value) }
    fun setCustomMargins(value: FitMargins) = edit { setCustomMargins(value) }
    fun setBoundedQueue(value: Boolean) = edit { setBoundedLiveQueue(value) }
    fun setDisplayNight(value: Boolean) = edit { setDisplayNightTheme(value) }
    fun setAaNight(value: Boolean) = edit { setAndroidAutoNightMode(value) }
    fun setAutoReconnect(value: Boolean) = edit { setAutoReconnect(value) }
    fun setTheme(value: AppTheme) = edit { setAppTheme(value) }
    fun renameBike(address: String, name: String) = edit { renameBike(address, name) }
    fun forgetBike(address: String) = edit { forgetBike(address) }
}

@Composable
fun SettingsRoute(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<SavedBike?>(null) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        OmoTopBar(title = stringResource(R.string.settings_title), onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.settings_apply_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Section(stringResource(R.string.settings_stream)) {
                ChoiceRow(
                    title = stringResource(R.string.settings_bitrate),
                    caption = stringResource(R.string.settings_bitrate_caption),
                    options = OmoSettings.BITRATE_OPTIONS,
                    selected = s.bitrateBps,
                    label = { "${it / 1_000_000} Mbps" },
                    onSelect = viewModel::setBitrate,
                )
                ChoiceRow(
                    title = stringResource(R.string.settings_output),
                    caption = stringResource(R.string.settings_output_caption),
                    options = OmoSettings.OUTPUT_WIDTH_OPTIONS,
                    selected = s.outputWidth,
                    label = { "$it×${it * 3 / 8}" },
                    onSelect = viewModel::setOutputWidth,
                )
                ChoiceRow(
                    title = stringResource(R.string.settings_source),
                    caption = stringResource(R.string.settings_source_caption),
                    options = OmoSettings.SOURCE_WIDTH_OPTIONS,
                    selected = s.sourceWidth,
                    label = { "${it * 9 / 16}p" },
                    onSelect = viewModel::setSourceWidth,
                )
                ToggleRow(stringResource(R.string.settings_low_latency), stringResource(R.string.settings_low_latency_caption), s.boundedLiveQueue, viewModel::setBoundedQueue)
            }

            Section(stringResource(R.string.settings_fit)) {
                ToggleRow(stringResource(R.string.settings_custom_fit), stringResource(R.string.settings_custom_fit_caption), s.customFit, viewModel::setCustomFit)
                if (s.customFit) {
                    CustomMarginsEditor(s.customMargins, viewModel::setCustomMargins)
                } else {
                ChoiceRow(
                    title = stringResource(R.string.settings_right_margin),
                    caption = stringResource(R.string.settings_right_margin_caption),
                    options = OmoSettings.RIGHT_MARGIN_OPTIONS,
                    selected = s.rightMarginPx,
                    label = { "$it px" },
                    onSelect = viewModel::setRightMargin,
                )
                ChoiceRow(
                    title = stringResource(R.string.settings_vertical_margin),
                    caption = stringResource(R.string.settings_vertical_margin_caption),
                    options = OmoSettings.VERTICAL_MARGIN_OPTIONS,
                    selected = s.verticalMarginPx,
                    label = { "$it px" },
                    onSelect = viewModel::setVerticalMargin,
                )
                }
            }

            Section(stringResource(R.string.settings_appearance)) {
                ToggleRow(stringResource(R.string.settings_display_night), stringResource(R.string.settings_display_night_caption), s.displayNightTheme, viewModel::setDisplayNight)
                ToggleRow(stringResource(R.string.settings_aa_night), stringResource(R.string.settings_aa_night_caption), s.androidAutoNightMode, viewModel::setAaNight)
                ChoiceRow(
                    title = stringResource(R.string.settings_app_theme),
                    caption = null,
                    options = AppTheme.entries.toList(),
                    selected = s.appTheme,
                    label = {
                        when (it) {
                            AppTheme.SYSTEM -> stringResource(R.string.settings_theme_system)
                            AppTheme.LIGHT -> stringResource(R.string.settings_theme_light)
                            AppTheme.DARK -> stringResource(R.string.settings_theme_dark)
                        }
                    },
                    onSelect = viewModel::setTheme,
                )
            }

            Section(stringResource(R.string.settings_connection)) {
                ToggleRow(stringResource(R.string.settings_auto_reconnect), stringResource(R.string.settings_auto_reconnect_caption), s.autoReconnect, viewModel::setAutoReconnect)
                if (s.savedBikes.isEmpty()) {
                    Text(stringResource(R.string.settings_no_bikes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                s.savedBikes.forEach { bike ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(bike.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                            Text(bike.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { renaming = bike }) {
                            Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.settings_rename))
                        }
                        IconButton(onClick = { viewModel.forgetBike(bike.address) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.settings_forget))
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    renaming?.let { bike ->
        var name by remember(bike) { mutableStateOf(bike.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.settings_rename)) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) viewModel.renameBike(bike.address, name.trim())
                    renaming = null
                }) { Text(stringResource(R.string.settings_save)) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
    OmoCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) { content() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(
    title: String,
    caption: String?,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        if (caption != null) {
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(label(option)) },
                    shape = RoundedCornerShape(14.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, caption: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * Four number fields for the free edges (960 × 360 units). Saved with the button so a half-typed
 * value never reaches the stream; values are clamped so enough picture is left.
 */
@Composable
private fun CustomMarginsEditor(current: FitMargins, onSave: (FitMargins) -> Unit) {
    var left by remember(current) { mutableStateOf(current.left.toString()) }
    var top by remember(current) { mutableStateOf(current.top.toString()) }
    var right by remember(current) { mutableStateOf(current.right.toString()) }
    var bottom by remember(current) { mutableStateOf(current.bottom.toString()) }
    val typed = FitMargins(
        left.toIntOrNull() ?: -1,
        top.toIntOrNull() ?: -1,
        right.toIntOrNull() ?: -1,
        bottom.toIntOrNull() ?: -1,
    )
    val valid = typed.isValid
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.settings_custom_fit_units, FitMargins.MAX_HORIZONTAL, FitMargins.MAX_VERTICAL),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EdgeField(stringResource(R.string.settings_edge_left), left, { left = it }, Modifier.weight(1f))
            EdgeField(stringResource(R.string.settings_edge_right), right, { right = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EdgeField(stringResource(R.string.settings_edge_top), top, { top = it }, Modifier.weight(1f))
            EdgeField(stringResource(R.string.settings_edge_bottom), bottom, { bottom = it }, Modifier.weight(1f))
        }
        if (!valid) {
            Text(stringResource(R.string.settings_custom_fit_invalid), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        } else {
            val w = FitMargins.UNIT_WIDTH - typed.left - typed.right
            val h = FitMargins.UNIT_HEIGHT - typed.top - typed.bottom
            Text(
                stringResource(R.string.settings_custom_fit_result, w, h),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onSave(FitMargins.DEFAULT) }) { Text(stringResource(R.string.settings_custom_fit_reset)) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onSave(typed) }, enabled = valid && typed != current) {
                Text(stringResource(R.string.settings_save))
            }
        }
    }
}

@Composable
private fun EdgeField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onChange(text.filter(Char::isDigit).take(3)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
