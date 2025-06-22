package com.kansson.kmp.convex.plugin

import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import kotlinx.serialization.Serializable

internal object CodeGenerator {
    fun run(functions: List<RemoteResponse.Function>): FileSpec {
        val spec = FileSpec.builder("com.kansson.kmp.convex.generated", "Api")

        val hierarchy = mutableMapOf<String, Pair<TypeSpec.Builder, TypeSpec.Builder>>()
        val root = TypeSpec.objectBuilder("Api")

        functions.forEach { function ->
            val paths = function.identifier
                .replace(".js", "")
                .split("/", ":")
                .map { part ->
                    part.replaceFirstChar { it.uppercase() }
                }

            paths.forEachIndexed { index, part ->
                val path = paths.take(index + 1).joinToString(".")
                val parent = hierarchy[paths.take(index).joinToString(".")]?.second ?: root

                if (index == paths.size - 1) {
                    val spec = TypeSpec.classBuilder(part)
                        .addModifiers(KModifier.DATA)

                    val constructor = FunSpec.constructorBuilder()
                    val type = ClassName("com.kansson.kmp.convex.core", "ConvexFunction", function.functionType.name)

                    val identifier = function.identifierData()
                    constructor.addParameter(identifier.first)
                    spec.addProperty(identifier.second)

                    val args = function.args.typeData("Args")
                    val first = args.second?.let {
                        spec.addType(it)
                        ClassName("", part, "Args")
                    } ?: args.first

                    constructor.addParameter("args", args.first)
                    val property = PropertySpec.builder("args", args.first)
                        .addModifiers(KModifier.OVERRIDE)
                        .initializer("args")
                    spec.addProperty(property.build())

                    val output = function.returns.typeData("Output")
                    val second = output.second?.let {
                        spec.addType(it)
                        ClassName("", part, "Output")
                    } ?: output.first

                    spec.addSuperinterface(type.parameterizedBy(first, second))
                    spec.primaryConstructor(constructor.build())
                    parent.addType(spec.build())
                } else if (!hierarchy.containsKey(path)) {
                    val builder = TypeSpec.classBuilder(part)
                    hierarchy[path] = parent to builder
                }
            }
        }

        hierarchy.entries
            .sortedByDescending { it.key.split(".").size }
            .forEach {
                val (parent, builder) = it.value
                parent.addType(builder.build())
            }

        spec.addType(root.build())
        return spec.build()
    }

    private fun RemoteResponse.Function.identifierData(): Pair<ParameterSpec, PropertySpec> {
        val parameter = ParameterSpec.builder("identifier", String::class)
            .defaultValue("%S", identifier)
        val property = PropertySpec.builder("identifier", String::class)
            .addModifiers(KModifier.OVERRIDE)
            .initializer("identifier")

        return parameter.build() to property.build()
    }

    private fun RemoteResponse.Function.Type.typeData(name: String): Pair<TypeName, TypeSpec?> = when (this) {
        is RemoteResponse.Function.Type.Id -> STRING to null
        RemoteResponse.Function.Type.Null -> UNIT.copy(nullable = true) to null
        RemoteResponse.Function.Type.Int64 -> ClassName("com.kansson.kmp.convex.core.type", "Long") to null
        RemoteResponse.Function.Type.Float64 -> DOUBLE to null
        RemoteResponse.Function.Type.Bool -> BOOLEAN to null
        RemoteResponse.Function.Type.String -> STRING to null
        RemoteResponse.Function.Type.Bytes -> ClassName("com.kansson.kmp.convex.core.type", "ByteArray") to null
        is RemoteResponse.Function.Type.Array -> {
            val data = value.typeData(name)
            LIST.parameterizedBy(data.first) to data.second
        }
        is RemoteResponse.Function.Type.Object -> {
            val spec = TypeSpec.classBuilder(name)
                .addModifiers(KModifier.DATA)
                .addAnnotation(Serializable::class)

            val constructor = FunSpec.constructorBuilder()
            value.forEach { (key, field) ->
                val data = field.fieldType.typeData(key.replaceFirstChar { it.uppercase() })
                val type = data.first.copy(nullable = data.first.isNullable || field.optional)

                data.second?.let { spec.addType(it) }
                constructor.addParameter(key, type)

                val property = PropertySpec.builder(key, type)
                    .initializer(key)
                spec.addProperty(property.build())
            }

            spec.primaryConstructor(constructor.build())
            ClassName("", name) to spec.build()
        }
        is RemoteResponse.Function.Type.Record -> {
            val data = values.fieldType.typeData(name)
            MAP.parameterizedBy(STRING, data.first) to data.second
        }
        is RemoteResponse.Function.Type.Union -> UNIT to null
        is RemoteResponse.Function.Type.Literal -> STRING to null
        RemoteResponse.Function.Type.Any -> UNIT to null
    }
}
