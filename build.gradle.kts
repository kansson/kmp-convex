import io.gitlab.arturbosch.detekt.Detekt

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.plugin.atomicfu) apply false
    alias(libs.plugins.kotlin.plugin.serialization) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.gobley.cargo) apply false
    alias(libs.plugins.gobley.uniffi) apply false
    alias(libs.plugins.gobley.rust) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.gradle.publish) apply false
    alias(libs.plugins.detekt)
}

subprojects {
    group = "com.kansson.kmp"
}

dependencies {
    detektPlugins(libs.detekt.formatting)
}

detekt {
    source.from(rootProject.rootDir)
    parallel = true
    config.from("detekt.yaml")
    buildUponDefaultConfig = true
}

tasks.withType<Detekt>().configureEach {
    exclude("**/build/**")
}
