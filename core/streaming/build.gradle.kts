// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

plugins {
    alias(libs.plugins.omoauto.android.library)
}

android {
    namespace = "com.logicsekai.omoauto.streaming"
}

dependencies {
    api(project(":core:protocol-aa"))
    api(project(":core:protocol-omoway"))
    api(project(":core:data"))
    implementation(project(":core:common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.kotlinx.coroutines.android)
}
