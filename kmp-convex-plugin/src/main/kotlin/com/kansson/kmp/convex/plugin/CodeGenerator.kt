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
import com.squareup.kotlinpoet.asTypeName
import kotlinx.serialization.Serializable

private data class TypeInfo(
    val typeName: TypeName,
    val typeSpec: TypeSpec? = null,
)

private data class HierarchyNode(
    val parent: TypeSpec.Builder,
    val builder: TypeSpec.Builder,
)

internal object CodeGenerator {
    fun run(functions: List<ConvexFunction>): FileSpec {
        val hierarchy = mutableMapOf<String, HierarchyNode>()
        val root = TypeSpec.objectBuilder("Api")

        functions
            .filter { it.visibility.kind == ConvexFunction.Visibility.Kind.Public }
            .forEach { function ->
                val paths = function.identifier
                    .replace(".js", "")
                    .split("/", ":")
                    .map { it.replaceFirstChar { char -> char.uppercase() } }

                paths.forEachIndexed { index, part ->
                    val currentPath = paths.take(index + 1).joinToString(".")
                    val parentPath = paths.take(index).joinToString(".")
                    val parent = hierarchy[parentPath]?.builder ?: root

                    when {
                        index == paths.size - 1 -> parent.addType(buildFunctionClass(part, function))
                        !hierarchy.containsKey(currentPath) -> hierarchy[currentPath] = HierarchyNode(
                            parent = parent,
                            builder = TypeSpec.classBuilder(part),
                        )
                    }
                }
            }

        hierarchy.entries
            .sortedByDescending { it.key.count { char -> char == '.' } }
            .forEach { (_, node) ->
                node.parent.addType(node.builder.build())
            }

        return FileSpec.builder("com.kansson.kmp.convex.generated", "Api")
            .addType(root.build())
            .build()
    }

    private fun buildFunctionClass(name: String, function: ConvexFunction): TypeSpec {
        val constructor = FunSpec.constructorBuilder()
        constructor.addParameter(
            ParameterSpec.builder("identifier", STRING)
                .defaultValue("%S", function.identifier)
                .build(),
        )

        val args = generateTypeInfo(function.args, "Args")
        val argsTypeName = if (args.typeSpec != null) {
            ClassName("", name, "Args")
        } else { args.typeName }

        val argsParam = ParameterSpec.builder("args", args.typeName)
        if (function.args is ConvexFunction.Type.Any) {
            argsParam.defaultValue("Unit")
        }
        constructor.addParameter(argsParam.build())

        val output = generateTypeInfo(function.returns, "Output")
        val returnTypeName = if (output.typeSpec != null) {
            when (function.returns) {
                is ConvexFunction.Type.Array -> {
                    val listType = output.typeName as? com.squareup.kotlinpoet.ParameterizedTypeName
                    val argType = listType?.typeArguments?.get(0)
                    if (argType is ClassName && argType.simpleName == "Output") {
                        List::class.asTypeName().parameterizedBy(ClassName("", name, "Output"))
                    } else {
                        output.typeName
                    }
                }
                else -> ClassName("", name, "Output")
            }
        } else { output.typeName }

        val functionType = ClassName("com.kansson.kmp.convex.core", "ConvexFunction", function.functionType.name)
        return TypeSpec.classBuilder(name)
            .addModifiers(KModifier.DATA)
            .primaryConstructor(constructor.build())
            .addProperty(
                PropertySpec.builder("identifier", STRING)
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer("identifier")
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("args", args.typeName)
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer("args")
                    .build(),
            )
            .apply {
                args.typeSpec?.let { addType(it) }
                output.typeSpec?.let { addType(it) }
                if (function.args !is ConvexFunction.Type.Any) {
                    addType(buildCompanionObject(name, forArgs = true))
                }
            }
            .addSuperinterface(functionType.parameterizedBy(argsTypeName, returnTypeName))
            .build()
    }

    private fun generateTypeInfo(type: ConvexFunction.Type, name: String): TypeInfo = when (type) {
        is ConvexFunction.Type.Id -> TypeInfo(STRING)
        ConvexFunction.Type.String -> TypeInfo(STRING)
        is ConvexFunction.Type.Literal -> TypeInfo(STRING)
        ConvexFunction.Type.Int64 -> TypeInfo(ClassName("com.kansson.kmp.convex.core.type", "Long"))
        ConvexFunction.Type.Float64 -> TypeInfo(DOUBLE)
        ConvexFunction.Type.Bool -> TypeInfo(BOOLEAN)
        ConvexFunction.Type.Bytes -> TypeInfo(ClassName("com.kansson.kmp.convex.core.type", "ByteArray"))
        is ConvexFunction.Type.Array -> {
            val item = generateTypeInfo(type.value, name)
            val itemTypeName = if (item.typeSpec != null) {
                ClassName("", name)
            } else {
                item.typeName
            }

            TypeInfo(
                typeName = LIST.parameterizedBy(itemTypeName),
                typeSpec = item.typeSpec,
            )
        }
        is ConvexFunction.Type.Record -> {
            val value = generateTypeInfo(type.values.fieldType, name)
            TypeInfo(
                typeName = MAP.parameterizedBy(STRING, value.typeName),
                typeSpec = value.typeSpec,
            )
        }
        is ConvexFunction.Type.Object -> buildObjectType(type, name)
        is ConvexFunction.Type.Union -> TypeInfo(UNIT)
        ConvexFunction.Type.Null -> TypeInfo(UNIT.copy(nullable = true))
        ConvexFunction.Type.Any -> TypeInfo(UNIT)
    }

