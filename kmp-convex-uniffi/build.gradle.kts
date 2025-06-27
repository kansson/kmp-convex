plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.atomicfu)
    alias(libs.plugins.android.library)
    alias(libs.plugins.gobley.cargo)
    alias(libs.plugins.gobley.uniffi)
    alias(libs.plugins.maven.publish)
}

kotlin {
    jvmToolchain(21)

    androidLibrary {
        namespace = "com.kansson.kmp.convex.uniffi"
        compileSdk = 35
        minSdk = 24
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()
}

uniffi {
    generateFromUdl {
        packageName = "com.kansson.kmp.convex.core"
        cdylibName = "convex"
        udlFile = layout.projectDirectory.file("src/convex.udl")
    }
}
