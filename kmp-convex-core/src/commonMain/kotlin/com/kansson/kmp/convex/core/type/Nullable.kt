@file:OptIn(ExperimentalSerializationApi::class)

package com.kansson.kmp.convex.core.type

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.jvm.JvmInline

@JvmInline
@Serializable(with = NullableSerializer::class)
public value class Nullable<T>(public val value: T?)

public class NullableSerializer<T>(
    private val innerSerializer: KSerializer<T>,
) : KSerializer<Nullable<T>> {
    override val descriptor: SerialDescriptor = innerSerializer.descriptor.nullable

    override fun serialize(encoder: Encoder, value: Nullable<T>) {
        if (value.value == null) {
            encoder.encodeNull()
        } else {
            encoder.encodeSerializableValue(innerSerializer, value.value)
        }
    }

    override fun deserialize(decoder: Decoder): Nullable<T> {
        val value = decoder.decodeNullableSerializableValue(innerSerializer)
        return Nullable(value)
    }
}
