// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.feature.support

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
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
import com.logicsekai.omoauto.designsystem.component.OmoSecondaryButton
import com.logicsekai.omoauto.designsystem.component.OmoTopBar
import com.logicsekai.omoauto.designsystem.theme.OmoPalette

/**
 * Links the app module provides. A blank URL hides its row (Google Play builds hide the
 * donation links, which Play's payments policy does not allow outside Play billing).
 */
data class SupportInfo(
    val sponsorUrl: String,
    val kofiUrl: String,
    val trakteerUrl: String,
    val sourceUrl: String,
    val communityUrl: String,
)

@Composable
fun SupportRoute(info: SupportInfo, onBack: () -> Unit) {
    val context = LocalContext.current
    fun open(url: String) = AndroidAutoHelper.openUrl(context, url)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        OmoTopBar(title = stringResource(R.string.support_title), onBack = onBack)
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
                    Icon(Icons.Rounded.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.support_hero_title), style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.support_hero_body), style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }
            }

            val donations = listOf(
                Triple(R.string.support_sponsor, R.string.support_sponsor_caption, info.sponsorUrl),
                Triple(R.string.support_kofi, R.string.support_kofi_caption, info.kofiUrl),
                Triple(R.string.support_trakteer, R.string.support_trakteer_caption, info.trakteerUrl),
            ).filter { it.third.isNotBlank() }
            if (donations.isNotEmpty()) {
                Section(stringResource(R.string.support_section_donate))
                donations.forEach { (title, caption, url) ->
                    AccentRow(text = stringResource(title), caption = stringResource(caption), onClick = { open(url) }, accent = OmoPalette.Pink)
                }
            }

            if (info.sourceUrl.isNotBlank()) {
                Section(stringResource(R.string.support_section_contribute))
                AccentRow(
                    text = stringResource(R.string.support_code),
                    caption = stringResource(R.string.support_code_caption),
                    onClick = { open(info.sourceUrl) },
                    accent = OmoPalette.Blue,
                )
                AccentRow(
                    text = stringResource(R.string.support_issue),
                    caption = stringResource(R.string.support_issue_caption),
                    onClick = { open("${info.sourceUrl}/issues/new/choose") },
                    accent = OmoPalette.Blue,
                )
                AccentRow(
                    text = stringResource(R.string.support_guide),
                    caption = stringResource(R.string.support_guide_caption),
                    onClick = { open("${info.sourceUrl}/blob/main/CONTRIBUTING.md") },
                    accent = OmoPalette.Blue,
                )
            }
            if (info.communityUrl.isNotBlank()) {
                AccentRow(
                    text = stringResource(R.string.support_community),
                    caption = info.communityUrl.removePrefix("https://"),
                    onClick = { open(info.communityUrl) },
                )
            }

            Spacer(Modifier.height(4.dp))
            val shareText = stringResource(R.string.support_share_text, info.sourceUrl.ifBlank { info.communityUrl })
            OmoSecondaryButton(
                text = stringResource(R.string.support_share),
                onClick = { share(context, shareText) },
                icon = Icons.Rounded.Share,
            )
            Text(
                stringResource(R.string.support_thanks),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 6.dp),
    )
}

private fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    try {
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // No app can share text; nothing to do.
    }
}
