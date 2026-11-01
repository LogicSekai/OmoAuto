// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

plugins {
    alias(libs.plugins.omoauto.android.feature)
}

android {
    namespace = "com.logicsekai.omoauto.feature.onboarding"
}

dependencies {
    implementation(project(":core:data"))
}
