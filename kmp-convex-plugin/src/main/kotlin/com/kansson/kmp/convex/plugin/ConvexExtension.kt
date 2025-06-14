package com.kansson.kmp.convex.plugin

import org.gradle.api.provider.Property

public abstract class ConvexExtension {
    public abstract val url: Property<String>
    public abstract val key: Property<String>
}
