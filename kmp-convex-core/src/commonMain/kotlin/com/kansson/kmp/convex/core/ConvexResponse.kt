package com.kansson.kmp.convex.core

sealed class ConvexResponse<Output> {
    @ConsistentCopyVisibility
    data class Success<Output> @PublishedApi internal constructor(
        val data: Output,
    ) : ConvexResponse<Output>()

    @ConsistentCopyVisibility
    data class Failure<Output> @PublishedApi internal constructor(
        val exception: Exception,
    ) : ConvexResponse<Output>()
}
