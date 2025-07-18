# Convex for Kotlin Multiplatform

A type-safe Kotlin Multiplatform client for [Convex](https://convex.dev) with code generation support.

## Installation

Add to your `gradle/libs.versions.toml`:

```toml
[versions]
kotlin = "2.1.21"
serialization = "1.8.1"
convex = "latest" # find under releases

[libraries]
kotlinx-serialization-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-core", version.ref = "serialization" }
convex-core = { group = "com.kansson.kmp", name = "kmp-convex-core", version.ref = "convex" }

[plugins]
serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
convex = { id = "com.kansson.kmp.convex", version.ref = "convex" }
```

Then in your root `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.serialization) apply false
    alias(libs.plugins.convex) apply false
}
```

And finally configure the plugin in your shared `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.serialization)
    alias(libs.plugins.convex)
}

kotlin {
    commonMain.dependencies {
        implementation(libs.kotlinx.serialization.core)
        implementation(libs.convex.core)
    }
}

convex {
    // See setup below
}
```

## Setup

The plugin supports two modes for code generation. Choose the one that best fits your development workflow.

### Local Code Generation

```kotlin
convex {
    local {
        server = file("../path/to/your/convex/project")
    }
}
```

Set `server` to the root directory of your Convex project and optionally override `command` if needed.

> The `command` is the Convex CLI executable like `npm run convex`.


### Remote Code Generation

```kotlin
convex {
    remote {
        url = "https://deployment-name.convex.cloud"
        key = "dev:deployment-name|key"
    }
}
```

Get your deployment `url` and create a deploy `key` in your Convex dashboard settings.

> The code generation is run at build time or manually with the `generateConvexSources` task.

## Usage

The plugin generates an API hierarchy similar to the JavaScript client.

Given a Convex query function like this:

```javascript
// convex/tasks.ts
export const list = query({
  args: {
    status: v.string()
  },
  returns: {
    tasks: v.array(
      v.object({
        title: v.string(),
        text: v.string(),
      }),
    ),
  },
  handler: async (ctx, {status}) => {
    // return tasks...
  }
});
```

> The `returns` validator is required for code generation to work properly.

The plugin generates:

```kotlin
object Api {
    object Tasks {
        data class List(
            override val identifier: String = "tasks.js:list",
            override val args: Args,
        ) : ConvexFunction.Query<List.Args, List.Output> {
            @Serializable
            data class Args(
                val status: String,
            )

            @Serializable
            data class Output(
                val tasks: List<Task>,
            ) {
                @Serializable
                data class Task(
                    val title: String,
                    val text: String,
                )
            }
        }
    }
}
```

Once you have the generated API, create a client and execute queries, mutations, or actions.

```kotlin
val client = ConvexClient("https://your-deployment.convex.cloud")
val request = Api.Tasks.List { status = "active" }

client.query(request).collect { response ->
    when (response) {
        is ConvexResponse.Success -> println(response.data.tasks)
        is ConvexResponse.Failure -> println(response.exception.message)
    }
}
```

> The Gradle plugin also generates type-safe builders that simplify creating requests.

## Early Access

Snapshot versions are available for testing the latest changes from the `main` branch. Configure the snapshot repository in your `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        maven {
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        }
    }

    resolutionStrategy {
        eachPlugin {
            if (requested.id.namespace == "com.kansson.kmp.convex") {
                useModule("com.kansson.kmp:convex-gradle-plugin:${requested.version}")
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        }
    }
}

```

Then use the snapshot version in your `gradle/libs.versions.toml`:

```toml
[versions]
convex = "main-SNAPSHOT"
```

## Roadmap

- Enhanced error handling
- Google and Apple authentication with persistence
- Improved code generation architecture
- Apple watchOS and tvOS support
