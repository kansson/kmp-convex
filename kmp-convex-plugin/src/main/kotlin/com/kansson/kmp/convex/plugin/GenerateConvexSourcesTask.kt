@file:OptIn(ExperimentalSerializationApi::class)

package com.kansson.kmp.convex.plugin

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonObject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

public abstract class GenerateConvexSourcesTask : DefaultTask() {
    @get:Input
    public abstract val extension: Property<ConvexExtension>

    @get:OutputDirectory
    public abstract val output: DirectoryProperty

    @TaskAction
    public fun generateConvexSources() {
        val extension = this.extension.get()
        val httpClient = HttpClient {
            defaultRequest {
                url(extension.url.get())
            }
            install(ContentNegotiation) {
                json()
            }
        }

        val data = runBlocking {
            val request = RemoteRequest(
                path = "_system/cli/modules:apiSpec",
                args = JsonObject(emptyMap()),
            )
            val response = httpClient.post("/api/query") {
                contentType(ContentType.Application.Json)
                setBody(request)
                headers {
                    append(HttpHeaders.Authorization, "Convex ${extension.key.get()}")
                }
            }

            response.body<RemoteResponse>()
        }
    }
}
