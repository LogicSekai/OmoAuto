// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.protocol.aa

import org.junit.Test
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HeadUnitKeysTest {

    @Test
    fun `matching certificate and key are accepted`() {
        val result = HeadUnitKeys.parse(TestKeys.headUnitCert, TestKeys.headUnitKey)
        val valid = assertIs<HeadUnitKeys.Result.Valid>(result)
        assertEquals("OmoAuto Test headunit", HeadUnitKeys.displayName(valid.subject))
        assertEquals("Google-Android-Reference", HeadUnitKeys.displayName("C=US,ST=CA,L=Mountain View,O=Google-Android-Reference"))
        assertEquals("only-cn", HeadUnitKeys.displayName("CN=only-cn"))
    }

    @Test
    fun `key from another identity is rejected`() {
        val result = HeadUnitKeys.parse(TestKeys.headUnitCert, TestKeys.phoneKey)
        assertEquals(HeadUnitKeys.Problem.KEY_MISMATCH, assertIs<HeadUnitKeys.Result.Invalid>(result).problem)
    }

    @Test
    fun `password protected key is reported`() {
        val result = HeadUnitKeys.parse(TestKeys.headUnitCert, TestKeys.headUnitKeyEncrypted)
        assertEquals(HeadUnitKeys.Problem.KEY_PASSWORD_PROTECTED, assertIs<HeadUnitKeys.Result.Invalid>(result).problem)
    }

    @Test
    fun `missing parts and expiry are reported`() {
        assertEquals(
            HeadUnitKeys.Problem.MISSING_PARTS,
            assertIs<HeadUnitKeys.Result.Invalid>(HeadUnitKeys.parse("", TestKeys.headUnitKey)).problem,
        )
        val farFuture = Date(System.currentTimeMillis() + 100L * 365 * 24 * 3600 * 1000)
        assertEquals(
            HeadUnitKeys.Problem.CERTIFICATE_EXPIRED,
            assertIs<HeadUnitKeys.Result.Invalid>(HeadUnitKeys.parse(TestKeys.headUnitCert, TestKeys.headUnitKey, farFuture)).problem,
        )
    }
}
