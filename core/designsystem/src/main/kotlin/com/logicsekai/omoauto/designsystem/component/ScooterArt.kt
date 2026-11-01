// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * OmoAuto's own scooter illustration (drawn in code, no external artwork).
 * A friendly electric scooter with a glowing dashboard screen.
 */
@Composable
fun ScooterArt(
    modifier: Modifier = Modifier,
    bodyColor: Color = Color.White,
    accentColor: Color = Color(0xFFFF7A4F),
    screenColor: Color = Color(0xFF3C467D),
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val wheelR = w * 0.15f
        val wheelY = h * 0.78f
        val rearX = w * 0.24f
        val frontX = w * 0.78f
        val stroke = w * 0.035f

        // Shadow
        drawOval(Color.Black.copy(alpha = 0.10f), topLeft = Offset(w * 0.08f, h * 0.9f), size = Size(w * 0.84f, h * 0.06f))

        // Body: deck + rear cowl
        val body = Path().apply {
            moveTo(w * 0.10f, h * 0.60f)
            quadraticTo(w * 0.12f, h * 0.42f, w * 0.34f, h * 0.44f)
            lineTo(w * 0.46f, h * 0.46f)
            lineTo(w * 0.52f, h * 0.64f)
            lineTo(w * 0.70f, h * 0.64f)
            quadraticTo(w * 0.74f, h * 0.70f, w * 0.66f, h * 0.72f)
            lineTo(w * 0.16f, h * 0.72f)
            quadraticTo(w * 0.08f, h * 0.70f, w * 0.10f, h * 0.60f)
            close()
        }
        drawPath(body, bodyColor)

        // Seat
        drawRoundRect(
            color = screenColor,
            topLeft = Offset(w * 0.16f, h * 0.37f),
            size = Size(w * 0.28f, h * 0.07f),
            cornerRadius = CornerRadius(w * 0.04f),
        )

        // Steering column
        drawLine(bodyColor, Offset(w * 0.60f, h * 0.66f), Offset(w * 0.72f, h * 0.24f), strokeWidth = stroke * 1.6f, cap = StrokeCap.Round)
        drawLine(bodyColor, Offset(w * 0.72f, h * 0.24f), Offset(frontX, wheelY), strokeWidth = stroke * 1.2f, cap = StrokeCap.Round)
        // Handlebar
        drawLine(bodyColor, Offset(w * 0.62f, h * 0.20f), Offset(w * 0.84f, h * 0.22f), strokeWidth = stroke * 1.3f, cap = StrokeCap.Round)

        // Dashboard screen (the display OmoAuto projects onto)
        drawRoundRect(
            color = screenColor,
            topLeft = Offset(w * 0.63f, h * 0.27f),
            size = Size(w * 0.17f, h * 0.10f),
            cornerRadius = CornerRadius(w * 0.025f),
        )
        drawRoundRect(
            color = accentColor,
            topLeft = Offset(w * 0.655f, h * 0.295f),
            size = Size(w * 0.08f, h * 0.05f),
            cornerRadius = CornerRadius(w * 0.015f),
        )

        // Headlight
        drawCircle(Color(0xFFFFE08A), radius = w * 0.03f, center = Offset(w * 0.80f, h * 0.36f))

        // Wheels
        listOf(rearX, frontX).forEach { x ->
            drawCircle(screenColor, radius = wheelR, center = Offset(x, wheelY))
            drawCircle(bodyColor, radius = wheelR * 0.45f, center = Offset(x, wheelY))
            drawCircle(accentColor, radius = wheelR * 0.18f, center = Offset(x, wheelY))
        }

        // Speed lines
        val lineColor = bodyColor.copy(alpha = 0.6f)
        drawLine(lineColor, Offset(w * 0.0f, h * 0.50f), Offset(w * 0.07f, h * 0.50f), strokeWidth = stroke * 0.7f, cap = StrokeCap.Round)
        drawLine(lineColor, Offset(w * 0.02f, h * 0.58f), Offset(w * 0.07f, h * 0.58f), strokeWidth = stroke * 0.7f, cap = StrokeCap.Round)

        // Wi-Fi arcs above the screen
        val arcCenter = Offset(w * 0.715f, h * 0.15f)
        for (i in 1..2) {
            val r = w * 0.035f * i
            drawArc(
                color = bodyColor,
                startAngle = 220f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = Offset(arcCenter.x - r, arcCenter.y - r),
                size = Size(r * 2, r * 2),
                style = Stroke(width = stroke * 0.7f, cap = StrokeCap.Round),
            )
        }
    }
}
