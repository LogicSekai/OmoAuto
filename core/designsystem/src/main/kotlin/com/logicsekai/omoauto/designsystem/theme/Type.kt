// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.logicsekai.omoauto.designsystem.R

/**
 * Poppins (SIL OFL 1.1) – the reference pairs it with Corsa Grotesk, a commercial face that
 * cannot be bundled; Poppins covers both roles.
 */
val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

private fun style(size: Int, weight: FontWeight, line: Int, spacing: Double = 0.0) = TextStyle(
    fontFamily = Poppins,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing.sp,
)

val OmoTypography = Typography(
    displayLarge = style(40, FontWeight.SemiBold, 48),
    displayMedium = style(34, FontWeight.SemiBold, 42),
    displaySmall = style(28, FontWeight.SemiBold, 36),
    headlineLarge = style(26, FontWeight.SemiBold, 34),
    headlineMedium = style(22, FontWeight.SemiBold, 30),
    headlineSmall = style(20, FontWeight.SemiBold, 28),
    titleLarge = style(18, FontWeight.SemiBold, 26),
    titleMedium = style(16, FontWeight.SemiBold, 24),
    titleSmall = style(14, FontWeight.SemiBold, 20),
    bodyLarge = style(16, FontWeight.Normal, 24),
    bodyMedium = style(14, FontWeight.Normal, 21),
    bodySmall = style(12, FontWeight.Normal, 18),
    labelLarge = style(14, FontWeight.Medium, 20),
    labelMedium = style(12, FontWeight.Medium, 16),
    labelSmall = style(11, FontWeight.Medium, 14, 0.2),
)
