// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.streaming.video

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.Surface
import com.logicsekai.omoauto.common.OmoLog
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Takes the decoded Android Auto picture (via a SurfaceTexture), crops the UI area and draws it
 * into the encoder's input surface at a steady 60 fps. When the source pauses, the latest image is
 * repeated so the display keeps receiving a regular stream. An optional preview surface (phone
 * screen) receives the same composition, but only when Android Auto sent a new picture and at most
 * [PREVIEW_FPS] times a second, without waiting for the phone's vsync: the preview costs little
 * GPU/battery and can never slow down the stream to the motorcycle.
 */
class GlCompositor(private val geometry: VideoGeometry, private val fps: Int = 60) {

    private val thread = HandlerThread("omo-gl").apply { start() }
    private val handler = Handler(thread.looper)

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var config: EGLConfig? = null
    private var pbuffer: EGLSurface = EGL14.EGL_NO_SURFACE
    private var encoderEgl: EGLSurface = EGL14.EGL_NO_SURFACE
    private var previewEgl: EGLSurface = EGL14.EGL_NO_SURFACE
    private var previewSurface: Surface? = null
    private var previewWidth = 0
    private var previewHeight = 0
    private var previewDirty = false
    private var lastPreviewNanos = 0L

    private var program = 0
    private var texture = 0
    private var aPosition = 0
    private var aTexCoord = 0
    private var uTexMatrix = 0
    private lateinit var surfaceTexture: SurfaceTexture
    private val texMatrix = FloatArray(16)
    private lateinit var vertices: FloatBuffer

    @Volatile private var frameAvailable = false
    @Volatile private var hasImage = false
    @Volatile private var running = false
    private var startNanos = 0L
    private var tick = 0L

    /** Surface the decoder renders into. Valid after [start]. */
    lateinit var inputSurface: Surface
        private set

    @Volatile var sourceFrames = 0L
        private set
    @Volatile var renderedFrames = 0L
        private set

    /** Creates the GL context and input surface; [encoderSurface] may be null (preview only). */
    fun start(encoderSurface: Surface?) {
        val ready = CountDownLatch(1)
        var failure: Throwable? = null
        handler.post {
            try {
                setUpEgl()
                encoderSurface?.let { encoderEgl = createWindowSurface(it) }
                makeCurrent(if (encoderEgl != EGL14.EGL_NO_SURFACE) encoderEgl else pbuffer)
                setUpGl()
                running = true
                startNanos = System.nanoTime()
                tick = 0
                scheduleNext()
            } catch (t: Throwable) {
                failure = t
            } finally {
                ready.countDown()
            }
        }
        if (!ready.await(5, TimeUnit.SECONDS)) throw IllegalStateException("GL setup timed out")
        failure?.let { throw IllegalStateException("GL setup failed: ${it.message}", it) }
    }