    private fun buildObjectType(objectType: ConvexFunction.Type.Object, name: String): TypeInfo {
        val constructor = FunSpec.constructorBuilder()
        objectType.value.forEach { (key, field) ->
            val data = generateTypeInfo(field.fieldType, key.replaceFirstChar { it.uppercase() })
            constructor.addParameter(key, data.typeName.copy(nullable = data.typeName.isNullable || field.optional))
        }

        val spec = TypeSpec.classBuilder(name)
            .addModifiers(KModifier.DATA)
            .addAnnotation(Serializable::class)
            .primaryConstructor(constructor.build())
            .apply {
                objectType.value.forEach { (key, field) ->
                    val data = generateTypeInfo(field.fieldType, key.replaceFirstChar { it.uppercase() })
                    val type = data.typeName.copy(nullable = data.typeName.isNullable || field.optional)

                    data.typeSpec?.let { addType(it) }
                    addProperty(
                        PropertySpec.builder(key, type)
                            .initializer(key)
                            .build(),
                    )
                }

                if (name != "Output") {
                    addType(buildDslBuilder(objectType.value, name))
                    addType(buildCompanionObject(name, forArgs = false))
                }
            }
            .build()

        return TypeInfo(ClassName("", name), spec)
    }

    private fun buildDslBuilder(fields: Map<String, ConvexFunction.Type.Object.Field>, name: String): TypeSpec {
        val arguments = mutableListOf<String>()

        val spec = TypeSpec.classBuilder("Builder")
            .addAnnotation(ClassName("com.kansson.kmp.convex.core", "ConvexDsl"))
            .apply {
                fields.forEach { (key, field) ->
                    val baseType = if (field.fieldType is ConvexFunction.Type.Object) {
                        LambdaTypeName.get(
                            receiver = ClassName("", key.replaceFirstChar { it.uppercase() }, "Builder"),
                            returnType = UNIT,
                        )
                    } else {
                        generateTypeInfo(field.fieldType, key.replaceFirstChar { it.uppercase() }).typeName
                    }

                    val propertyType = if (field.optional) baseType.copy(nullable = true) else baseType
                    val defaultValue = if (field.optional) CodeBlock.of("null") else defaultValueFor(field.fieldType)

                    addProperty(
                        PropertySpec.builder(key, propertyType)
                            .mutable(true)
                            .initializer(defaultValue)
                            .build(),
                    )

                    val argument = when {
                        field.fieldType is ConvexFunction.Type.Object && field.optional -> {
                            val capitalizedKey = key.replaceFirstChar { it.uppercase() }
                            "$key = $key?.let { $capitalizedKey.Builder().apply(it).build() }"
                        }
                        field.fieldType is ConvexFunction.Type.Object -> {
                            val capitalizedKey = key.replaceFirstChar { it.uppercase() }
                            "$key = $capitalizedKey.Builder().apply($key).build()"
                        }
                        else -> "$key = $key"
                    }
                    arguments += argument
                }

                addFunction(
                    FunSpec.builder("build")
                        .returns(ClassName("", name))
                        .addCode("return %T(%L)", ClassName("", name), arguments.joinToString(", "))
                        .build(),
                )
            }
            .build()

        return spec
    }

    private fun buildCompanionObject(name: String, forArgs: Boolean): TypeSpec {
        val receiverClass = if (forArgs) ClassName("", "Args", "Builder") else ClassName("", "Builder")
        val code = if (forArgs) {
            "return %T(args = Args.Builder().apply(block).build())"
        } else {
            "return Builder().apply(block).build()"
        }

        return TypeSpec.companionObjectBuilder()
            .addFunction(
                FunSpec.builder("invoke")
                    .addModifiers(KModifier.OPERATOR)
                    .returns(ClassName("", name))
                    .addParameter("block", LambdaTypeName.get(receiver = receiverClass, returnType = UNIT))
                    .addCode(code, ClassName("", name))
                    .build(),
            )
            .build()
    }

    private fun defaultValueFor(type: ConvexFunction.Type): CodeBlock = when (type) {
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
