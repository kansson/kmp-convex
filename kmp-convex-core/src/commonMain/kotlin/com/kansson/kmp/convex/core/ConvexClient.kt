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
internal val Json = KotlinJson {
    ignoreUnknownKeys = true
    explicitNulls = false
    allowSpecialFloatingPointValues = true
}

class ConvexClient(
    deploymentUrl: String,
    ffiClientFactory: (deploymentUrl: String, clientId: String) -> MobileConvexClientInterface = ::MobileConvexClient,
) {

    @PublishedApi
    internal val ffiClient =
        ffiClientFactory(deploymentUrl, "android-todo") // TODO client id

    inline fun <reified Args, reified Output> query(
        function: ConvexFunction.Query<Args, Output>,
    ): Flow<ConvexResponse<Output>> = callbackFlow {
        val args = Json.encodeToJsonElement(function.args).jsonObject.mapValues {
            it.value.toString()
        }

        val subscription = ffiClient.subscribe(
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

    suspend inline fun <Args, reified Output> mutation(
        function: ConvexFunction.Mutation<Args, Output>,
    ): ConvexResponse<Output> {
        try {
            val output = ffiClient.mutation(
                name = function.identifier,
                args = mapOf(),
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

    suspend inline fun <Args, reified Output> action(
        function: ConvexFunction.Action<Args, Output>,
    ): ConvexResponse<Output> {
        try {
            val output = ffiClient.action(
                name = function.identifier,
                args = mapOf(),
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
}
