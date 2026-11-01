// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.logicsekai.omoauto.R
import com.logicsekai.omoauto.designsystem.component.ScooterArt
import com.logicsekai.omoauto.designsystem.theme.OmoPalette

private data class DrawerItem(val route: String, val icon: ImageVector, val label: Int)

private val DRAWER_ITEMS = listOf(
    DrawerItem(Routes.HOME, Icons.Rounded.Home, R.string.nav_home),
    DrawerItem(Routes.SETUP, Icons.Rounded.TaskAlt, R.string.nav_setup),
    DrawerItem(Routes.CONTROLS, Icons.Rounded.Gamepad, R.string.nav_controls),
    DrawerItem(Routes.DIAGNOSTICS, Icons.Rounded.Dashboard, R.string.nav_diagnostics),
    DrawerItem(Routes.SETTINGS, Icons.Rounded.Settings, R.string.nav_settings),
    DrawerItem(Routes.SUPPORT, Icons.Rounded.Favorite, R.string.nav_support),
    DrawerItem(Routes.ABOUT, Icons.Rounded.Info, R.string.nav_about),
)

/**
 * Orange full-screen menu; the current screen shrinks to the right as a rounded card
 * (with a faded "ghost" card behind it), like the design reference.
 */
@Composable
fun OmoDrawerScaffold(
    open: Boolean,
    selectedRoute: String,
    onClose: () -> Unit,
    onNavigate: (String) -> Unit,
    onStopAll: () -> Unit,
    content: @Composable () -> Unit,
) {
    val progress by animateFloatAsState(if (open) 1f else 0f, animationSpec = tween(380), label = "drawer")
    BackHandler(enabled = open, onBack = onClose)

    Box(Modifier.fillMaxSize().background(if (progress > 0f) OmoPalette.Orange else MaterialTheme.colorScheme.background)) {
        if (progress > 0f) {
            DrawerMenu(
                selectedRoute = selectedRoute,
                onClose = onClose,
                onNavigate = onNavigate,
                onStopAll = onStopAll,
                modifier = Modifier.alpha(progress),
            )
            // Ghost card
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = 1f - 0.34f * progress
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        translationX = size.width * 0.60f * progress
                        alpha = 0.35f * progress
                        shape = RoundedCornerShape(40.dp)
                        clip = true
                    }
                    .background(Color.White),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = 1f - 0.26f * progress
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    translationX = size.width * 0.70f * progress
                    shadowElevation = 32f * progress
                    shape = RoundedCornerShape((40 * progress).dp)
                    clip = progress > 0f
                },
        ) {
            content()
            if (open) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClose,
                        ),
                )
            }
        }
    }
}

@Composable
private fun DrawerMenu(
    selectedRoute: String,
    onClose: () -> Unit,
    onNavigate: (String) -> Unit,
    onStopAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxHeight().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Surface(
                onClick = onClose,
                modifier = Modifier.size(60.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.nav_close), tint = Color.White)
                }
            }
            Spacer(Modifier.weight(1f))
            DRAWER_ITEMS.forEach { item ->
                DrawerRow(item, selected = item.route == selectedRoute, onClick = { onNavigate(item.route) })
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.width(200.dp).height(1.dp).background(Color.White.copy(alpha = 0.3f)))
            Row(
                Modifier
                    .padding(vertical = 18.dp)
                    .clickable(onClick = onStopAll),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.nav_stop_all), style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
        }
        ScooterArt(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 64.dp).size(150.dp).alpha(0.95f),
            bodyColor = Color.White,
            accentColor = OmoPalette.Orange,
            screenColor = OmoPalette.Navy,
        )
    }
}

@Composable
private fun DrawerRow(item: DrawerItem, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth(0.62f)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .background(
                    if (selected) Color.White.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.08f),
                    RoundedCornerShape(20.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.icon, contentDescription = null, tint = Color.White)
        }
        Spacer(Modifier.width(18.dp))
        Text(
            stringResource(item.label),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = if (selected) 1f else 0.85f),
        )
    }
}
