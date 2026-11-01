// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.logicsekai.omoauto.designsystem.theme.OmoPalette

/** Top bar: rounded back button, centred title, optional trailing action. */
@Composable
fun OmoTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    backDescription: String = "Back",
    action: @Composable () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp)) {
            if (onBack != null) OmoIconButton(Icons.AutoMirrored.Rounded.ArrowBack, backDescription, onBack)
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) { action() }
    }
}

enum class StepState { DONE, CURRENT, PENDING }

/** One setup step tile – orange when current, sky when done or pending. */
@Composable
fun StepCard(
    index: Int,
    title: String,
    state: StepState,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val current = state == StepState.CURRENT
    val container = if (current) MaterialTheme.colorScheme.primary else OmoPalette.Sky
    val content = if (current) Color.White else OmoPalette.Blue
    val shape = RoundedCornerShape(22.dp)
    val body: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(if (current) Color.White.copy(alpha = 0.25f) else Color.White),
                contentAlignment = Alignment.Center,
            ) {
                if (state == StepState.DONE) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = OmoPalette.Blue, modifier = Modifier.size(18.dp))
                } else {
                    Text("$index", style = MaterialTheme.typography.labelLarge, color = if (current) Color.White else OmoPalette.Blue)
                }
            }
            Spacer(Modifier.width(14.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = content, modifier = Modifier.weight(1f))
            if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelMedium, color = content)
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, color = container, shadowElevation = if (current) 6.dp else 0.dp) { body() }
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = container) { body() }
    }
}

/** Small coloured status pill. */
@Composable
fun StatusChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Title + body text block for info screens. */
@Composable
fun InfoBlock(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
