import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.vanniktech.publish)
    alias(libs.plugins.dokka)
}

/*
 * :kitecore is the runtime gap-closing library of the Kite lineage — the
 * complement to the kmp-ssot Gradle plugin. The plugin closes build-time gaps
 * (platform-tree injection); :kitecore closes runtime gaps that a build-time
 * codegen pass structurally cannot (a CoroutineDispatcher, platform identity).
 *
 * v0.1.0 ships:
 *   - ioDispatcher(): unified IO dispatcher — real Dispatchers.IO on
 *     JVM/Android/Apple (it DOES exist on Native since coroutines 1.7), worker-
 *     or-Default on the web targets.
 *   - KiteWorker (js): the inline Blob Web-Worker offload primitive, packaged.
 *   - Platform: name / osVersion / deviceModel / family, per target.
 *
 * Only dependency is kotlinx-coroutines-core (exposed via `api`, since
 * ioDispatcher() returns a CoroutineDispatcher).
 */
kotlin {
    jvmToolchain(21)

    android {
        namespace = "io.github.yuroyami.kitecore"
        compileSdk = 36
        minSdk = 21
    }

    jvm()

    // Apple — frameworks for direct Xcode embedding.
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
        iosX64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "KiteCore"
            isStatic = false
        }
    }
    macosArm64()

    // Web
    js {
        browser()
        nodejs()
        binaries.library()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        nodejs()
    }

    sourceSets {
        all {
            languageSettings {
                optIn("kotlin.RequiresOptIn")
            }
        }

        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// Publishing is configured by the vanniktech plugin from gradle.properties:
// shared coordinates/POM/signing in the root gradle.properties, this module's
// POM_NAME + POM_DESCRIPTION in kitecore/gradle.properties.
