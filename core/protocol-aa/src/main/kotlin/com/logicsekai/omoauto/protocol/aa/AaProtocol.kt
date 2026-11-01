// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.protocol.aa

/** Channel ids announced by our service discovery response. */
object AaChannel {
    const val CONTROL = 0
    const val SENSOR = 1
    const val VIDEO = 2
    const val INPUT = 3
    const val AUDIO = 4
    const val MICROPHONE = 5
}

/** Message ids on the control channel (0). Channel-open (7/8) also appears on other channels. */
object AaControl {
    const val VERSION_REQUEST = 1
    const val VERSION_RESPONSE = 2
    const val SSL_HANDSHAKE = 3
    const val AUTH_COMPLETE = 4
    const val SERVICE_DISCOVERY_REQUEST = 5
    const val SERVICE_DISCOVERY_RESPONSE = 6
    const val CHANNEL_OPEN_REQUEST = 7
    const val CHANNEL_OPEN_RESPONSE = 8
    const val PING_REQUEST = 11
    const val PING_RESPONSE = 12
    const val NAV_FOCUS_REQUEST = 13
    const val NAV_FOCUS_NOTIFICATION = 14
    const val SHUTDOWN_REQUEST = 15
    const val SHUTDOWN_RESPONSE = 16
    const val VOICE_SESSION_REQUEST = 17
    const val AUDIO_FOCUS_REQUEST = 18
    const val AUDIO_FOCUS_NOTIFICATION = 19

    const val PROTOCOL_MAJOR = 1
    const val PROTOCOL_MINOR = 1
}

/** Media channel messages (video, audio, microphone). */
object AaMedia {
    const val DATA = 0
    const val CODEC_CONFIG = 1
    const val SETUP_REQUEST = 0x8000
    const val START = 0x8001
    const val STOP = 0x8002
    const val CONFIG_RESPONSE = 0x8003
    const val ACK = 0x8004
    const val MIC_REQUEST = 0x8005
    const val MIC_RESPONSE = 0x8006
    const val VIDEO_FOCUS_REQUEST = 0x8007
    const val VIDEO_FOCUS_NOTIFICATION = 0x8008
}

object AaSensor {
    const val START_REQUEST = 0x8001
    const val START_RESPONSE = 0x8002
    const val EVENT = 0x8003

    const val TYPE_NIGHT_MODE = 10
    const val TYPE_DRIVING_STATUS = 13
}

object AaInput {
    const val EVENT = 0x8001
    const val BINDING_REQUEST = 0x8002
    const val BINDING_RESPONSE = 0x8003
}

/** Touch actions (Android MotionEvent values) for [AaMessages.touchEvent]. */
object AaTouchAction {
    const val DOWN = 0
    const val UP = 1
    const val MOVE = 2
}

/** Key codes the head unit announces. Values follow Android KeyEvent / AA rotary conventions. */
object AaKeyCode {
    const val HOME = 3
    const val BACK = 4
    const val DPAD_UP = 19
    const val DPAD_DOWN = 20
    const val DPAD_LEFT = 21
    const val DPAD_RIGHT = 22
    const val DPAD_CENTER = 23
    const val ROTARY_CONTROLLER = 65536
    const val MEDIA = 65538

    val ANNOUNCED = intArrayOf(HOME, BACK, DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT, DPAD_CENTER, ROTARY_CONTROLLER, MEDIA)
}

/** Builders for the protobuf bodies we send. Field numbers follow the AA head-unit protocol. */
internal object AaMessages {

    fun versionRequest(): ByteArray = byteArrayOf(
        0, AaControl.PROTOCOL_MAJOR.toByte(), 0, AaControl.PROTOCOL_MINOR.toByte(),
    )

    /** Generic `{1: status}` body, status 0 = OK. */
    fun status(status: Int = 0): ByteArray = ProtoWriter().varint(1, status).toByteArray()

    fun pingResponse(timestamp: Long): ByteArray = ProtoWriter().varint(1, timestamp).toByteArray()

    fun navFocus(type: Int = 2): ByteArray = ProtoWriter().varint(1, type).toByteArray()

    /** focus state 1 = GAIN, 3 = LOSS. */
    fun audioFocus(state: Int): ByteArray = ProtoWriter().varint(1, state).bool(2, false).toByteArray()

    /** Media config response: status 2 = READY, [maxUnacked] frames in flight, configuration index 0. */
    fun mediaConfig(maxUnacked: Int): ByteArray =
        ProtoWriter().varint(1, 2).varint(2, maxUnacked).varint(3, 0).toByteArray()

