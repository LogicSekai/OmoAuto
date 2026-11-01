// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.data.phone

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhoneVendorTest {

    @Test
    fun `manufacturer and brand map to vendor families`() {
        assertEquals(PhoneVendor.XIAOMI, PhoneVendor.from("Xiaomi", "Redmi"))
        assertEquals(PhoneVendor.XIAOMI, PhoneVendor.from("Xiaomi", "POCO"))
        assertEquals(PhoneVendor.SAMSUNG, PhoneVendor.from("samsung", "samsung"))
        assertEquals(PhoneVendor.OPPO_REALME_ONEPLUS, PhoneVendor.from("realme", "realme"))
        assertEquals(PhoneVendor.OPPO_REALME_ONEPLUS, PhoneVendor.from("OnePlus", "OnePlus"))
        assertEquals(PhoneVendor.OPPO_REALME_ONEPLUS, PhoneVendor.from("OPPO", "OPPO"))
        assertEquals(PhoneVendor.VIVO, PhoneVendor.from("vivo", "iQOO"))
        assertEquals(PhoneVendor.HUAWEI_HONOR, PhoneVendor.from("HONOR", "HONOR"))
        assertEquals(PhoneVendor.TRANSSION, PhoneVendor.from("INFINIX MOBILITY LIMITED", "Infinix"))
        assertEquals(PhoneVendor.TRANSSION, PhoneVendor.from("TECNO MOBILE LIMITED", "TECNO"))
        assertEquals(PhoneVendor.OTHER, PhoneVendor.from("Google", "google"))
        assertEquals(PhoneVendor.OTHER, PhoneVendor.from(null, null))
    }

    @Test
    fun `only near-stock phones skip the background step`() {
        assertFalse(PhoneVendor.OTHER.needsBackgroundStep)
        PhoneVendor.entries.filter { it != PhoneVendor.OTHER }.forEach { assertTrue(it.needsBackgroundStep, it.name) }
    }
}
