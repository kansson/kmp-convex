plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.atomicfu)
    alias(libs.plugins.android.library)
    alias(libs.plugins.gobley.cargo)
    alias(libs.plugins.gobley.uniffi)
    alias(libs.plugins.publish)
}

kotlin {
    explicitApi()
    jvmToolchain(21)

    androidTarget {
        publishLibraryVariants("release")
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
        }
    }
}

android {
    namespace = "com.kansson.kmp.convex.core"
    compileSdk = 35
    defaultConfig {
        minSdk = 24
    }
}
