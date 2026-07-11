plugins {
    alias(libs.plugins.kotlin.multiplatform).apply(false)
    alias(libs.plugins.android.kmp.library).apply(false)
    alias(libs.plugins.vanniktech.publish).apply(false)
    // Applied (not deferred) at the root so `dokkaGenerate` aggregates the
    // library module into one API site at build/dokka/html.
    alias(libs.plugins.dokka)
}

// group and version come from gradle.properties.

dependencies {
    dokka(project(":kitecore"))
}

dokka {
    moduleName.set("KiteCore")
}
