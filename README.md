# Convex for Kotlin Multiplatform

A type-safe Kotlin Multiplatform client for [Convex](https://convex.dev) with code generation support.

## Installation

Add to your `gradle/libs.versions.toml`:

```toml
[versions]
convex = "0.0.0"

[libraries]
convex-core = { group = "com.kansson.kmp", name = "kmp-convex-core", version.ref = "convex" }

[plugins]
convex = { id = "com.kansson.kmp.convex", version.ref = "convex" }
```

Then in your root `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.convex) apply false
}
```

And finally configure the plugin in your shared `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.convex)
}

convex {
    url = "https://deployment-name.convex.cloud"
    key = "dev:deployment-name|key"
}
```

Get your deployment `url` and create a deploy `key` in your Convex dashboard settings.

> The plugin uses these to generate type-safe code from your Convex functions at build time.

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
  handler: async (ctx, { status }) => {
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
            override val args: Args
        ) : ConvexFunction.Query<List.Args, List.Output> {
            @Serializable
            data class Args(
                val status: String
            )

            @Serializable
            data class Output(
                val tasks: List<Task>
            ) {
                @Serializable
                data class Task(
                    val title: String,
                    val text: String
                )
            }
        }
    }
}
```

Once you have the generated API, create a client and execute queries, mutations, or actions to get results:

```kotlin
val client = ConvexClient("https://your-deployment.convex.cloud")
val request = Api.Tasks.List { status = "active" }

client.query(request).collect { response ->
    when (response) {
      is ConvexResponse.Success -> println(response.data)
      is ConvexResponse.Failure -> println(response.exception.message)
    }
}
```

Queries return a `Flow` for live updates, while mutations and actions return single responses.

> The Gradle plugin also generates type-safe builders that simplify creating requests as shown in the example above.

## Roadmap

- Enhanced error handling
- Google and Apple authentication with persistence
- Improved code generation architecture
- Environment variable configuration for deploy keys
- Local CLI integration for code generation
