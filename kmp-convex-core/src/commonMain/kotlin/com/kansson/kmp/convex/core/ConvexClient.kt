package com.kansson.kmp.convex.core

import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
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

    public inline fun <reified Args, reified Output> query(
        function: ConvexFunction.Query<Args, Output>,
    ): Flow<ConvexResponse<Output>> = callbackFlow {
        val args = Json.encodeToJsonElement(function.args)
            .jsonObject
            .mapValues { it.value.toString() }

        val subscription = ffi.subscribe(
            name = function.identifier,
            args = args,
            subscriber = object : QuerySubscriber {
                override fun onUpdate(value: String) {
                    try {
                        val data = Json.decodeFromString<Output>(value)
                        val response = ConvexResponse.Success(data)
                        trySend(response)
                    } catch (@Suppress("TooGenericExceptionCaught") exception: Throwable) {
                        cancel("error handling data from ffi", exception)
                    }
                }

                override fun onError(message: String, value: String?) {
                    val exception = when (value) {
                        null -> ServerError(message)
                        else -> ConvexError(message, value)
                    }
                    val response = ConvexResponse.Failure<Output>(exception)
                    trySend(response)
                }
            },
        )

        awaitClose {
            subscription.cancel()
        }
    }

    public suspend inline fun <reified Args, reified Output> mutation(
        function: ConvexFunction.Mutation<Args, Output>,
    ): ConvexResponse<Output> {
        val args = Json.encodeToJsonElement(function.args)
            .jsonObject
            .mapValues { it.value.toString() }

        try {
            val output = ffi.mutation(
                name = function.identifier,
                args = args,
            )

            return try {
                val data = Json.decodeFromString<Output>(output)
                ConvexResponse.Success(data)
            } catch (exception: SerializationException) {
                ConvexResponse.Failure(exception)
            }
        } catch (exception: ClientException) {
            throw exception.toError()
        }
    }

    public suspend inline fun <reified Args, reified Output> action(
        function: ConvexFunction.Action<Args, Output>,
    ): ConvexResponse<Output> {
        val args = Json.encodeToJsonElement(function.args)
            .jsonObject
            .mapValues { it.value.toString() }

        try {
            val output = ffi.action(
                name = function.identifier,
                args = args,
            )

            return try {
                val data = Json.decodeFromString<Output>(output)
                ConvexResponse.Success(data)
            } catch (exception: SerializationException) {
                ConvexResponse.Failure(exception)
            }
        } catch (exception: ClientException) {
            throw exception.toError()
        }
    }

    public suspend fun setAuth(token: String): Unit = ffi.setAuth(token)
}
