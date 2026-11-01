// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * Pure Kotlin/JVM module: no Android SDK, so it builds and tests anywhere.
 *
 * These modules run on Android, but they are compiled against the desktop JDK. To stop Java 9+
 * APIs that Android lacks from slipping in (for example SSLEngineResult.HandshakeStatus
 * .NEED_UNWRAP_AGAIN, or ByteBuffer.clear() returning ByteBuffer, both crash on Android 8),
 * Kotlin compiles them against the Java 8 API with -Xjdk-release.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")

        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }
        extensions.configure<KotlinJvmProjectExtension> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_1_8)
                freeCompilerArgs.add("-Xjdk-release=1.8")
            }
        }

        dependencies.add("testImplementation", libs.lib("junit4"))
        dependencies.add("testImplementation", libs.lib("kotlin-test"))
    }
}
