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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files
import java.nio.file.Paths

public abstract class GenerateConvexSourcesTask : DefaultTask() {
    @get:InputDirectory
    @get:Optional
    public abstract val serverDirectory: DirectoryProperty

    @get:OutputDirectory
    public abstract val buildDirectory: DirectoryProperty

    @get:Input
    @get:Optional
    public abstract val localCommand: Property<String>

    @get:Input
    @get:Optional
    public abstract val remoteSource: Property<RemoteConvexSource>

    @TaskAction
    public fun generateConvexSources() {
        val functions = when {
            serverDirectory.isPresent -> {
                val root = serverDirectory.asFile.get()
                val packageManager = PackageManager.entries.find {
                    val path = Paths.get(root.path, it.lockFile)
                    Files.exists(path)
                } ?: PackageManager.NPM

                val command = "${localCommand.orNull ?: packageManager.command} function-spec"
                val process = ProcessBuilder(command.split(" "))
                    .directory(root)
                    .redirectErrorStream(true)
                    .start()

                val output = process.inputStream.bufferedReader()
                    .use { it.readText() }

                if (process.waitFor() != 0) {
                    return
                }

                val data = Json.decodeFromString<LocalData>(output)
                data.functions
            }

            remoteSource.isPresent &&
                remoteSource.get().url.isPresent && remoteSource.get().key.isPresent -> runBlocking {
                val httpClient = HttpClient {
                    install(ContentNegotiation) { json() }
                    defaultRequest {
                        url(remoteSource.get().url.get())
                    }
                }

                val request = buildJsonObject {
                    put("path", "_system/cli/modules:apiSpec")
                    putJsonObject("args") {}
                }

                val response = httpClient.post("/api/query") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                    headers {
                        append(HttpHeaders.Authorization, "Convex ${remoteSource.get().key.get()}")
                    }
                }

                val data = response.body<RemoteData>()
                when (data.status) {
                    RemoteData.Status.Success -> data.value
                    RemoteData.Status.Error -> emptyList()
                }
            }

            else -> emptyList()
        }

        if (functions.isEmpty()) {
            return
        }

        val directory = buildDirectory.get().asFile
        CodeGenerator
            .run(functions)
            .also { it.writeTo(directory) }
    }
}

private enum class PackageManager(
    val lockFile: String,
    val command: String,
) {
    NPM("package-lock.json", "npm run convex"),
    PNPM("pnpm-lock.yaml", "pnpm convex"),
    YARN("yarn.lock", "yarn convex"),
    BUN_BINARY("bun.lockb", "bun convex"),
    BUN_TEXT("bun.lock", "bun convex"),
}

@Serializable
internal data class LocalData(
    val url: String,
    val functions: List<ConvexFunction>,
)

@Serializable
internal class RemoteData(
    val status: Status,
    val value: List<ConvexFunction>,
) {
    @Serializable
    enum class Status {
        @SerialName("success")
        Success,

        @SerialName("error")
        Error,
    }
}
