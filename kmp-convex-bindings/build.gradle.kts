import gobley.gradle.cargo.dsl.android
import gobley.gradle.cargo.dsl.appleMobile
import gobley.gradle.rust.dsl.rustVersion

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

    watchosSimulatorArm64()
    watchosX64()
    // https://github.com/rust-lang/rust/issues/147776?utm_source=chatgpt.com
    // watchosArm32()
    // watchosArm64()
    watchosDeviceArm64()

    tvosSimulatorArm64()
    tvosX64()
    tvosArm64()

    sourceSets {
        androidMain.dependencies {
            //noinspection UseTomlInstead
            implementation("net.java.dev.jna:jna:5.17.0@aar")
        }
    }
}

android {
    namespace = "com.kansson.kmp.convex.bindings"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }
}

cargo {
    builds.android {
        variants {
            buildTaskProvider.configure {
                additionalEnvironment.put(
                    "RUSTFLAGS",
                    "-C link-args=-Wl,-z,max-page-size=16384",
                )
            }
        }
    }

    builds.appleMobile {
        variants {
            if (rustTarget.tier(project.rustVersion.get()) >= 3) {
                buildTaskProvider.configure {
                    nightly = true
                    extraArguments.add("-Zbuild-std")
                }
                checkTaskProvider.configure {
                    nightly = true
                    extraArguments.add("-Zbuild-std")
                }
            }
        }
    }
}

uniffi {
    generateFromUdl {
        packageName = "com.kansson.kmp.convex.bindings"
        cdylibName = "convex"
        udlFile = layout.projectDirectory.file("src/convex.udl")
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    pom {
        name = "kmp-convex-bindings"
        description = "Native Convex bindings for Kotlin Multiplatform"
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
