// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * A feature module = Android library + Compose + the shared design system.
 * Features never depend on each other; :app wires them together.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("omoauto.android.library")
        pluginManager.apply("omoauto.android.compose")

        dependencies.add("implementation", project(":core:designsystem"))
        dependencies.add("implementation", project(":core:common"))
        dependencies.add("implementation", libs.lib("androidx-activity-compose"))
        dependencies.add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
        dependencies.add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
        dependencies.add("implementation", libs.lib("kotlinx-coroutines-android"))
    }
}
