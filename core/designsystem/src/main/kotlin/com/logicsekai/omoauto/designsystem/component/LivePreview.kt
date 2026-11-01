// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.designsystem.component

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import com.logicsekai.omoauto.protocol.aa.AaTouchAction
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign

/**
 * Live picture of what is (or would be) sent to the motorcycle, in the display's 8:3 aspect.
 * Used on Home and Controls. [caption] (status such as "Preview on this phone") is shown below
 * the picture, never on top of it; null hides it. [cornerRadius] 0 gives square corners. [onSurfaceAvailable] fires on creation and resize,
 * [onSurfaceDestroyed] with the same surface when the view goes away.
 */
@Composable
fun LivePreview(
    caption: String?,
    onSurfaceAvailable: (Surface, Int, Int) -> Unit,
    onSurfaceDestroyed: (Surface) -> Unit,
    modifier: Modifier = Modifier,
    /** Touches on the preview (AaTouchAction, x, y, view width, view height); null disables touch. */
    onTouch: ((action: Int, x: Float, y: Float, width: Int, height: Int) -> Unit)? = null,
    cornerRadius: Dp = 22.dp,
) {
    val available = rememberUpdatedState(onSurfaceAvailable)
    val destroyed = rememberUpdatedState(onSurfaceDestroyed)
    val touch = rememberUpdatedState(onTouch)
    Column(modifier) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(8f / 3f)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color.Black),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                TextureView(context).apply {
                    isOpaque = true
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        private var surface: Surface? = null

                        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                            surface = Surface(texture).also { available.value(it, width, height) }
                        }

                        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                            surface?.let { available.value(it, width, height) }
                        }

                        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                            surface?.let { destroyed.value(it) }
                            surface?.release()
                            surface = null
                            return true
                        }

                        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                    }
                }
            },
        )
        // Touch layer on top of the TextureView: the preview works as a touchpad for Android Auto.
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val w = size.width
                        val h = size.height
                        touch.value?.invoke(AaTouchAction.DOWN, down.position.x, down.position.y, w, h)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.pressed) {
                                if (change.positionChanged()) {
                                    touch.value?.invoke(AaTouchAction.MOVE, change.position.x, change.position.y, w, h)
                                }
                                change.consume()
                            } else {
                                touch.value?.invoke(AaTouchAction.UP, change.position.x, change.position.y, w, h)
                                change.consume()
                                break
                            }
                        }
                    }
                },
        )
    }
    if (caption != null) {
        Text(
            caption,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
    }
}
