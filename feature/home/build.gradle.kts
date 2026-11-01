// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

plugins {
    alias(libs.plugins.omoauto.android.feature)
}

android {
    namespace = "com.logicsekai.omoauto.feature.home"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:streaming"))
}
