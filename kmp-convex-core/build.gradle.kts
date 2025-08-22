
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.atomicfu)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish)
}

kotlin {
    explicitApi()
    jvmToolchain(21)

    jvm()
    androidTarget {
        publishLibraryVariants("release")
    }

    macosX64()
    macosArm64()
    iosSimulatorArm64()
    iosX64()
    iosArm64()

    linuxX64()
    linuxArm64()
    mingwX64()

    sourceSets {
        commonMain.dependencies {
            implementation(projects.kmpConvexBindings)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.io.core)
        }
    }
}

android {
    namespace = "com.kansson.kmp.convex.core"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    pom {
        name = "kmp-convex"
        description = "Convex for Kotlin Multiplatform"
        inceptionYear = "2025"
        url = "https://github.com/kansson/kmp-convex"
        licenses {
            license {
                name = "MIT License"
                url = "https://opensource.org/licenses/mit"
            }
        }
        developers {
            developer {
                id = "kansson"
                name = "Isak Hansson"
                url = "https://github.com/kansson"
            }
        }
        scm {
            url = "https://github.com/kansson/kmp-convex"
            connection = "scm:git:https://github.com/kansson/kmp-convex.git"
            developerConnection = "scm:git:ssh://git@github.com/kansson/kmp-convex.git"
        }
    }
}
