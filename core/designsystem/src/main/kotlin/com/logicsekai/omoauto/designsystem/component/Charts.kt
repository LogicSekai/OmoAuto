// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Vertical bar chart with rounded tracks (the "Activity & Results" widget).
 * [values] are 0..1; [topLabels] go above each bar, [labels] below.
 */
@Composable
fun BarChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    topLabels: List<String> = emptyList(),
    barColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.outline,
    height: Dp = 110.dp,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        values.forEachIndexed { index, value ->
            val animated by animateFloatAsState(value.coerceIn(0f, 1f), animationSpec = tween(500), label = "bar")
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text(
                    topLabels.getOrElse(index) { "" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.width(8.dp).height(height).clip(RoundedCornerShape(4.dp)).background(trackColor),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight(animated.coerceAtLeast(0.04f)).clip(RoundedCornerShape(4.dp)).background(barColor))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    labels.getOrElse(index) { "" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Thin tick sparkline (the "Learning streak" widget). The highest recent tick is highlighted. */
@Composable
fun TickSparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Canvas(modifier.fillMaxWidth().height(64.dp)) {
        if (values.isEmpty()) return@Canvas
        val gap = size.width / (values.size + 1)
        val highlight = values.indices.maxByOrNull { values[it] } ?: -1
        values.forEachIndexed { i, raw ->
            val v = raw.coerceIn(0.05f, 1f)
            val x = gap * (i + 1)
            val h = size.height * v
            val top = (size.height - h) / 2f
            drawLine(
                color = if (i == highlight) color else color.copy(alpha = 0.35f),
                start = Offset(x, top),
                end = Offset(x, top + h),
                strokeWidth = if (i == highlight) 3.dp.toPx() else 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Circular progress ring with centred label (the "50%" ring). */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    stroke: Dp = 5.dp,
    color: Color = MaterialTheme.colorScheme.secondary,
    label: String? = null,
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), animationSpec = tween(600), label = "ring")
    val track = MaterialTheme.colorScheme.outline
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            drawArc(track, 0f, 360f, useCenter = false, style = Stroke(s))
            drawArc(color, -90f, 360f * animated, useCenter = false, style = Stroke(s, cap = StrokeCap.Round))
        }
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Labelled horizontal meter with leading icon ("Good 40%" rows on the result screen). */
@Composable
fun MeterRow(
    label: String,
    value: String,
    fraction: Float,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), animationSpec = tween(500), label = "meter")
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Text(value, style = MaterialTheme.typography.labelLarge, color = color)
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.outline)) {
                Box(Modifier.fillMaxWidth(animated).height(5.dp).clip(RoundedCornerShape(3.dp)).background(color))
            }
        }
    }
}
