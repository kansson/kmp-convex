package com.kansson.kmp.convex.plugin

import org.gradle.api.Action
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

public abstract class ConvexExtension @Inject constructor(
    objects: ObjectFactory,
) {
    internal val local = objects.newInstance(LocalConvexSource::class.java)
    internal val remote = objects.newInstance(RemoteConvexSource::class.java)

    public fun local(action: Action<LocalConvexSource>) {
        action.execute(local)
    }

    public fun remote(action: Action<RemoteConvexSource>) {
        action.execute(remote)
    }
}

public abstract class LocalConvexSource {
    public abstract val server: DirectoryProperty
    public abstract val command: Property<String>
}

public abstract class RemoteConvexSource {
    public abstract val url: Property<String>
    public abstract val key: Property<String>
}
