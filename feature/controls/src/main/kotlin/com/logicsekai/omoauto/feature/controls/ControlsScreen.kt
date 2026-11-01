// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.feature.controls

import android.content.Context
import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.view.Surface
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.logicsekai.omoauto.designsystem.component.LivePreview
import com.logicsekai.omoauto.designsystem.component.OmoSecondaryButton
import com.logicsekai.omoauto.designsystem.component.OmoTopBar
import com.logicsekai.omoauto.designsystem.component.RoundActionButton
import com.logicsekai.omoauto.designsystem.component.ScooterArt
import com.logicsekai.omoauto.designsystem.component.StatusChip
import com.logicsekai.omoauto.designsystem.theme.OmoThemeExtras
import com.logicsekai.omoauto.protocol.aa.AaKeyCode
import com.logicsekai.omoauto.streaming.AaPhase
import com.logicsekai.omoauto.streaming.OmoWebLink
import com.logicsekai.omoauto.streaming.MediaKeys
import com.logicsekai.omoauto.streaming.OmoWebLink.ScreenCommand
import com.logicsekai.omoauto.streaming.ProjectionEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ControlsViewModel(private val engine: ProjectionEngine) : ViewModel() {
    val state = engine.state
    val webActive = engine.webLink.active
    fun key(code: Int) = engine.sendKey(code)
    fun rotate(delta: Int) = engine.sendRotary(delta)
    fun media(keyCode: Int) = engine.mediaKeys.press(keyCode)
    fun screen(command: OmoWebLink.ScreenCommand) = engine.webLink.sendScreen(command)
    fun setPreviewSurface(surface: Surface, width: Int, height: Int) = engine.setPreviewSurface(surface, width, height)
    fun clearPreviewSurface(surface: Surface) = engine.clearPreviewSurface(surface)
    fun touchPreview(action: Int, x: Float, y: Float, width: Int, height: Int) = engine.touchPreview(action, x, y, width, height)
}

/** How the buttons are laid out. */
enum class ControlMode {
    /** Media player buttons (any playing app) plus OmoWeb's screen buttons. */
    MEDIA,

    /** TV-remote style D-pad: up, down, left, right around a central OK. */
    REMOTE,

    /**
     * Touch: the live picture fills the phone in landscape and works as Android Auto's touch
     * screen. A close button returns to the previous mode. Not remembered across visits.
     */
    TOUCH,
}

private const val PREFS = "omo_controls"
private const val KEY_MODE = "mode"

private fun loadMode(context: Context): ControlMode =
    runCatching { ControlMode.valueOf(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MODE, null)!!) }
        .getOrDefault(ControlMode.MEDIA)
        .let { if (it == ControlMode.TOUCH) ControlMode.MEDIA else it }

private fun saveMode(context: Context, mode: ControlMode) {
    if (mode == ControlMode.TOUCH) return
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_MODE, mode.name).apply()
}

