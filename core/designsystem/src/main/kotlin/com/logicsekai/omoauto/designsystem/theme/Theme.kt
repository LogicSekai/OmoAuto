// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = OmoPalette.Orange,
    onPrimary = Color.White,
    primaryContainer = OmoPalette.OrangeSoft,
    onPrimaryContainer = OmoPalette.OrangeDeep,
    secondary = OmoPalette.Blue,
    onSecondary = Color.White,
    secondaryContainer = OmoPalette.Sky,
    onSecondaryContainer = OmoPalette.Blue,
    tertiary = OmoPalette.Navy,
    onTertiary = Color.White,
    background = OmoPalette.Paper,
    onBackground = OmoPalette.Ink,
    surface = OmoPalette.Paper,
    onSurface = OmoPalette.Ink,
    surfaceVariant = OmoPalette.Cloud,
    onSurfaceVariant = OmoPalette.InkMuted,
    outline = OmoPalette.Line,
    outlineVariant = OmoPalette.Line,
    error = OmoPalette.Poor,
)

private val DarkColors = darkColorScheme(
    primary = OmoPalette.Orange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A2A22),
    onPrimaryContainer = OmoPalette.OrangeSoft,
    secondary = OmoPalette.SkyBright,
    onSecondary = OmoPalette.NavyDeep,
    secondaryContainer = Color(0xFF1E3F66),
    onSecondaryContainer = OmoPalette.Sky,
    tertiary = OmoPalette.Sky,
    onTertiary = OmoPalette.NavyDeep,
    background = OmoPalette.Night,
    onBackground = OmoPalette.NightInk,
    surface = OmoPalette.NightSurface,
    onSurface = OmoPalette.NightInk,
    surfaceVariant = OmoPalette.NightCard,
    onSurfaceVariant = OmoPalette.NightInkMuted,
    outline = OmoPalette.NightLine,
    outlineVariant = OmoPalette.NightLine,
    error = OmoPalette.Poor,
)

/** Extra semantic colours that Material's scheme does not cover. */
@Immutable
data class OmoExtraColors(
    val cardPink: Color,
    val cardSky: Color,
    val cardLilac: Color,
    val good: Color,
    val moderate: Color,
    val fair: Color,
    val poor: Color,
    val muted: Color,
)

private val LightExtras = OmoExtraColors(
    cardPink = OmoPalette.Pink,
    cardSky = OmoPalette.SkyBright,
    cardLilac = OmoPalette.Lilac,
    good = OmoPalette.Good,
    moderate = OmoPalette.Moderate,
    fair = OmoPalette.Fair,
    poor = OmoPalette.Poor,
    muted = OmoPalette.InkMuted,
)

private val DarkExtras = LightExtras.copy(muted = OmoPalette.NightInkMuted)

val LocalOmoColors = staticCompositionLocalOf { LightExtras }

val OmoShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun OmoTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalOmoColors provides if (darkTheme) DarkExtras else LightExtras) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = OmoTypography,
            shapes = OmoShapes,
            content = content,
        )
    }
}

/** Shortcut: `OmoTheme.colors.good` etc. */
object OmoThemeExtras {
    val colors: OmoExtraColors
        @Composable get() = LocalOmoColors.current
}
