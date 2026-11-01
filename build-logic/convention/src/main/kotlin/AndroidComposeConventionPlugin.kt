// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Enables Jetpack Compose on an Android module (application or library) and adds the
 * Compose BOM plus the libraries every UI module needs.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        pluginManager.withPlugin("com.android.application") {
            extensions.configure<ApplicationExtension> { buildFeatures { compose = true } }
        }
        pluginManager.withPlugin("com.android.library") {
            extensions.configure<LibraryExtension> { buildFeatures { compose = true } }
        }

        val bom = dependencies.platform(libs.lib("androidx-compose-bom"))
        dependencies.add("implementation", bom)
        dependencies.add("implementation", libs.lib("androidx-compose-ui"))
        dependencies.add("implementation", libs.lib("androidx-compose-ui-graphics"))
        dependencies.add("implementation", libs.lib("androidx-compose-foundation"))
        dependencies.add("implementation", libs.lib("androidx-compose-material3"))
        dependencies.add("implementation", libs.lib("androidx-compose-material-icons-extended"))
        dependencies.add("implementation", libs.lib("androidx-compose-ui-tooling-preview"))
        dependencies.add("debugImplementation", libs.lib("androidx-compose-ui-tooling"))
    }
}
