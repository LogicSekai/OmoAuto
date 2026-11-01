// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

plugins {
    alias(libs.plugins.omoauto.android.library)
    alias(libs.plugins.omoauto.android.compose)
}

android {
    namespace = "com.logicsekai.omoauto.designsystem"
}

dependencies {
    // LivePreview reports touches with the Android Auto touch action codes.
    implementation(project(":core:protocol-aa"))
}
