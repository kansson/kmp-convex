@file:OptIn(ExperimentalEncodingApi::class)

package com.kansson.kmp.convex.core.type

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.Long as KotlinLong

internal object LongSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor = Box.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Long) {
        val buffer = Buffer().apply { writeLong(value) }
        val content = Base64.encode(buffer.readByteArray())
        encoder.encodeSerializableValue(
            serializer = Box.serializer(),
            value = Box(content),
        )
    }

    override fun deserialize(decoder: Decoder): Long {
        val value = decoder.decodeSerializableValue(Box.serializer())
        val content = Base64.decode(value.integer)
        val buffer = Buffer().apply { write(content) }

        return buffer.readLong()
    }

    @Serializable
    data class Box(
        @SerialName("\$integer")
        val integer: String,
    )
}

public typealias Long =
    @Serializable(LongSerializer::class)
    KotlinLong
