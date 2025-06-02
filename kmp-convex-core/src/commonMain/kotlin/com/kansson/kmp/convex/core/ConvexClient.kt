package com.kansson.kmp.convex.core

import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlin.jvm.JvmName
import kotlinx.serialization.json.Json as KotlinJson

@PublishedApi
internal val Json = KotlinJson {
    ignoreUnknownKeys = true
    allowSpecialFloatingPointValues = true
    // This allows Int, Long, Float or Double values anywhere to be annotated with @ConvexNum which
    // knows the special JSON format that Convex uses for those values.
    serializersModule =
        SerializersModule {
            contextual(Int64ToIntDecoder)
            contextual(Int64ToLongDecoder)
            contextual(Float64ToFloatDecoder)
            contextual(Float64ToDoubleDecoder)
        }
}

typealias Int64 =
    @Serializable(Int64ToLongDecoder::class)
    Long
typealias Float64 =
    @Serializable(Float64ToDoubleDecoder::class)
    Double
typealias Int32 =
    @Serializable(Int64ToIntDecoder::class)
    Int
typealias Float32 =
    @Serializable(Float64ToFloatDecoder::class)
    Float
typealias ConvexNum = Contextual

/**
 * A client API for interacting with a Convex backend.
 *
 * Handles marshalling of data between calling code and the [convex-mobile]() and
 * [convex-rs](https://github.com/get-convex/convex-rs) native libraries.
 *
 * Consumers of this client should use Kotlin's JSON serialization to handle data sent to/from the
 * Convex backend.
 */
open class ConvexClient(
    deploymentUrl: String,
    ffiClientFactory: (deploymentUrl: String, clientId: String) -> MobileConvexClientInterface = ::MobileConvexClient,
) {

    @PublishedApi
    internal val ffiClient =
        ffiClientFactory(deploymentUrl, "android-todo") // TODO client id

    /**
     * Subscribes to the query with the given [name] and converts data from the subscription into a
     * [Flow] of [Result]s containing [T].
     *
     * If there is an error generating [T], the [Result] will contain either a [ConvexError] (for
     * application specific errors) or a [ServerError].
     *
     * The upstream Convex subscription will be canceled if whatever is subscribed to the [Flow]
     * stops listening.
     *
     * @param T result data type that will be decoded from JSON
     */
    inline fun <reified T> subscribe(
        name: String,
        args: Map<String, Any?>? = null,
    ): Flow<Result<T>> = callbackFlow {
        val subscription = ffiClient.subscribe(
            name,
            args?.mapValues { it.value.toJsonElement().toString() } ?: mapOf(),
            object : QuerySubscriber {
                override fun onUpdate(value: String) {
                    try {
                        val data = Json.decodeFromString<T>(value)
                        trySend(Result.success(data))
                    } catch (e: Throwable) {
                        // Don't catch when https://github.com/mozilla/uniffi-rs/issues/2194 is fixed.
                        // Ideally any unchecked exception that happens here goes uncaught and triggers
                        // a crash, as it's likely a developer error related to mishandling the JSON
                        // data.
                        val message = "error handling data from FFI"
                        cancel(message, e)
                    }
                }

                override fun onError(message: String, value: String?) {
                    if (value == null) {
                        // This is a server error of some sort.
                        trySend(Result.failure(ServerError(message)))
                    } else {
                        // An application specific error thrown in a Convex backend function.
                        trySend(Result.failure(ConvexError(message, value)))
                    }
                }
            },
        )

        awaitClose {
            subscription.cancel()
        }
    }

    /**
     * Executes the action with the given [name] and [args] and returns the result.
     *
     * The [args] should be a map of [String] to any data that can be serialized to JSON.
     *
     * For actions that don't return a value, prefer calling the version of this method that doesn't
     * require a return type parameter.
     *
     * @param T data type that will be decoded from JSON and returned
     */
    suspend inline fun <reified T> action(name: String, args: Map<String, Any?>? = null): T {
        try {
            val jsonData = ffiClient.action(
                name,
                args?.mapValues { it.value.toJsonElement().toString() } ?: mapOf(),
            )
            try {
                return Json.decodeFromString<T>(jsonData)
            } catch (e: Throwable) {
                throw InternalError(
                    "Failed to decode JSON, ensure you're using types compatible with Convex in your return value",
                    e,
                )
            }
        } catch (e: ClientException) {
            throw e.toError()
        }
    }

    /**
     * Executes the action with the given [name] and [args] and returns the result.
     *
     * The [args] should be a map of [String] to any data that can be serialized to JSON.
     *
     * This version requires that the remote action returns null or no value. If you wish to
     * return a value from an action, use the version of the method that allows specifying the
     * return type.
     */
    @JvmName("voidAction")
    suspend fun action(name: String, args: Map<String, Any?>? = null) {
        action<Unit?>(name = name, args = args)
    }

    /**
     * Executes the mutation with the given [name] and [args] and returns the result.
     *
     * The [args] should be a map of [String] to any data that can be serialized to JSON.
     *
     * For actions that don't return a value, prefer calling the version of this method that doesn't
     * require a return type parameter.
     *
     * @param T data type that will be decoded from JSON and returned
     */
    suspend inline fun <reified T> mutation(name: String, args: Map<String, Any?>? = null): T {
        try {
            val jsonData = ffiClient.mutation(
                name,
                args?.mapValues { it.value.toJsonElement().toString() } ?: mapOf(),
            )
            try {
                return Json.decodeFromString<T>(jsonData)
            } catch (e: Throwable) {
                throw InternalError(
                    "Failed to decode JSON, ensure you're using types compatible with Convex in your return value",
                    e,
                )
            }
        } catch (e: ClientException) {
            throw e.toError()
        }
    }

    /**
     * Executes the mutation with the given [name] and [args].
     *
     * The [args] should be a map of [String] to any data that can be serialized to JSON.
     *
     * This version requires that the remote mutation returns null or no value. If you wish to
     * return a value from a mutation, use the version of the method that allows specifying the
     * return type.
     */
    @JvmName("voidMutation")
    suspend fun mutation(name: String, args: Map<String, Any?>? = null) {
        mutation<Unit?>(name = name, args = args)
    }
}
