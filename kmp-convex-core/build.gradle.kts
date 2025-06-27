import com.vanniktech.maven.publish.SonatypeHost

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

    androidLibrary {
        namespace = "com.kansson.kmp.convex.core"
        compileSdk = 35
        minSdk = 24
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(projects.kmpConvexUniffi)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.io.core)
        }
    }
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
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
