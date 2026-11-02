plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt)
}

project(":sigil") {
    afterEvaluate {
        extensions.configure<com.android.build.gradle.LibraryExtension> {
            externalNativeBuild.cmake.path = rootProject.file("native/sigil/android/CMakeLists.txt")
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    baseline = file("$rootDir/config/detekt/baseline.xml")
    parallel = true
    autoCorrect = false
    source.setFrom(
        "$rootDir/app/src/main/kotlin",
        "$rootDir/app/src/test/kotlin"
    )
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true)
        xml.required.set(true)
        txt.required.set(false)
        sarif.required.set(false)
    }
}
