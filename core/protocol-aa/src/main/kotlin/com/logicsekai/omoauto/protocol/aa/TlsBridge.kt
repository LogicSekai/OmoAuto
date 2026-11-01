// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.protocol.aa

import java.io.ByteArrayOutputStream
import java.net.Socket
import java.nio.Buffer
import java.nio.ByteBuffer
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult
import javax.net.ssl.SSLException
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager

/** Certificate + private key our head unit presents to Android Auto. */
class HeadUnitIdentity(val certificate: X509Certificate, val privateKey: PrivateKey)

/**
 * Runs TLS over Android Auto's own framing: handshake records travel inside SSL_HANDSHAKE
 * messages, later records inside frames flagged ENCRYPTED. The head unit is the TLS client.
 */
internal class TlsBridge(private val engine: SSLEngine) {

    private var inbound: ByteBuffer = ByteBuffer.allocate(0)

    var isHandshakeComplete = false
        private set

    companion object {
        /** TLS client engine presenting the head unit [identity]. */
        fun client(identity: HeadUnitIdentity): TlsBridge = TlsBridge(createEngine(identity, clientMode = true))

        /** Also used by tests to play the phone (TLS server) side. */
        fun createEngine(identity: HeadUnitIdentity, clientMode: Boolean): SSLEngine {
            val context = SSLContext.getInstance("TLS")
            context.init(arrayOf(SingleKeyManager(identity)), arrayOf(AcceptAllTrustManager), SecureRandom())
            return context.createSSLEngine().apply {
                useClientMode = clientMode
                if (!clientMode) wantClientAuth = true
            }
        }
    }

    /** Starts the handshake and returns the first records to send (ClientHello). */
    fun begin(): ByteArray {
        engine.beginHandshake()
        return advance()
    }

    /** Feeds handshake bytes from the phone and returns whatever must be sent back. */
    fun onHandshakeData(data: ByteArray): ByteArray {
        append(data)
        return advance()
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val src = ByteBuffer.wrap(plain)
        val out = ByteArrayOutputStream(plain.size + 64)
        var dst = ByteBuffer.allocate(engine.session.packetBufferSize)
        while (src.hasRemaining()) {
            dst.resetForWrite()
            val result = engine.wrap(src, dst)
            when (result.status) {
                SSLEngineResult.Status.OK -> out.write(dst.array(), 0, dst.position())
                SSLEngineResult.Status.BUFFER_OVERFLOW -> dst = ByteBuffer.allocate(dst.capacity() * 2)
                else -> throw SSLException("TLS wrap failed: ${result.status}")
            }
        }
        return out.toByteArray()
    }

    fun decrypt(cipher: ByteArray): ByteArray {
        append(cipher)
        val out = ByteArrayOutputStream(cipher.size)
        var app = ByteBuffer.allocate(engine.session.applicationBufferSize)
        while (inbound.hasRemaining()) {
            app.resetForWrite()
            val result = engine.unwrap(inbound, app)
            out.write(app.array(), 0, app.position())
            when (result.status) {
                SSLEngineResult.Status.BUFFER_UNDERFLOW -> break
                SSLEngineResult.Status.BUFFER_OVERFLOW -> app = ByteBuffer.allocate(app.capacity() * 2)
                SSLEngineResult.Status.OK -> if (result.bytesConsumed() == 0 && result.bytesProduced() == 0) break
                else -> throw SSLException("TLS unwrap failed: ${result.status}")
            }
            runDelegatedTasks()
        }
        compactInbound()
        return out.toByteArray()
    }

    /**
     * Drives the handshake as far as the bytes we have allow.
     *
     * Only constants that exist on Android are referenced here: Java 9+ added
     * HandshakeStatus.NEED_UNWRAP_AGAIN (DTLS only) and touching it crashes on Android with
     * NoSuchFieldError. Unknown statuses are treated like NEED_UNWRAP.
     */
    private fun advance(): ByteArray {
        val out = ByteArrayOutputStream()
        val empty = ByteBuffer.allocate(0)
        var net = ByteBuffer.allocate(engine.session.packetBufferSize)
        var app = ByteBuffer.allocate(engine.session.applicationBufferSize)
        while (true) {
            val status = engine.handshakeStatus
            if (status == null ||
                status == SSLEngineResult.HandshakeStatus.FINISHED ||
                status == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING
            ) {
                if (status != null) isHandshakeComplete = true
                break
            } else if (status == SSLEngineResult.HandshakeStatus.NEED_TASK) {
                runDelegatedTasks()
            } else if (status == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
                net.resetForWrite()
                val result = engine.wrap(empty, net)
                val outcome = result.status
                if (outcome == SSLEngineResult.Status.OK) {
                    out.write(net.array(), 0, net.position())
                } else if (outcome == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                    net = ByteBuffer.allocate(net.capacity() * 2)
                } else {
                    throw SSLException("TLS closed during handshake ($outcome)")
                }
                if (result.handshakeStatus == SSLEngineResult.HandshakeStatus.FINISHED) isHandshakeComplete = true
            } else {
                // NEED_UNWRAP (and, on desktop JDKs, NEED_UNWRAP_AGAIN).
                if (!inbound.hasRemaining()) break
                app.resetForWrite()
                val result = engine.unwrap(inbound, app)
                val outcome = result.status
                if (outcome == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                    break
                } else if (outcome == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                    app = ByteBuffer.allocate(app.capacity() * 2)
                } else if (outcome == SSLEngineResult.Status.CLOSED) {
                    throw SSLException("TLS closed during handshake")
                }
                if (result.handshakeStatus == SSLEngineResult.HandshakeStatus.FINISHED) isHandshakeComplete = true
            }
        }
        compactInbound()
        return out.toByteArray()
    }

    private fun runDelegatedTasks() {
        while (true) {
            val task = engine.delegatedTask ?: return
            task.run()
        }
    }

    private fun append(data: ByteArray) {
        val combined = ByteBuffer.allocate(inbound.remaining() + data.size)
        combined.put(inbound)
        combined.put(data)
        (combined as Buffer).flip()
        inbound = combined
    }

    private fun compactInbound() {
        if (!inbound.hasRemaining()) inbound = ByteBuffer.allocate(0)
    }
}

/** Always offers our single head-unit certificate for client authentication. */
private class SingleKeyManager(private val identity: HeadUnitIdentity) : X509ExtendedKeyManager() {
    override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(ALIAS)
    override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = ALIAS
    override fun chooseEngineClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?) = ALIAS
    override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(ALIAS)
    override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?) = ALIAS
    override fun chooseEngineServerAlias(keyType: String?, issuers: Array<out Principal>?, engine: SSLEngine?): String? =
        if (keyType == null || keyType == identity.privateKey.algorithm) ALIAS else null
    override fun getCertificateChain(alias: String?): Array<X509Certificate> = arrayOf(identity.certificate)
    override fun getPrivateKey(alias: String?): PrivateKey = identity.privateKey

    private companion object {
        const val ALIAS = "headunit"
    }
}

/**
 * The phone presents a Google-issued certificate that a head unit cannot verify offline;
 * real head units accept it as well. The connection is loopback-only (127.0.0.1).
 */
private object AcceptAllTrustManager : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

/**
 * `ByteBuffer.clear()` compiled against JDK 9+ links to a covariant method that Android 8 lacks;
 * calling it through [Buffer] links to the method every Android version has.
 */
private fun ByteBuffer.resetForWrite() {
    (this as Buffer).clear()
}
