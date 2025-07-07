package com.kansson.kmp.convex.core

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

public sealed class ConvexResponse<Output> {
    @ConsistentCopyVisibility
    public data class Success<Output> @PublishedApi internal constructor(
        val data: Output,
    ) : ConvexResponse<Output>()

    @ConsistentCopyVisibility
    public data class Failure<Output> @PublishedApi internal constructor(
        val message: String,
        val data: Data?,
    ) : ConvexResponse<Output>() {
        @ConsistentCopyVisibility
        public data class Data @PublishedApi internal constructor(
            val json: String,
        ) {
            public inline fun <reified T> decode(): T = Json.decodeFromString<T>(json)
            public inline fun <reified T> tryDecode(): T? = try {
                decode()
            } catch (_: SerializationException) {
                null
            }
        }
    }
}
