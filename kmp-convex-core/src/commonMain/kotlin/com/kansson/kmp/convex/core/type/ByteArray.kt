@file:OptIn(ExperimentalEncodingApi::class)

package com.kansson.kmp.convex.core.type

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.ByteArray as KotlinByteArray

internal object ByteArraySerializer : KSerializer<ByteArray> {
    override val descriptor: SerialDescriptor = Box.serializer().descriptor

    override fun serialize(encoder: Encoder, value: ByteArray) {
        val content = Base64.encode(value)
        encoder.encodeSerializableValue(
            serializer = Box.serializer(),
            value = Box(content),
        )
    }

    override fun deserialize(decoder: Decoder): ByteArray {
        val value = decoder.decodeSerializableValue(Box.serializer())
        return Base64.decode(value.bytes)
    }

    @Serializable
    data class Box(
        @SerialName("\$bytes")
        val bytes: String,
    )
}

public typealias ByteArray =
    @Serializable(ByteArraySerializer::class)
    KotlinByteArray
