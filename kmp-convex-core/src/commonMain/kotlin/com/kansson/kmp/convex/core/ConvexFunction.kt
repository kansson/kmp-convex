package com.kansson.kmp.convex.core

sealed interface ConvexFunction<Args, Output> {
    val identifier: String
    val args: Args

    interface Query<Args, Output> : ConvexFunction<Args, Output>
    interface Mutation<Args, Output> : ConvexFunction<Args, Output>
    interface Action<Args, Output> : ConvexFunction<Args, Output>
}
