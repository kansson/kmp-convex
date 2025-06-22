package com.kansson.kmp.convex.core

public sealed interface ConvexFunction<Args, Output> {
    public val identifier: String
    public val args: Args

    public interface Query<Args, Output> : ConvexFunction<Args, Output>
    public interface Mutation<Args, Output> : ConvexFunction<Args, Output>
    public interface Action<Args, Output> : ConvexFunction<Args, Output>
}