    fun mediaAck(session: Int): ByteArray = ProtoWriter().varint(1, session).varint(2, 1).toByteArray()

    /** Video focus: mode 1 = PROJECTED. */
    fun videoFocus(unsolicited: Boolean): ByteArray = ProtoWriter().varint(1, 1).bool(2, unsolicited).toByteArray()

    fun micResponse(): ByteArray = ProtoWriter().varint(1, 0).varint(2, 0).toByteArray()

    fun drivingStatusEvent(status: Int = 0): ByteArray =
        ProtoWriter().message(AaSensor.TYPE_DRIVING_STATUS) { varint(1, status) }.toByteArray()

    fun nightModeEvent(night: Boolean): ByteArray =
        ProtoWriter().message(AaSensor.TYPE_NIGHT_MODE) { bool(1, night) }.toByteArray()

    fun keyEvent(timestamp: Long, keyCode: Int, down: Boolean): ByteArray = ProtoWriter()
        .varint(1, timestamp)
        .message(4) {
            message(1) {
                varint(1, keyCode)
                bool(2, down)
                varint(3, 0) // meta state
                bool(4, false) // long press
            }
        }
        .toByteArray()

    fun rotaryEvent(timestamp: Long, delta: Int): ByteArray = ProtoWriter()
        .varint(1, timestamp)
        .message(6) {
            message(1) {
                varint(1, AaKeyCode.ROTARY_CONTROLLER)
                varint(2, delta.toLong())
            }
        }
        .toByteArray()

    /**
     * Single-finger touch at ([x], [y]) in Android Auto's video coordinates (the full frame,
     * margins included). Input report field 3 = touch event: pointers, action index, action.
     */
    fun touchEvent(timestamp: Long, action: Int, x: Int, y: Int): ByteArray = ProtoWriter()
        .varint(1, timestamp)
        .message(3) {
            message(1) {
                varint(1, x)
                varint(2, y)
                varint(3, 0) // pointer id
            }
            varint(2, 0) // action index
            varint(3, action)
        }
        .toByteArray()

    fun shutdownRequest(): ByteArray = ProtoWriter().varint(1, 1).toByteArray()

    fun serviceDiscoveryResponse(config: HeadUnitConfig): ByteArray {
        val audio = ProtoWriter().varint(1, 16_000).varint(2, 16).varint(3, 1).toByteArray()
        return ProtoWriter()
            // 1: sensor source (driving status + night mode)
            .message(1) {
                varint(1, AaChannel.SENSOR)
                message(2) {
                    message(1) { varint(1, AaSensor.TYPE_DRIVING_STATUS) }
                    message(1) { varint(1, AaSensor.TYPE_NIGHT_MODE) }
                }
            }
            // 2: video sink, H.264 baseline
            .message(1) {
                varint(1, AaChannel.VIDEO)
                message(3) {
                    varint(1, 3) // MEDIA_CODEC_VIDEO_H264_BP
                    message(4) {
                        varint(1, config.video.resolutionCode)
                        varint(2, 1) // 60 fps
                        varint(3, config.video.marginWidth)
                        varint(4, config.video.marginHeight)
                        varint(5, config.video.density)
                    }
                    varint(5, 1) // available while in call
                }
            }
            // 3: input source (keys + rotary, and a touch screen the size of the video frame so
            //    the phone preview can act as a touchpad)
            .message(1) {
                varint(1, AaChannel.INPUT)
                message(4) {
                    AaKeyCode.ANNOUNCED.forEach { varint(1, it) }
                    if (config.touchscreen) {
                        message(2) {
                            varint(1, config.video.sourceWidth)
                            varint(2, config.video.sourceHeight)
                        }
                    }
                }
            }
            // 4: audio sink (guidance/system audio, PCM 16 kHz mono; we accept and discard)
            .message(1) {
                varint(1, AaChannel.AUDIO)
                message(3) {
                    varint(1, 1) // PCM
                    varint(2, 2)
                    bytes(3, audio)
                }
            }
            // 5: microphone source (declared but never opened)
            .message(1) {
                varint(1, AaChannel.MICROPHONE)
                message(5) {
                    varint(1, 1)
                    bytes(2, audio)
                }
            }
            .string(2, config.info.make)
            .string(3, config.info.model)
            .string(4, config.info.year)
            .string(5, config.info.vehicleId)
            .varint(6, 0) // driver position
            .string(7, config.info.headUnitMake)
            .string(8, config.info.headUnitModel)
            .string(9, config.info.softwareBuild)
            .string(10, config.info.softwareVersion)
            .bool(11, false)
            .toByteArray()
    }
}
