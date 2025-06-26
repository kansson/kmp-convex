plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.gradle.publish)
}

version = "0.0.0"

kotlin {
    explicitApi()
    jvmToolchain(21)
}

gradlePlugin {
    website = "https://github.com/kansson/kmp-convex"
    vcsUrl = "https://github.com/kansson/kmp-convex.git"

    plugins {
        create("kmp-convex-plugin") {
            id = "com.kansson.kmp.convex"
            displayName = "Convex for Kotlin Multiplatform"
            description = "Gradle Plugin that can generate type-safe models from Convex functions."
            tags = listOf("kotlin", "multiplatform", "kmp", "convwex")
            implementationClass = "com.kansson.kmp.convex.plugin.ConvexPlugin"
        }
    }
}

dependencies {
    compileOnly(libs.kotlin.gradle.plugin)
    implementation(libs.ktor.client.java)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.poet.kotlin)
}
