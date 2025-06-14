package com.kansson.kmp.convex.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileCommon

public class ConvexPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("convex", ConvexExtension::class.java)
        val task = project.tasks.register("generateConvexSources", GenerateConvexSourcesTask::class.java) {
            it.group = "convex"
            it.extension.set(extension)

            val output = project.layout.buildDirectory.dir("generated/sources/convex/commonMain/kotlin")
            it.output.set(output)
        }

        project.afterEvaluate {
            project.plugins.withType(KotlinMultiplatformPluginWrapper::class.java) {
                val kotlinExtension = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
                kotlinExtension.sourceSets.getByName("commonMain").kotlin.srcDir(
                    task.flatMap {
                        it.output
                    },
                )
            }
        }

        project.tasks.withType(KotlinCompileCommon::class.java).all {
            it.dependsOn(task)
        }
    }
}
