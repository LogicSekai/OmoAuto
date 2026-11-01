// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.protocol.aa

/** Throwaway self-signed test identities (src/test/resources), never used by the app. */
internal object TestKeys {
    fun text(name: String): String =
        requireNotNull(TestKeys::class.java.classLoader.getResourceAsStream(name)) { "Missing test resource $name" }
            .bufferedReader().use { it.readText() }

    val headUnitCert get() = text("test-headunit-cert.pem")
    val headUnitKey get() = text("test-headunit-key.pem")
    val headUnitKeyEncrypted get() = text("test-headunit-key-encrypted.pem")
    val phoneCert get() = text("test-phone-cert.pem")
    val phoneKey get() = text("test-phone-key.pem")

    fun headUnitIdentity(): HeadUnitIdentity =
        (HeadUnitKeys.parse(headUnitCert, headUnitKey) as HeadUnitKeys.Result.Valid).identity

    fun phoneIdentity(): HeadUnitIdentity =
        (HeadUnitKeys.parse(phoneCert, phoneKey) as HeadUnitKeys.Result.Valid).identity
}
