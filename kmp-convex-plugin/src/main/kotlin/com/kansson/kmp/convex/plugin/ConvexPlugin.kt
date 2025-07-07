package com.kansson.kmp.convex.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileCommon
import java.util.Properties

public class ConvexPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("convex", ConvexExtension::class.java)
        val task = project.tasks.register("generateConvexSources", GenerateConvexSourcesTask::class.java) {
            val directory = project.layout.buildDirectory.dir("generated/sources/convex/commonMain/kotlin")
            it.serverDirectory.set(extension.local.server)
            it.buildDirectory.set(directory)

            val localProperties = Properties()
            val localPropertiesFile = project.rootProject.file("local.properties")
            if (localPropertiesFile.exists()) {
                localProperties.load(localPropertiesFile.inputStream())
                localProperties.getProperty("convex.key")?.let { key ->
                    extension.remote.key.set(key)
                }
            }

            it.localCommand.set(extension.local.command)
            it.remoteSource.set(extension.remote)
            it.group = "convex"
        }

        project.afterEvaluate {
            project.plugins.withType(KotlinMultiplatformPluginWrapper::class.java) {
                val kotlinExtension = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
                kotlinExtension.sourceSets.getByName("commonMain").kotlin.srcDir(
                    task.flatMap {
                        it.buildDirectory
                    },
                )
            }

            project.plugins.withType(KotlinPluginWrapper::class.java) {
                val kotlinExtension = project.extensions.getByType(KotlinJvmProjectExtension::class.java)
                kotlinExtension.sourceSets.getByName("main").kotlin.srcDir(
                    task.flatMap {
                        it.buildDirectory
                    },
                )
            }
        }

        project.tasks.withType(KotlinCompileCommon::class.java).all {
            it.dependsOn(task)
        }
    }
}