    /**
     * Attaches (or with null detaches) the phone preview. Resizing the same surface only updates
     * the size. Detaching waits briefly for the GL thread so the caller may release the surface.
     */
    fun setPreviewSurface(surface: Surface?, width: Int, height: Int) {
        val done = CountDownLatch(1)
        handler.post {
            try {
                if (surface != null && surface === previewSurface && previewEgl != EGL14.EGL_NO_SURFACE) {
                    previewWidth = width
                    previewHeight = height
                    previewDirty = true
                    return@post
                }
                if (previewEgl != EGL14.EGL_NO_SURFACE) {
                    if (display != EGL14.EGL_NO_DISPLAY) {
                        EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)
                        EGL14.eglDestroySurface(display, previewEgl)
                    }
                    previewEgl = EGL14.EGL_NO_SURFACE
                }
                previewSurface = null
                if (surface != null && surface.isValid && display != EGL14.EGL_NO_DISPLAY) {
                    previewEgl = try {
                        createWindowSurface(surface).also {
                            makeCurrent(it)
                            // Never block the render loop on the phone's display refresh.
                            EGL14.eglSwapInterval(display, 0)
                        }
                    } catch (e: Exception) {
                        OmoLog.w(TAG, "Preview surface rejected", e)
                        EGL14.EGL_NO_SURFACE
                    }
                    if (previewEgl != EGL14.EGL_NO_SURFACE) previewSurface = surface
                    previewWidth = width
                    previewHeight = height
                    previewDirty = true
                }
            } finally {
                done.countDown()
            }
        }
        if (surface == null) done.await(500, TimeUnit.MILLISECONDS)
    }

    fun release() {
        running = false
        val done = CountDownLatch(1)
        handler.post {
            try {
                if (::surfaceTexture.isInitialized) surfaceTexture.release()
                if (::inputSurface.isInitialized) inputSurface.release()
                if (program != 0) GLES20.glDeleteProgram(program)
                if (texture != 0) GLES20.glDeleteTextures(1, intArrayOf(texture), 0)
                if (display != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                    listOf(encoderEgl, previewEgl, pbuffer).forEach {
                        if (it != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, it)
                    }
                    EGL14.eglDestroyContext(display, context)
                    EGL14.eglReleaseThread()
                    EGL14.eglTerminate(display)
                }
            } catch (t: Throwable) {
                OmoLog.w(TAG, "GL release failed", t)
            } finally {
                display = EGL14.EGL_NO_DISPLAY
                done.countDown()
            }
        }
        done.await(2, TimeUnit.SECONDS)
        thread.quitSafely()
    }

    // ---------------------------------------------------------------------------------------------

    private fun scheduleNext() {
        if (!running) return
        tick++
        val targetNanos = startNanos + tick * 1_000_000_000L / fps
        val delayMs = ((targetNanos - System.nanoTime()) / 1_000_000L).coerceAtLeast(0)
        handler.postAtTime(::renderFrame, SystemClock.uptimeMillis() + delayMs)
    }

    private fun renderFrame() {
        if (!running) return
        try {
            if (frameAvailable) {
                frameAvailable = false
                surfaceTexture.updateTexImage()
                surfaceTexture.getTransformMatrix(texMatrix)
                hasImage = true
                previewDirty = true
            }
            if (hasImage) {
                if (encoderEgl != EGL14.EGL_NO_SURFACE) {
                    makeCurrent(encoderEgl)
                    draw(0, 0, geometry.outputWidth, geometry.outputHeight)
                    // Timestamps start at 0 for each session (as WayXAuto did). Absolute
                    // System.nanoTime() values (hours of uptime) leave the display black.
                    EGLExt.eglPresentationTimeANDROID(display, encoderEgl, System.nanoTime() - startNanos)
                    EGL14.eglSwapBuffers(display, encoderEgl)
                    renderedFrames++
                }
                val now = System.nanoTime()
                if (previewDirty && previewEgl != EGL14.EGL_NO_SURFACE && previewWidth > 0 && previewHeight > 0 &&
                    now - lastPreviewNanos >= 1_000_000_000L / PREVIEW_FPS
                ) {
                    previewDirty = false
                    lastPreviewNanos = now
                    makeCurrent(previewEgl)
                    val scale = minOf(previewWidth / geometry.outputWidth.toFloat(), previewHeight / geometry.outputHeight.toFloat())
                    val w = (geometry.outputWidth * scale).toInt()
                    val h = (geometry.outputHeight * scale).toInt()
                    GLES20.glViewport(0, 0, previewWidth, previewHeight)
                    GLES20.glClearColor(0f, 0f, 0f, 1f)
                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                    draw((previewWidth - w) / 2, (previewHeight - h) / 2, w, h)
                    EGL14.eglSwapBuffers(display, previewEgl)
                }
            }
        } catch (t: Throwable) {
            OmoLog.w(TAG, "Render failed", t)
        }
        scheduleNext()
    }

    /** Draws the composition into the viewport (x, y, width, height) – GL bottom-left origin. */
    private fun draw(x: Int, y: Int, width: Int, height: Int) {
        val sx = width / geometry.outputWidth.toFloat()
        val sy = height / geometry.outputHeight.toFloat()
        // Black background (margins)
        GLES20.glViewport(x, y, width, height)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
        GLES20.glScissor(x, y, width, height)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)

        // UI area: convert top-left destination rect to GL bottom-left coordinates
        val destBottom = geometry.outputHeight - geometry.destTop - geometry.destHeight
        GLES20.glViewport(
            x + (geometry.destLeft * sx).toInt(),
            y + (destBottom * sy).toInt(),
            (geometry.destWidth * sx).toInt(),
            (geometry.destHeight * sy).toInt(),
        )
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
        GLES20.glUniformMatrix4fv(uTexMatrix, 1, false, texMatrix, 0)
        vertices.position(0)
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 16, vertices)
        GLES20.glEnableVertexAttribArray(aPosition)
        vertices.position(2)
        GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 16, vertices)
        GLES20.glEnableVertexAttribArray(aTexCoord)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun setUpEgl() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "No EGL display" }
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize failed" }
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        check(EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0) && count[0] > 0) { "No EGL config" }
        config = configs[0]
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        check(context != EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed" }
        // Only needed when no encoder surface is attached; may be unsupported with this config.
        pbuffer = EGL14.eglCreatePbufferSurface(display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
            ?: EGL14.EGL_NO_SURFACE
    }

    private fun createWindowSurface(surface: Surface): EGLSurface {
        val egl = EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
        check(egl != EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface failed: 0x${Integer.toHexString(EGL14.eglGetError())}" }
        return egl
    }

    private fun makeCurrent(surface: EGLSurface) {
        check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "eglMakeCurrent failed" }
    }

    private fun setUpGl() {
        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
        uTexMatrix = GLES20.glGetUniformLocation(program, "uTexMatrix")

        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        texture = ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        surfaceTexture = SurfaceTexture(texture).apply {
            setDefaultBufferSize(geometry.aa.sourceWidth, geometry.aa.sourceHeight)
            setOnFrameAvailableListener({
                frameAvailable = true
                sourceFrames++
            }, handler)
        }
        inputSurface = Surface(surfaceTexture)
        android.opengl.Matrix.setIdentityM(texMatrix, 0)

        // Crop rectangle in texture space (bottom-left origin).
        val srcW = geometry.aa.sourceWidth.toFloat()
        val srcH = geometry.aa.sourceHeight.toFloat()
        val u0 = geometry.cropLeft / srcW
        val u1 = (geometry.cropLeft + geometry.cropWidth) / srcW
        val v0 = 1f - (geometry.cropTop + geometry.cropHeight) / srcH
        val v1 = 1f - geometry.cropTop / srcH
        val data = floatArrayOf(
            -1f, -1f, u0, v0,
            1f, -1f, u1, v0,
            -1f, 1f, u0, v1,
            1f, 1f, u1, v1,
        )
        vertices = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(data)
            position(0)
        }
    }

    private fun buildProgram(vertex: String, fragment: String): Int {
        fun compile(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            check(status[0] != 0) { "Shader compile failed: ${GLES20.glGetShaderInfoLog(shader)}" }
            return shader
        }
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, compile(GLES20.GL_VERTEX_SHADER, vertex))
        GLES20.glAttachShader(program, compile(GLES20.GL_FRAGMENT_SHADER, fragment))
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        check(status[0] != 0) { "Program link failed: ${GLES20.glGetProgramInfoLog(program)}" }
        return program
    }

    private companion object {
        const val TAG = "OmoGl"
        /** The phone preview is a reference picture; 30 fps looks smooth and halves its cost. */
        const val PREVIEW_FPS = 30
        const val EGL_RECORDABLE_ANDROID = 0x3142

        const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            uniform mat4 uTexMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = (uTexMatrix * aTexCoord).xy;
            }
        """

        const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTexCoord);
            }
        """
    }
}
