enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven {
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
            content {
                includeGroupByRegex("dev\\.gobley\\..*")
            }
        }
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "kmp-convex"
include(":kmp-convex-core")
include(":kmp-convex-bindings")
include(":kmp-convex-plugin")
