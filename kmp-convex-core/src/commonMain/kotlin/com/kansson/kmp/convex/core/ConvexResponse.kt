package com.kansson.kmp.convex.core

public sealed class ConvexResponse<Output> {
    @ConsistentCopyVisibility
    public data class Success<Output> @PublishedApi internal constructor(
        val data: Output,
    ) : ConvexResponse<Output>()

    @ConsistentCopyVisibility
    public data class Failure<Output> @PublishedApi internal constructor(
        val exception: Exception,
    ) : ConvexResponse<Output>()
}
