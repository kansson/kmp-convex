@file:OptIn(ExperimentalSerializationApi::class)

package com.kansson.kmp.convex.plugin

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
internal data class ConvexFunction(
    val args: Type,
    val functionType: FunctionType,
    val identifier: String,
    val returns: Type,
    val visibility: Visibility,
) {
    @Serializable
    @JsonClassDiscriminator("type")
    sealed interface Type {
        @Serializable
        @SerialName("id")
        data class Id(
            val tableName: kotlin.String,
        ) : Type

        @Serializable
        @SerialName("null")
        data object Null : Type

        @Serializable
        @SerialName("bigint")
        data object Int64 : Type

        @Serializable
        @SerialName("number")
        data object Float64 : Type

        @Serializable
        @SerialName("boolean")
        data object Bool : Type

        @Serializable
        @SerialName("string")
        data object String : Type

        @Serializable
        @SerialName("bytes")
        data object Bytes : Type

        @Serializable
        @SerialName("array")
        data class Array(
            val value: Type,
        ) : Type

        @Serializable
        @SerialName("object")
        data class Object(
            val value: Map<kotlin.String, Field>,
        ) : Type {
            @Serializable
            data class Field(
                val fieldType: Type,
                val optional: Boolean,
            )
        }

        @Serializable
        @SerialName("record")
        data class Record(
            val keys: String,
            val values: Object.Field,
        ) : Type

        @Serializable
        @SerialName("union")
        data class Union(
            val value: List<Type>,
        ) : Type

        @Serializable
        @SerialName("literal")
        data class Literal(
            val value: kotlin.String,
        ) : Type

        @Serializable
        @SerialName("any")
        data object Any : Type
    }

    @Serializable
    enum class FunctionType {
        Query,
        Mutation,
        Action,
    }

    @Serializable
    data class Visibility(
        val kind: Kind,
    ) {
        @Serializable
        enum class Kind {
            @SerialName("public")
            Public,

            @SerialName("internal")
            Internal,
        }
    }
}
