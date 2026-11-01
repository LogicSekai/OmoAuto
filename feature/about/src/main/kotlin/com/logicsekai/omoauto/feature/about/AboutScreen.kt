// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.feature.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.logicsekai.omoauto.data.phone.AndroidAutoHelper
import com.logicsekai.omoauto.designsystem.component.AccentRow
import com.logicsekai.omoauto.designsystem.component.OmoCard
import com.logicsekai.omoauto.designsystem.component.OmoTopBar
import com.logicsekai.omoauto.designsystem.component.ScooterArt
import com.logicsekai.omoauto.designsystem.theme.OmoPalette

/** Static facts the app module provides (version, links). Empty URLs hide their row. */
data class AboutInfo(
    val versionName: String,
    val versionCode: Int,
    val buildType: String,
    val communityUrl: String,
    val sourceUrl: String,
)

private data class Notice(val name: String, val license: String, val note: String)

private val NOTICES = listOf(
    Notice("Kotlin & kotlinx.coroutines", "Apache-2.0", "JetBrains"),
    Notice("AndroidX, Jetpack Compose, Material 3", "Apache-2.0", "The Android Open Source Project"),
    Notice("Material Icons", "Apache-2.0", "Google"),
    Notice("Poppins", "SIL Open Font License 1.1", "The Poppins Project Authors"),
    Notice(
        "Open Headunit sample keys",
        "AGPL-3.0",
        "andreknieriem/open-headunit, based on Headunit by Michael Reid (mikereidis). Downloaded on demand, not bundled",
    ),
)

@Composable
fun AboutRoute(info: AboutInfo, onBack: () -> Unit, onOpenSupport: () -> Unit, onOpenUpdate: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        OmoTopBar(title = stringResource(R.string.about_title), onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(shape = RoundedCornerShape(30.dp), color = OmoPalette.Orange, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                        ScooterArt(Modifier.size(140.dp), bodyColor = Color.White, accentColor = OmoPalette.Orange)
                    }
                    Text("OmoAuto", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Text(
                        stringResource(R.string.about_version, info.versionName, info.versionCode, info.buildType),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.about_tagline), style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }
            }

            if (info.communityUrl.isNotBlank()) {
                AccentRow(
                    text = stringResource(R.string.about_community),
                    caption = info.communityUrl.removePrefix("https://"),
                    onClick = { AndroidAutoHelper.openUrl(context, info.communityUrl) },
                )
            }
            if (info.sourceUrl.isNotBlank()) {
                AccentRow(
                    text = stringResource(R.string.about_source),
                    caption = info.sourceUrl.removePrefix("https://"),
                    onClick = { AndroidAutoHelper.openUrl(context, info.sourceUrl) },
                    accent = OmoPalette.Blue,
                )
            }
            AccentRow(
                text = stringResource(R.string.about_support),
                caption = stringResource(R.string.about_support_caption),
                onClick = onOpenSupport,
                accent = OmoPalette.Pink,
            )
            AccentRow(
                text = stringResource(R.string.about_update),
                caption = stringResource(R.string.about_update_caption),
                onClick = onOpenUpdate,
            )

            OmoCard(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.about_disclaimer_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.about_disclaimer_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            OmoCard(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.about_license_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.about_license_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            OmoCard(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.about_notices), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.about_notices_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                NOTICES.forEach { notice ->
                    Spacer(Modifier.height(12.dp))
                    Text(notice.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text("${notice.license} · ${notice.note}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
