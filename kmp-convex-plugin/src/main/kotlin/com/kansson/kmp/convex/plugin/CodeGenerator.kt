package com.kansson.kmp.convex.plugin

import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.LambdaTypeName
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
    @Suppress("LongMethod")
    fun run(functions: List<ConvexFunction>): FileSpec {
        val spec = FileSpec.builder("com.kansson.kmp.convex.generated", "Api")

        val hierarchy = mutableMapOf<String, Pair<TypeSpec.Builder, TypeSpec.Builder>>()
        val root = TypeSpec.objectBuilder("Api")

        functions
            .filter { it.visibility.kind == ConvexFunction.Visibility.Kind.Public }
            .forEach { function ->
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
                        val type =
                            ClassName("com.kansson.kmp.convex.core", "ConvexFunction", function.functionType.name)

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

                        if (function.args !is ConvexFunction.Type.Any) {
                            val companion = companionObject(part, args = true)
                            spec.addType(companion)
                        }

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

    private fun ConvexFunction.identifierData(): Pair<ParameterSpec, PropertySpec> {
        val parameter = ParameterSpec.builder("identifier", String::class)
            .defaultValue("%S", identifier)
        val property = PropertySpec.builder("identifier", String::class)
            .addModifiers(KModifier.OVERRIDE)
            .initializer("identifier")

        return parameter.build() to property.build()
    }

    private fun ConvexFunction.Type.typeData(name: String): Pair<TypeName, TypeSpec?> = when (this) {
        is ConvexFunction.Type.Id -> STRING to null
        ConvexFunction.Type.Null -> UNIT.copy(nullable = true) to null
        ConvexFunction.Type.Int64 -> ClassName("com.kansson.kmp.convex.core.type", "Long") to null
        ConvexFunction.Type.Float64 -> DOUBLE to null
        ConvexFunction.Type.Bool -> BOOLEAN to null
        ConvexFunction.Type.String -> STRING to null
        ConvexFunction.Type.Bytes -> ClassName("com.kansson.kmp.convex.core.type", "ByteArray") to null
        is ConvexFunction.Type.Array -> {
            val data = value.typeData(name)
            LIST.parameterizedBy(data.first) to data.second
        }
        is ConvexFunction.Type.Object -> {
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

            if (name != "Output") {
                val builder = builderTypeSpec(value, name)
                spec.addType(builder)

                val companion = companionObject(name, args = false)
                spec.addType(companion)
            }

            spec.primaryConstructor(constructor.build())
            ClassName("", name) to spec.build()
        }
        is ConvexFunction.Type.Record -> {
            val data = values.fieldType.typeData(name)
            MAP.parameterizedBy(STRING, data.first) to data.second
        }
        is ConvexFunction.Type.Union -> UNIT to null
        is ConvexFunction.Type.Literal -> STRING to null
        ConvexFunction.Type.Any -> UNIT to null
    }

    private fun builderTypeSpec(
        fields: Map<String, ConvexFunction.Type.Object.Field>,
        name: String,
    ): TypeSpec {
        val spec = TypeSpec.classBuilder("Builder")
            .addAnnotation(ClassName("com.kansson.kmp.convex.core", "ConvexDsl"))

        val function = FunSpec.builder("build")
            .returns(ClassName("", name))

        val arguments = mutableListOf<String>()

        fields.forEach { (key, field) ->
            val baseType = if (field.fieldType is ConvexFunction.Type.Object) {
                LambdaTypeName.get(
                    receiver = ClassName("", key.replaceFirstChar { it.uppercase() }, "Builder"),
                    returnType = UNIT,
                )
            } else {
                val data = field.fieldType.typeData(key.replaceFirstChar { it.uppercase() })
                data.first
            }

            val builderPropertyType = if (field.optional) {
                baseType.copy(nullable = true)
            } else {
                baseType
            }

            val defaultValue = when {
                field.optional -> CodeBlock.of("null")
                else -> field.fieldType.defaultCodeBlock()
            }

            val property = PropertySpec.builder(key, builderPropertyType)
                .mutable(true)
                .initializer(defaultValue)
            spec.addProperty(property.build())

            val argument = when {
                field.fieldType is ConvexFunction.Type.Object && field.optional -> {
                    "$key = $key?.let { ${key.replaceFirstChar { it.uppercase() }}.Builder().apply(it).build() }"
                }
                field.fieldType is ConvexFunction.Type.Object -> {
                    "$key = ${key.replaceFirstChar { it.uppercase() }}.Builder().apply($key).build()"
                }
                else -> "$key = $key"
            }
            arguments.add(argument)
        }

        function.addCode(CodeBlock.of("return %T(%L)", ClassName("", name), arguments.joinToString(", ")))
        spec.addFunction(function.build())
        return spec.build()
    }

    private fun companionObject(name: String, args: Boolean): TypeSpec {
        val spec = TypeSpec.companionObjectBuilder()
        val invoke = FunSpec.builder("invoke")
            .addModifiers(KModifier.OPERATOR)
            .returns(ClassName("", name))

        val type = if (args) {
            invoke.addCode("return %T(args = Args.Builder().apply(block).build())", ClassName("", name))
            LambdaTypeName.get(
                receiver = ClassName("", "Args", "Builder"),
                returnType = UNIT,
            )
        } else {
            invoke.addCode("return Builder().apply(block).build()")
            LambdaTypeName.get(
                receiver = ClassName("", "Builder"),
                returnType = UNIT,
            )
        }

        invoke.addParameter("block", type)
        spec.addFunction(invoke.build())
        return spec.build()
    }

    private fun ConvexFunction.Type.defaultCodeBlock(): CodeBlock = when (this) {
        is ConvexFunction.Type.Id -> CodeBlock.of("%S", "")
        ConvexFunction.Type.String -> CodeBlock.of("%S", "")
        is ConvexFunction.Type.Literal -> CodeBlock.of("%S", "")
        ConvexFunction.Type.Int64 -> CodeBlock.of("%L", 0L)
        ConvexFunction.Type.Float64 -> CodeBlock.of("%L", 0.0)
        ConvexFunction.Type.Bool -> CodeBlock.of("false")
        ConvexFunction.Type.Bytes -> CodeBlock.of("byteArrayOf()")
        is ConvexFunction.Type.Array -> CodeBlock.of("emptyList()")
        is ConvexFunction.Type.Record -> CodeBlock.of("emptyMap()")
        is ConvexFunction.Type.Object -> CodeBlock.of("{}")
        is ConvexFunction.Type.Union -> CodeBlock.of("%L", Unit)
        ConvexFunction.Type.Null -> CodeBlock.of("%L", Unit)
        ConvexFunction.Type.Any -> CodeBlock.of("%L", Unit)
    }
}
