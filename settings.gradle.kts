// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "OmoAuto"

// Application
include(":app")

// Core – pure Kotlin/JVM (unit-testable on any machine, no Android SDK needed)
include(":core:common")
include(":core:protocol-aa")
include(":core:protocol-omoway")

// Core – Android
include(":core:designsystem")
include(":core:data")
include(":core:streaming")

// Features (one screen/flow per module)
include(":feature:onboarding")
include(":feature:home")
include(":feature:setup")
include(":feature:controls")
include(":feature:diagnostics")
include(":feature:settings")
include(":feature:about")
include(":feature:support")
include(":feature:update")
