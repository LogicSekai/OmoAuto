// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

plugins {
    alias(libs.plugins.omoauto.android.library)
}

android {
    namespace = "com.logicsekai.omoauto.data"
}

dependencies {
    api(project(":core:protocol-aa"))
    implementation(project(":core:common"))
    implementation(libs.androidx.core.ktx)
    // api: SettingsRepository setters return DataStore Preferences.
    api(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    // Background update checks (UpdateCheckWorker).
    implementation(libs.androidx.work.runtime.ktx)
}
