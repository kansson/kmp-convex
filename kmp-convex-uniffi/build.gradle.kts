import com.vanniktech.maven.publish.SonatypeHost

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

    androidTarget {
        publishLibraryVariants("release")
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
}

android {
    namespace = "com.kansson.kmp.convex.uniffi"
    compileSdk = 35
    defaultConfig {
        minSdk = 24
    }
}

uniffi {
    generateFromUdl {
        packageName = "com.kansson.kmp.convex.core"
        cdylibName = "convex"
        udlFile = layout.projectDirectory.file("src/convex.udl")
    }
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
    pom {
        name = "kmp-convex-uniffi"
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
