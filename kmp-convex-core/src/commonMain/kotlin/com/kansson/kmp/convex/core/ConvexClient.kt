package com.kansson.kmp.convex.core

import com.kansson.kmp.convex.bindings.ClientException
import com.kansson.kmp.convex.bindings.MobileConvexClient
import com.kansson.kmp.convex.bindings.MobileConvexClientInterface
import com.kansson.kmp.convex.bindings.QuerySubscriber
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json as KotlinJson

@PublishedApi
internal val Json: KotlinJson = KotlinJson {
    ignoreUnknownKeys = true
    explicitNulls = false
    allowSpecialFloatingPointValues = true
}

public open class ConvexClient(
    url: String,
    factory: (url: String, clientId: String) -> MobileConvexClientInterface = ::MobileConvexClient,
) {
    @PublishedApi
    internal val ffi: MobileConvexClientInterface =
        factory(url, "kmp-unspecified")

    @Throws(ConvexException::class, CancellationException::class)
    public inline fun <reified Args, reified Output> query(
        function: ConvexFunction.Query<Args, Output>,
    ): Flow<ConvexResponse<Output>> = callbackFlow {
        val args = try {
            Json.encodeToJsonElement(function.args)
                .jsonObject
                .mapValues { it.value.toString() }
        } catch (exception: SerializationException) {
            return@callbackFlow cancel(
                message = "Failed to encode function arguments.",
                cause = exception,
            )
        }

        val subscription = ffi.subscribe(
            name = function.identifier,
            args = args,
            subscriber = object : QuerySubscriber {
                override fun onUpdate(value: String) {
                    try {
                        val data = Json.decodeFromString<Output>(value)
                        trySend(ConvexResponse.Success(data))
                    } catch (exception: SerializationException) {
                        cancel(
                            message = "Failed to decode function update data.",
                            cause = exception,
                        )
                    }
                }

                override fun onError(message: String, value: String?) {
                    val response = ConvexResponse.Failure<Output>(
                        message = message,
                        data = value?.let {
                            ConvexResponse.Failure.Data(json = it)
                        },
                    )
                    trySend(response)
                }
            },
        )

        awaitClose {
            subscription.cancel()
        }
    }

    @Throws(ConvexException::class, CancellationException::class)
    public suspend inline fun <reified Args, reified Output> mutation(
        function: ConvexFunction.Mutation<Args, Output>,
    ): ConvexResponse<Output> = call(function) { args ->
        ffi.mutation(
            name = function.identifier,
            args = args,
        )
    }

    @Throws(ConvexException::class, CancellationException::class)
    public suspend inline fun <reified Args, reified Output> action(
        function: ConvexFunction.Action<Args, Output>,
    ): ConvexResponse<Output> = call(function) { args ->
        ffi.action(
            name = function.identifier,
            args = args,
        )
    }

    @PublishedApi
    internal inline fun <reified Args, reified Output> call(
        function: ConvexFunction<Args, Output>,
        block: (args: Map<String, String>) -> String,
    ): ConvexResponse<Output> = try {
        val args = Json.encodeToJsonElement(function.args)
            .jsonObject
            .mapValues { it.value.toString() }

        val output = block(args)
        val data = Json.decodeFromString<Output>(output)
        ConvexResponse.Success(data)
    } catch (expected: Exception) {
        when (expected) {
            is SerializationException -> throw ConvexException(
                message = "Failed to serialize function data.",
                cause = expected,
            )
            is ClientException -> when (expected) {
                is ClientException.ConvexException -> ConvexResponse.Failure(
                    message = expected.message,
                    data = ConvexResponse.Failure.Data(json = expected.data),
                )
                is ClientException.ServerException -> ConvexResponse.Failure(
                    message = expected.message,
                    data = null,
                )
                is ClientException.InternalException -> throw ConvexException(
                    message = expected.message,
                    cause = expected,
                )
            }
            else -> throw expected
        }
    }

    public suspend fun setAuth(token: String): Unit = ffi.setAuth(token)
}