/** Remote control for Android Auto – for use while parked. Shows the live picture on top. */
@Composable
fun ControlsRoute(viewModel: ControlsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val webActive by viewModel.webActive.collectAsStateWithLifecycle()
    val phase = state.androidAuto.phase
    val ready = phase == AaPhase.STREAMING || phase == AaPhase.CONNECTED
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf(loadMode(context)) }
    var lastMode by rememberSaveable { mutableStateOf(mode) }
    val haptics = LocalHapticFeedback.current
    val send: (() -> Unit) -> Unit = { action ->
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        action()
    }

    if (mode == ControlMode.TOUCH) {
        TouchScreen(
            showPicture = state.isAndroidAutoOn,
            touchEnabled = ready,
            viewModel = viewModel,
            onClose = { mode = lastMode },
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        OmoTopBar(title = stringResource(R.string.controls_title), onBack = onBack, backDescription = stringResource(R.string.controls_back))
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.isAndroidAutoOn) {
                LivePreview(
                    caption = stringResource(if (state.isStreamingToBike) R.string.controls_preview_on_bike else R.string.controls_preview_phone),
                    onSurfaceAvailable = viewModel::setPreviewSurface,
                    onSurfaceDestroyed = viewModel::clearPreviewSurface,
                    onTouch = if (ready) viewModel::touchPreview else null,
                )
            } else {
                Box(
                    Modifier.size(120.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    ScooterArt(Modifier.size(100.dp), bodyColor = MaterialTheme.colorScheme.primary, accentColor = MaterialTheme.colorScheme.surface)
                }
            }
            Spacer(Modifier.height(14.dp))
            StatusChip(
                text = stringResource(
                    when {
                        webActive -> R.string.controls_target_web
                        ready -> R.string.controls_ready
                        else -> R.string.controls_not_ready
                    },
                ),
                color = if (ready || webActive) OmoThemeExtras.colors.good else OmoThemeExtras.colors.muted,
            )
            Spacer(Modifier.height(14.dp))
            ModeSwitch(mode) {
                if (it != ControlMode.TOUCH) lastMode = it
                mode = it
                saveMode(context, it)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(
                    when (mode) {
                        ControlMode.MEDIA -> R.string.controls_hint_media
                        ControlMode.REMOTE -> R.string.controls_hint_remote
                        ControlMode.TOUCH -> R.string.controls_hint_media
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))

            when (mode) {
                ControlMode.MEDIA -> {
                    MediaPlayerControls(send, viewModel)
                    Spacer(Modifier.height(16.dp))
                    WebScreenControls(webActive, send, viewModel)
                }
                ControlMode.REMOTE -> RemotePad(ready, send, viewModel)
                ControlMode.TOUCH -> Unit
            }

            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                OmoSecondaryButton(
                    stringResource(R.string.controls_back_key),
                    { send { viewModel.key(AaKeyCode.BACK) } },
                    modifier = Modifier.weight(1f),
                    icon = Icons.AutoMirrored.Rounded.Undo,
                    enabled = ready,
                )
                OmoSecondaryButton(
                    stringResource(R.string.controls_home_key),
                    { send { viewModel.key(AaKeyCode.HOME) } },
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Home,
                    enabled = ready,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        Text(
            stringResource(R.string.controls_parked),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}

@Composable
private fun ModeSwitch(mode: ControlMode, onChange: (ControlMode) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
    ) {
        listOf(
            ControlMode.MEDIA to R.string.controls_mode_media,
            ControlMode.REMOTE to R.string.controls_mode_remote,
            ControlMode.TOUCH to R.string.controls_mode_touch,
        ).forEach { (m, label) ->
            val selected = m == mode
            Surface(
                onClick = { onChange(m) },
                shape = RoundedCornerShape(50),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/**
 * Media buttons for whatever app is playing on the phone (Spotify, YouTube Music, OmoWeb, ...),
 * sent as standard media keys.
 */
@Composable
private fun MediaPlayerControls(send: (() -> Unit) -> Unit, viewModel: ControlsViewModel) {
    ControlGroup(stringResource(R.string.controls_player_title), stringResource(R.string.controls_player_caption)) {
        fun key(code: Int): () -> Unit = { send { viewModel.media(code) } }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundActionButton(Icons.Rounded.SkipPrevious, stringResource(R.string.controls_player_previous), key(MediaKeys.PREVIOUS), size = 48.dp, primary = false)
            RoundActionButton(Icons.Rounded.FastRewind, stringResource(R.string.controls_player_rewind), key(MediaKeys.REWIND), size = 52.dp, primary = false)
            RoundActionButton(Icons.Rounded.PlayArrow, stringResource(R.string.controls_player_play_pause), key(MediaKeys.PLAY_PAUSE), size = 68.dp)
            RoundActionButton(Icons.Rounded.FastForward, stringResource(R.string.controls_player_forward), key(MediaKeys.FAST_FORWARD), size = 52.dp, primary = false)
            RoundActionButton(Icons.Rounded.SkipNext, stringResource(R.string.controls_player_next), key(MediaKeys.NEXT), size = 48.dp, primary = false)
        }
    }
}

/** Screen buttons only OmoWeb has; they work while OmoWeb is on the car screen. */
@Composable
private fun WebScreenControls(webActive: Boolean, send: (() -> Unit) -> Unit, viewModel: ControlsViewModel) {
    ControlGroup(
        stringResource(R.string.controls_web_title),
        if (webActive) null else stringResource(R.string.controls_web_inactive),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OmoSecondaryButton(
                stringResource(R.string.controls_web_fullscreen),
                { send { viewModel.screen(ScreenCommand.FULLSCREEN) } },
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Fullscreen,
                enabled = webActive,
            )
            OmoSecondaryButton(
                stringResource(R.string.controls_web_ultrawide),
                { send { viewModel.screen(ScreenCommand.ULTRAWIDE) } },
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.ZoomOutMap,
                enabled = webActive,
            )
        }
    }
}

@Composable
private fun ControlGroup(title: String, caption: String?, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        if (caption != null) {
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

/**
 * Touch mode: the live picture fills the phone in landscape (system bars hidden) and works as
 * Android Auto's touch screen, like the motorcycle display would. The close button (top right)
 * or Back returns to the previous control mode.
 */
@Composable
private fun TouchScreen(showPicture: Boolean, touchEnabled: Boolean, viewModel: ControlsViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val previousOrientation = activity?.requestedOrientation
        val insets = activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        insets?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            insets?.show(WindowInsetsCompat.Type.systemBars())
            if (previousOrientation != null) activity.requestedOrientation = previousOrientation
        }
    }
    BackHandler(onBack = onClose)

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        if (showPicture) {
            LivePreview(
                caption = null,
                onSurfaceAvailable = viewModel::setPreviewSurface,
                onSurfaceDestroyed = viewModel::clearPreviewSurface,
                onTouch = if (touchEnabled) viewModel::touchPreview else null,
                cornerRadius = 0.dp,
            )
        } else {
            Text(
                stringResource(R.string.controls_not_ready),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        }
        Surface(
            onClick = onClose,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.55f),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.controls_touch_close), modifier = Modifier.size(26.dp))
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** TV-remote style D-pad: arrows around a central OK. Holding an arrow repeats it. */
@Composable
private fun RemotePad(ready: Boolean, send: (() -> Unit) -> Unit, viewModel: ControlsViewModel) {
    Box(
        Modifier
            .size(264.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .alpha(if (ready) 1f else 0.45f),
    ) {
        PadArrow(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.controls_up), ready, Modifier.align(Alignment.TopCenter)) {
            // Same as the media mode's "previous" (rotary step back): Android Auto ignores D-pad up.
            send { viewModel.rotate(-1) }
        }
        PadArrow(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.controls_down), ready, Modifier.align(Alignment.BottomCenter)) {
            send { viewModel.rotate(1) }
        }
        PadArrow(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.controls_left), ready, Modifier.align(Alignment.CenterStart)) {
            send { viewModel.key(AaKeyCode.DPAD_LEFT) }
        }
        PadArrow(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.controls_right), ready, Modifier.align(Alignment.CenterEnd)) {
            send { viewModel.key(AaKeyCode.DPAD_RIGHT) }
        }
        Surface(
            onClick = { send { viewModel.key(AaKeyCode.DPAD_CENTER) } },
            enabled = ready,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = 4.dp,
            modifier = Modifier.align(Alignment.Center).size(104.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.controls_ok), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PadArrow(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    modifier: Modifier,
    size: Dp = 80.dp,
    onPress: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val press = rememberUpdatedState(onPress)
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .semantics {
                contentDescription = description
                role = Role.Button
                if (enabled) onClick { press.value(); true }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        press.value()
                        val repeat = scope.launch {
                            delay(450)
                            while (true) {
                                press.value()
                                delay(140)
                            }
                        }
                        tryAwaitRelease()
                        repeat.cancel()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
    }
}
