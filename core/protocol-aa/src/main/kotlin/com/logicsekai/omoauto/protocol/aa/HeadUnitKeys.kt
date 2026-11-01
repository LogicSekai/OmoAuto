// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.protocol.aa

import java.io.ByteArrayInputStream
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateFactory
import java.security.cert.CertificateNotYetValidException
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.Date

/**
 * Parses and validates the head unit certificate + private key (PEM).
 *
 * Android Auto only talks to a head unit that presents a certificate. OmoAuto uses the public
 * sample identity from the open-source Open Headunit project, downloaded on demand (never bundled).
 */
object HeadUnitKeys {

    sealed class Result {
        data class Valid(val identity: HeadUnitIdentity, val subject: String, val validUntil: Date) : Result()
        data class Invalid(val problem: Problem) : Result()
    }

    enum class Problem {
        MISSING_PARTS,
        CERTIFICATE_UNREADABLE,
        CERTIFICATE_EXPIRED,
        CERTIFICATE_NOT_YET_VALID,
        KEY_PASSWORD_PROTECTED,
        KEY_UNSUPPORTED,
        KEY_UNREADABLE,
        KEY_MISMATCH,
    }

    fun parse(certificatePem: String, privateKeyPem: String, now: Date = Date()): Result {
        if (!certificatePem.contains("BEGIN CERTIFICATE") || !privateKeyPem.contains("PRIVATE KEY")) {
            return Result.Invalid(Problem.MISSING_PARTS)
        }
        val certificate = try {
            CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(certificatePem.toByteArray())) as X509Certificate
        } catch (e: Exception) {
            return Result.Invalid(Problem.CERTIFICATE_UNREADABLE)
        }
        try {
            certificate.checkValidity(now)
        } catch (e: CertificateExpiredException) {
            return Result.Invalid(Problem.CERTIFICATE_EXPIRED)
        } catch (e: CertificateNotYetValidException) {
            return Result.Invalid(Problem.CERTIFICATE_NOT_YET_VALID)
        }

        if (privateKeyPem.contains("ENCRYPTED PRIVATE KEY")) return Result.Invalid(Problem.KEY_PASSWORD_PROTECTED)
        if (!privateKeyPem.contains("BEGIN PRIVATE KEY")) return Result.Invalid(Problem.KEY_UNSUPPORTED)
        val der = try {
            pemBody(privateKeyPem, "PRIVATE KEY")
        } catch (e: IllegalArgumentException) {
            return Result.Invalid(Problem.KEY_UNREADABLE)
        }
        val algorithm = certificate.publicKey.algorithm // "RSA" or "EC"
        if (algorithm != "RSA" && algorithm != "EC") return Result.Invalid(Problem.KEY_UNSUPPORTED)
        val privateKey = try {
            KeyFactory.getInstance(algorithm).generatePrivate(PKCS8EncodedKeySpec(der))
        } catch (e: Exception) {
            return Result.Invalid(Problem.KEY_UNREADABLE)
        }
        if (!keyMatches(certificate, privateKey)) return Result.Invalid(Problem.KEY_MISMATCH)

        return Result.Valid(
            identity = HeadUnitIdentity(certificate, privateKey),
            subject = certificate.subjectX500Principal.name,
            validUntil = certificate.notAfter,
        )
    }

    /** Organisation (O=) or common name (CN=) from the certificate subject, for display. */
    fun displayName(subject: String): String {
        fun attribute(name: String) = Regex("(?:^|,)\\s*$name=([^,]+)").find(subject)?.groupValues?.get(1)?.trim()
        return attribute("O") ?: attribute("CN") ?: subject
    }

    private fun keyMatches(certificate: X509Certificate, key: PrivateKey): Boolean = try {
        val algorithm = if (key.algorithm == "EC") "SHA256withECDSA" else "SHA256withRSA"
        val probe = "omoauto-key-check".toByteArray()
        val signature = Signature.getInstance(algorithm).run {
            initSign(key)
            update(probe)
            sign()
        }
        Signature.getInstance(algorithm).run {
            initVerify(certificate.publicKey)
            update(probe)
            verify(signature)
        }
    } catch (e: Exception) {
        false
    }

    private fun pemBody(pem: String, label: String): ByteArray {
        val begin = "-----BEGIN $label-----"
        val end = "-----END $label-----"
        val start = pem.indexOf(begin)
        val stop = pem.indexOf(end)
        require(start >= 0 && stop > start) { "PEM block $label not found" }
        val base64 = pem.substring(start + begin.length, stop).replace(Regex("\\s"), "")
        return Base64.getDecoder().decode(base64)
    }
}
