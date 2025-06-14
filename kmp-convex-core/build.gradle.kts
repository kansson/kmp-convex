plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.atomicfu)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.gobley.cargo)
    alias(libs.plugins.gobley.uniffi)
    alias(libs.plugins.maven.publish)
}

kotlin {
    jvmToolchain(21)

    androidTarget {
        publishLibraryVariants("release")
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
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

uniffi {
    generateFromUdl {
        packageName = "com.kansson.kmp.convex.core"
        cdylibName = "convex"
        udlFile = layout.projectDirectory.file("../src/convex.udl")
    }
}
