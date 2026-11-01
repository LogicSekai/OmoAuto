// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SportsMotorsports
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.logicsekai.omoauto.data.settings.SettingsRepository
import com.logicsekai.omoauto.designsystem.component.OmoPrimaryButton
import com.logicsekai.omoauto.designsystem.component.ScooterArt
import com.logicsekai.omoauto.designsystem.theme.OmoPalette
import kotlinx.coroutines.launch

class OnboardingViewModel(private val settings: SettingsRepository) : ViewModel() {
    fun accept(onDone: () -> Unit) {
        viewModelScope.launch {
            settings.setDisclaimerAccepted(true)
            settings.setOnboardingDone(true)
            onDone()
        }
    }
}

/** Welcome → "Before you ride" disclaimer. Calls [onFinished] once accepted. */
@Composable
fun OnboardingRoute(viewModel: OnboardingViewModel, onFinished: () -> Unit) {
    var step by rememberSaveable { mutableStateOf(0) }
    AnimatedContent(targetState = step, label = "onboarding") { current ->
        when (current) {
            0 -> WelcomeScreen(onContinue = { step = 1 })
            else -> DisclaimerScreen(onAccept = { viewModel.accept(onFinished) })
        }
    }
}

@Composable
private fun WelcomeScreen(onContinue: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(36.dp))
                .background(OmoPalette.Orange),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(260.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)))
            ScooterArt(Modifier.size(240.dp), bodyColor = Color.White, accentColor = OmoPalette.Orange)
        }
        Spacer(Modifier.height(28.dp))
        Text(
            stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.onboarding_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.onboarding_badges),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        OmoPrimaryButton(stringResource(R.string.onboarding_continue), onContinue)
    }
}

@Composable
private fun DisclaimerScreen(onAccept: () -> Unit) {
    var agreed by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.disclaimer_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(R.string.disclaimer_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            DisclaimerItem(Icons.Rounded.Groups, R.string.disclaimer_independent_title, R.string.disclaimer_independent_body)
            DisclaimerItem(Icons.Rounded.WarningAmber, R.string.disclaimer_warranty_title, R.string.disclaimer_warranty_body)
            DisclaimerItem(Icons.Rounded.SportsMotorsports, R.string.disclaimer_safety_title, R.string.disclaimer_safety_body)
            DisclaimerItem(Icons.Rounded.Gavel, R.string.disclaimer_responsibility_title, R.string.disclaimer_responsibility_body)
            DisclaimerItem(Icons.Rounded.Lock, R.string.disclaimer_privacy_title, R.string.disclaimer_privacy_body)
        }
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = agreed, onCheckedChange = { agreed = it })
                Text(
                    stringResource(R.string.disclaimer_agree),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(Modifier.height(12.dp))
            OmoPrimaryButton(stringResource(R.string.disclaimer_accept), onAccept, enabled = agreed)
        }
    }
}

@Composable
private fun DisclaimerItem(icon: ImageVector, title: Int, body: Int) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(stringResource(title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}
