// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

import java.util.Properties

plugins {
    alias(libs.plugins.omoauto.android.application)
    alias(libs.plugins.omoauto.android.compose)
}

// ---- Signing -------------------------------------------------------------------------------
// Release and beta builds are signed with the keystore described in <root>/keystore.properties
// (created by scripts\signing-setup.bat, never committed). Without it they fall back to the
// debug key so the build still works on a fresh machine, but such bundles cannot go to Play.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasReleaseKeystore = keystoreProps.getProperty("storeFile")?.let { rootProject.file(it).exists() } == true

val omoVersionCode = (findProperty("omo.versionCode") as String?)?.toInt() ?: 1
val omoVersionName = (findProperty("omo.versionName") as String?) ?: "0.0.0"

// ---- Distribution ----------------------------------------------------------------------------
// "github" (default): APKs published on GitHub Releases. They check for updates, can download and
// install new versions, and show the donation links.
// "play": bundles for Google Play (scripts\bundle-*.bat pass -Pomo.distribution=play). The store
// handles updates, so self-update and its install permission are removed, and donation links are
// hidden because Play's payments policy does not allow them outside Play billing.
val playBuild = (findProperty("omo.distribution") as String?) == "play"
fun quoted(value: String) = "\"$value\""

android {
    namespace = "com.logicsekai.omoauto"

    defaultConfig {
        applicationId = "com.logicsekai.omoauto"
        versionCode = omoVersionCode
        versionName = omoVersionName

        // Links shown in the app. Leave a value empty to hide it.
        buildConfigField("String", "UPDATE_REPO", quoted(if (playBuild) "" else "LogicSekai/OmoAuto"))
        buildConfigField("String", "COMMUNITY_URL", quoted("https://logicsekai.com"))
        buildConfigField("String", "SOURCE_URL", quoted("https://github.com/LogicSekai/OmoAuto"))
        buildConfigField("String", "SPONSOR_URL", quoted(if (playBuild) "" else "https://github.com/sponsors/LogicSekai"))
        buildConfigField("String", "KOFI_URL", quoted(if (playBuild) "" else "https://ko-fi.com/logicsekai"))
        buildConfigField("String", "TRAKTEER_URL", quoted(if (playBuild) "" else "https://trakteer.id/kwaii"))
        // Release builds from GitHub download and install updates themselves; debug builds
        // (different package) only link to the release page.
        buildConfigField("boolean", "SELF_UPDATE", "false")
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            buildConfigField("boolean", "SELF_UPDATE", (!playBuild).toString())
        }
        create("beta") {
            initWith(getByName("release"))
            versionNameSuffix = "-beta"
            matchingFallbacks += listOf("release")
            // Beta bundles only go to Play testing tracks.
            buildConfigField("String", "UPDATE_REPO", quoted(""))
            buildConfigField("boolean", "SELF_UPDATE", "false")
        }
    }

    // Play bundles: drop the self-update install permission (see src/play/AndroidManifest.xml).
    sourceSets.getByName("beta").manifest.srcFile("src/play/AndroidManifest.xml")
    if (playBuild) {
        sourceSets.getByName("release").manifest.srcFile("src/play/AndroidManifest.xml")
    }

    buildFeatures {
        buildConfig = true
    }

    bundle {
        // Keep Indonesian and English in the base module so switching language needs no download.
        language { enableSplit = false }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:streaming"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:home"))
    implementation(project(":feature:setup"))
    implementation(project(":feature:controls"))
    implementation(project(":feature:diagnostics"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:about"))
    implementation(project(":feature:support"))
    implementation(project(":feature:update"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
}
