package com.kansson.kmp.convex.plugin

import com.squareup.kotlinpoet.AnnotationSpec
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
    val isNullableUnion: Boolean = false,
    val innerTypeName: TypeName? = null,
)

private data class HierarchyNode(
    val parent: TypeSpec.Builder,
    val builder: TypeSpec.Builder,
)

private fun sanitizeFieldName(name: String): String {
    if (name.isEmpty()) return "_empty"
    val sanitized = name.replace(Regex("[^a-zA-Z0-9_]"), "_")
    return if (sanitized.first().isDigit()) "_$sanitized" else sanitized
}

private fun sanitizeClassName(name: String): String {
    if (name.isEmpty()) return "Empty"
    val sanitized = name
        .replace(Regex("[^a-zA-Z0-9_]"), "_")
        .split("_")
        .filter { it.isNotEmpty() }
        .joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }
    if (sanitized.isEmpty()) return "Value"
    return if (sanitized.first().isDigit()) "_$sanitized" else sanitized
}

internal object CodeGenerator {
    fun run(functions: List<ConvexFunction>): FileSpec {
        val hierarchy = mutableMapOf<String, HierarchyNode>()
        val root = TypeSpec.objectBuilder("Api")

        functions
            .filterIsInstance<ConvexFunction.RpcFunction>()
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
            .addAnnotation(
                AnnotationSpec.builder(ClassName("kotlin", "OptIn"))
                    .addMember("%T::class", ClassName("kotlinx.serialization", "ExperimentalSerializationApi"))
                    .build(),
            )
            .addImport("com.kansson.kmp.convex.core.type", "Nullable")
            .addType(root.build())
            .build()
    }

    private fun buildFunctionClass(name: String, function: ConvexFunction.RpcFunction): TypeSpec {
        val isEmptyArgs = function.args is ConvexFunction.Type.Object &&
            (function.args as ConvexFunction.Type.Object).value.isEmpty()
        val args = generateTypeInfo(function.args, "Args")
        val argsTypeName = if (isEmptyArgs) {
            UNIT
        } else if (args.typeSpec != null) {
            ClassName("", name, "Args")
        } else {
            args.typeName
        }

        val output = generateTypeInfo(function.returns, "Output")
        val returnTypeName = when {
            output.isNullableUnion && output.typeSpec != null -> {
                // For nullable unions like v.union(object, v.null()), use Output?
                ClassName("", name, "Output").copy(nullable = true)
            }
            output.typeSpec != null -> {
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
            }
            else -> output.typeName
        }

        val functionType = ClassName("com.kansson.kmp.convex.core", "ConvexFunction", function.functionType.name)
        val hasArgs = function.args !is ConvexFunction.Type.Any && !isEmptyArgs

        return if (hasArgs) {
            val constructor = FunSpec.constructorBuilder()
            constructor.addParameter(ParameterSpec.builder("args", args.typeName).build())

            TypeSpec.classBuilder(name)
                .addModifiers(KModifier.DATA)
                .primaryConstructor(constructor.build())
                .addProperty(
                    PropertySpec.builder("identifier", STRING)
                        .addModifiers(KModifier.OVERRIDE)
                        .initializer("%S", function.identifier)
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
                    addType(buildCompanionObject(name, forArgs = true))
                }
                .addSuperinterface(functionType.parameterizedBy(argsTypeName, returnTypeName))
                .build()
        } else {
            TypeSpec.objectBuilder(name)
                .addModifiers(KModifier.DATA)
                .addProperty(
                    PropertySpec.builder("identifier", STRING)
                        .addModifiers(KModifier.OVERRIDE)
                        .initializer("%S", function.identifier)
                        .build(),
                )
                .addProperty(
                    PropertySpec.builder("args", UNIT)
                        .addModifiers(KModifier.OVERRIDE)
                        .initializer("Unit")
                        .build(),
                )
                .apply {
                    output.typeSpec?.let { addType(it) }
                }
                .addSuperinterface(functionType.parameterizedBy(argsTypeName, returnTypeName))
                .build()
        }
    }

    private fun generateTypeInfo(type: ConvexFunction.Type, name: String): TypeInfo = when (type) {
        is ConvexFunction.Type.Id -> TypeInfo(STRING)
        ConvexFunction.Type.String -> TypeInfo(STRING)
        is ConvexFunction.Type.Literal -> buildEnumType(listOf(type), name)
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
        is ConvexFunction.Type.Union -> {
            val nonNullTypes = type.value.filter { it !is ConvexFunction.Type.Null }
            val hasNull = type.value.any { it is ConvexFunction.Type.Null }
            val isLiteralOnlyUnion = nonNullTypes.isNotEmpty() &&
                nonNullTypes.all { it is ConvexFunction.Type.Literal }

            when {
                // Pure literal union -> Enum
                isLiteralOnlyUnion && !hasNull -> buildEnumType(
                    nonNullTypes.filterIsInstance<ConvexFunction.Type.Literal>(),
                    name,
                )
                // Literals + null -> Nullable<Enum>
                isLiteralOnlyUnion && hasNull -> {
                    val enum = buildEnumType(
                        nonNullTypes.filterIsInstance<ConvexFunction.Type.Literal>(),
                        name,
                    )
                    val nullableClass = ClassName("com.kansson.kmp.convex.core.type", "Nullable")
                    TypeInfo(
                        typeName = nullableClass.parameterizedBy(enum.typeName),
                        typeSpec = enum.typeSpec,
                        isNullableUnion = true,
                        innerTypeName = enum.typeName,
                    )
                }
                // Handle T | null as Nullable<T> - must check before object unions
                type.value.size == 2 && hasNull -> {
                    val nonNullType = nonNullTypes.first()
                    val inner = generateTypeInfo(nonNullType, name)
                    val nullableClass = ClassName("com.kansson.kmp.convex.core.type", "Nullable")
                    TypeInfo(
                        typeName = nullableClass.parameterizedBy(inner.typeName),
                        typeSpec = inner.typeSpec,
                        isNullableUnion = true,
                        innerTypeName = inner.typeName,
                    )
                }
                type.value.any { it is ConvexFunction.Type.Object } -> buildObjectUnionType(type, name)
                // Handle primitive unions (string | number, etc.)
                type.value.size > 1 -> buildPrimitiveUnionType(type, name)
                else -> TypeInfo(UNIT) // Fallback for empty/single-type unions
            }
        }
        ConvexFunction.Type.Null -> TypeInfo(UNIT.copy(nullable = true))
        ConvexFunction.Type.Any -> TypeInfo(UNIT)
    }

    private fun buildObjectType(objectType: ConvexFunction.Type.Object, name: String): TypeInfo {
        // Empty objects become data objects
        if (objectType.value.isEmpty()) {
            val spec = TypeSpec.objectBuilder(name)
                .addModifiers(KModifier.DATA)
                .addAnnotation(Serializable::class)
                .build()
            return TypeInfo(ClassName("", name), spec)
        }

        val serialNameClass = ClassName("kotlinx.serialization", "SerialName")
        val constructor = FunSpec.constructorBuilder()

        objectType.value.forEach { (key, field) ->
            val sanitizedKey = sanitizeFieldName(key)
            val data = generateTypeInfo(field.fieldType, key.replaceFirstChar { it.uppercase() })
            // For nullable unions, the type is already Nullable<T>, only add nullable for optional
            val type = if (data.isNullableUnion) {
                if (field.optional) data.typeName.copy(nullable = true) else data.typeName
            } else {
                data.typeName.copy(nullable = data.typeName.isNullable || field.optional)
            }
            constructor.addParameter(sanitizedKey, type)
        }

        val spec = TypeSpec.classBuilder(name)
            .addModifiers(KModifier.DATA)
            .addAnnotation(Serializable::class)
            .primaryConstructor(constructor.build())
            .apply {
                objectType.value.forEach { (key, field) ->
                    val sanitizedKey = sanitizeFieldName(key)
                    val needsSerialName = sanitizedKey != key
                    val data = generateTypeInfo(field.fieldType, key.replaceFirstChar { it.uppercase() })
                    // For nullable unions, the type is already Nullable<T>, only add nullable for optional
                    val type = if (data.isNullableUnion) {
                        if (field.optional) data.typeName.copy(nullable = true) else data.typeName
                    } else {
                        data.typeName.copy(nullable = data.typeName.isNullable || field.optional)
                    }

                    data.typeSpec?.let { addType(it) }
                    addProperty(
                        PropertySpec.builder(sanitizedKey, type)
                            .initializer(sanitizedKey)
                            .apply {
                                if (needsSerialName) {
                                    addAnnotation(
                                        AnnotationSpec.builder(serialNameClass)
                                            .addMember("%S", key)
                                            .build(),
                                    )
                                }
                            }
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
                    val sanitizedKey = sanitizeFieldName(key)
                    val data = generateTypeInfo(field.fieldType, key.replaceFirstChar { it.uppercase() })
                    val isSealedClass = isSealedClassUnion(field.fieldType)

                    val baseType = when {
                        field.fieldType is ConvexFunction.Type.Object -> LambdaTypeName.get(
                            receiver = ClassName("", key.replaceFirstChar { it.uppercase() }, "Builder"),
                            returnType = UNIT,
                        )
                        // For nullable unions, use the inner type in the Builder
                        data.isNullableUnion -> data.innerTypeName!!
                        else -> data.typeName
                    }

                    // Sealed classes, nullable unions, and optional fields are nullable in the builder
                    val propertyType = if (field.optional || data.isNullableUnion || isSealedClass) {
                        baseType.copy(nullable = true)
                    } else {
                        baseType
                    }

                    val defaultValue = if (field.optional || data.isNullableUnion || isSealedClass) {
                        CodeBlock.of("null")
                    } else {
                        defaultValueFor(field.fieldType, key.replaceFirstChar { it.uppercase() })
                    }

                    addProperty(
                        PropertySpec.builder(sanitizedKey, propertyType)
                            .mutable(true)
                            .initializer(defaultValue)
                            .build(),
                    )

                    val argument = when {
                        field.fieldType is ConvexFunction.Type.Object && field.optional -> {
                            val capitalizedKey = key.replaceFirstChar { it.uppercase() }
                            "$sanitizedKey = $sanitizedKey?.let { $capitalizedKey.Builder().apply(it).build() }"
                        }
                        field.fieldType is ConvexFunction.Type.Object -> {
                            val capitalizedKey = key.replaceFirstChar { it.uppercase() }
                            "$sanitizedKey = $capitalizedKey.Builder().apply($sanitizedKey).build()"
                        }
                        // For nullable unions, wrap in Nullable(...)
                        data.isNullableUnion -> "$sanitizedKey = Nullable($sanitizedKey)"
                        // For required sealed classes, assert non-null
                        isSealedClass && !field.optional -> "$sanitizedKey = $sanitizedKey!!"
                        else -> "$sanitizedKey = $sanitizedKey"
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

    private fun isSealedClassUnion(type: ConvexFunction.Type): Boolean {
        if (type !is ConvexFunction.Type.Union) return false
        // Not a literal union (enum)
        if (type.value.all { it is ConvexFunction.Type.Literal }) return false
        // Has objects (discriminated union) or multiple primitives
        return type.value.any { it is ConvexFunction.Type.Object } || type.value.size > 1
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

    private fun isLiteralUnion(union: ConvexFunction.Type.Union): Boolean {
        return union.value.all { it is ConvexFunction.Type.Literal }
    }

    private fun literalToEnumName(literal: String): String {
        val cleaned = literal
            .replace(Regex("[^a-zA-Z0-9_]"), "_")
            .uppercase()
            .replace(Regex("_+"), "_")
            .trim('_')
            .ifEmpty { "EMPTY" }

        // Ensure valid identifier start (not a digit)
        return if (cleaned.first().isDigit()) "_$cleaned" else cleaned
    }

    private fun buildEnumType(literals: List<ConvexFunction.Type.Literal>, name: String): TypeInfo {
        val serialNameClass = ClassName("kotlinx.serialization", "SerialName")
        val usedNames = mutableSetOf<String>()

        val spec = TypeSpec.enumBuilder(name)
            .addAnnotation(Serializable::class)
            .apply {
                for (literal in literals) {
                    var enumName = literalToEnumName(literal.value)
                    var suffix = 1
                    val baseName = enumName
                    while (enumName in usedNames) {
                        enumName = "${baseName}_$suffix"
                        suffix++
                    }
                    usedNames.add(enumName)

                    addEnumConstant(
                        enumName,
                        TypeSpec.anonymousClassBuilder()
                            .addAnnotation(
                                AnnotationSpec.builder(serialNameClass)
                                    .addMember("%S", literal.value)
                                    .build(),
                            )
                            .build(),
                    )
                }
            }
            .build()

        return TypeInfo(ClassName("", name), spec)
    }

    private fun buildPrimitiveUnionType(union: ConvexFunction.Type.Union, name: String): TypeInfo {
        val sealedClassBuilder = TypeSpec.classBuilder(name)
            .addModifiers(KModifier.SEALED)
            .addAnnotation(Serializable::class)

        val usedNames = mutableSetOf<String>()

        union.value.forEachIndexed { index, type ->
            when (type) {
                is ConvexFunction.Type.Null -> {
                    usedNames.add("Null")
                    val nullVariant = TypeSpec.objectBuilder("Null")
                        .addModifiers(KModifier.DATA)
                        .addAnnotation(Serializable::class)
                        .superclass(ClassName("", name))
                        .build()
                    sealedClassBuilder.addType(nullVariant)
                }
                else -> {
                    var variantName = variantNameForType(type, index)
                    var suffix = 1
                    val baseName = variantName
                    while (variantName in usedNames) {
                        variantName = "${baseName}_$suffix"
                        suffix++
                    }
                    usedNames.add(variantName)

                    val innerType = generateTypeInfo(type, variantName)

                    val variantSpec = TypeSpec.classBuilder(variantName)
                        .addModifiers(KModifier.DATA)
                        .addAnnotation(Serializable::class)
                        .primaryConstructor(
                            FunSpec.constructorBuilder()
                                .addParameter("value", innerType.typeName)
                                .build(),
                        )
                        .addProperty(
                            PropertySpec.builder("value", innerType.typeName)
                                .initializer("value")
                                .build(),
                        )
                        .superclass(ClassName("", name))
                        .build()
                    sealedClassBuilder.addType(variantSpec)
                }
            }
        }

        return TypeInfo(ClassName("", name), sealedClassBuilder.build())
    }

    private fun buildObjectUnionType(union: ConvexFunction.Type.Union, name: String): TypeInfo {
        val serialNameClass = ClassName("kotlinx.serialization", "SerialName")
        val jsonClassDiscriminator = ClassName("kotlinx.serialization.json", "JsonClassDiscriminator")

        // Detect discriminator field
        val discriminatorField = detectDiscriminatorField(union)

        val sealedClassBuilder = TypeSpec.classBuilder(name)
            .addModifiers(KModifier.SEALED)
            .addAnnotation(Serializable::class)

        if (discriminatorField != null) {
            sealedClassBuilder.addAnnotation(
                AnnotationSpec.builder(jsonClassDiscriminator)
                    .addMember("%S", discriminatorField)
                    .build(),
            )
        }

        union.value.forEachIndexed { index, type ->
            when (type) {
                is ConvexFunction.Type.Object -> {
                    val variantName = determineVariantName(type, discriminatorField, index)
                    val serialName = getSerialNameForVariant(type, discriminatorField, index)

                    // Exclude discriminator field from properties
                    val variantFields = if (discriminatorField != null) {
                        type.value.filterKeys { it != discriminatorField }
                    } else {
                        type.value
                    }

                    val variantSpec = buildVariantDataClass(variantName, variantFields, serialName, name)
                    sealedClassBuilder.addType(variantSpec)
                }
                is ConvexFunction.Type.Null -> {
                    val nullVariant = TypeSpec.objectBuilder("Null")
                        .addModifiers(KModifier.DATA)
                        .addAnnotation(Serializable::class)
                        .superclass(ClassName("", name))
                        .build()
                    sealedClassBuilder.addType(nullVariant)
                }
                else -> {
                    // Handle primitive types wrapped in a value holder
                    val variantName = variantNameForType(type, index)
                    val innerType = generateTypeInfo(type, variantName)

                    val variantSpec = TypeSpec.classBuilder(variantName)
                        .addModifiers(KModifier.DATA)
                        .addAnnotation(Serializable::class)
                        .primaryConstructor(
                            FunSpec.constructorBuilder()
                                .addParameter("value", innerType.typeName)
                                .build(),
                        )
                        .addProperty(
                            PropertySpec.builder("value", innerType.typeName)
                                .initializer("value")
                                .build(),
                        )
                        .superclass(ClassName("", name))
                        .build()
                    sealedClassBuilder.addType(variantSpec)
                }
            }
        }

        return TypeInfo(ClassName("", name), sealedClassBuilder.build())
    }

    private fun detectDiscriminatorField(union: ConvexFunction.Type.Union): String? {
        val objects = union.value.filterIsInstance<ConvexFunction.Type.Object>()
        if (objects.size < 2) return null

        // Find all field names that exist in every object variant
        val commonFields = objects
            .map { it.value.keys }
            .reduce { acc, keys -> acc.intersect(keys) }

        // Find a field where every variant has a literal type with unique values
        for (fieldName in commonFields) {
            val literals = objects.mapNotNull { obj ->
                (obj.value[fieldName]?.fieldType as? ConvexFunction.Type.Literal)?.value
            }

            // All variants must have a literal for this field, and all values must be unique
            if (literals.size == objects.size && literals.distinct().size == literals.size) {
                return fieldName
            }
        }

        return null
    }

    private fun determineVariantName(
        obj: ConvexFunction.Type.Object,
        discriminatorField: String?,
        index: Int,
    ): String {
        if (discriminatorField != null) {
            val literal = obj.value[discriminatorField]?.fieldType as? ConvexFunction.Type.Literal
            if (literal != null) {
                return sanitizeClassName(literal.value)
            }
        }
        return "Variant$index"
    }

    private fun getSerialNameForVariant(
        obj: ConvexFunction.Type.Object,
        discriminatorField: String?,
        index: Int,
    ): String {
        if (discriminatorField != null) {
            val literal = obj.value[discriminatorField]?.fieldType as? ConvexFunction.Type.Literal
            if (literal != null) {
                return literal.value
            }
        }
        return "variant$index"
    }

    private fun variantNameForType(type: ConvexFunction.Type, index: Int): String = when (type) {
        is ConvexFunction.Type.String -> "StringValue"
        is ConvexFunction.Type.Int64 -> "IntValue"
        is ConvexFunction.Type.Float64 -> "DoubleValue"
        is ConvexFunction.Type.Bool -> "BoolValue"
        is ConvexFunction.Type.Array -> "ArrayValue"
        is ConvexFunction.Type.Id -> "IdValue"
        is ConvexFunction.Type.Bytes -> "BytesValue"
        else -> "Variant$index"
    }

    private fun buildVariantDataClass(
        variantName: String,
        fields: Map<String, ConvexFunction.Type.Object.Field>,
        serialName: String,
        parentName: String,
    ): TypeSpec {
        val serialNameClass = ClassName("kotlinx.serialization", "SerialName")

        // Use data object when there are no fields
        if (fields.isEmpty()) {
            return TypeSpec.objectBuilder(variantName)
                .addModifiers(KModifier.DATA)
                .addAnnotation(Serializable::class)
                .addAnnotation(
                    AnnotationSpec.builder(serialNameClass)
                        .addMember("%S", serialName)
                        .build(),
                )
                .superclass(ClassName("", parentName))
                .build()
        }

        val constructor = FunSpec.constructorBuilder()
        fields.forEach { (key, field) ->
            val sanitizedKey = sanitizeFieldName(key)
            val typeInfo = generateTypeInfo(field.fieldType, key.replaceFirstChar { it.uppercase() })
            // For nullable unions, the type is already Nullable<T>, only add nullable for optional
            val typeName = if (typeInfo.isNullableUnion) {
                if (field.optional) typeInfo.typeName.copy(nullable = true) else typeInfo.typeName
            } else {
                typeInfo.typeName.copy(nullable = typeInfo.typeName.isNullable || field.optional)
            }
            constructor.addParameter(sanitizedKey, typeName)
        }

        return TypeSpec.classBuilder(variantName)
            .addModifiers(KModifier.DATA)
            .addAnnotation(Serializable::class)
            .addAnnotation(
                AnnotationSpec.builder(serialNameClass)
                    .addMember("%S", serialName)
                    .build(),
            )
            .primaryConstructor(constructor.build())
            .superclass(ClassName("", parentName))
            .apply {
                fields.forEach { (key, field) ->
                    val sanitizedKey = sanitizeFieldName(key)
                    val needsSerialName = sanitizedKey != key
                    val typeInfo = generateTypeInfo(field.fieldType, key.replaceFirstChar { it.uppercase() })
                    // For nullable unions, the type is already Nullable<T>, only add nullable for optional
                    val typeName = if (typeInfo.isNullableUnion) {
                        if (field.optional) typeInfo.typeName.copy(nullable = true) else typeInfo.typeName
                    } else {
                        typeInfo.typeName.copy(nullable = typeInfo.typeName.isNullable || field.optional)
                    }

                    typeInfo.typeSpec?.let { addType(it) }
                    addProperty(
                        PropertySpec.builder(sanitizedKey, typeName)
                            .initializer(sanitizedKey)
                            .apply {
                                if (needsSerialName) {
                                    addAnnotation(
                                        AnnotationSpec.builder(serialNameClass)
                                            .addMember("%S", key)
                                            .build(),
                                    )
                                }
                            }
                            .build(),
                    )
                }
            }
            .build()
    }

    private fun defaultValueFor(type: ConvexFunction.Type, name: String): CodeBlock = when (type) {
        is ConvexFunction.Type.Id -> CodeBlock.of("%S", "")
        ConvexFunction.Type.String -> CodeBlock.of("%S", "")
        is ConvexFunction.Type.Literal -> CodeBlock.of("%T.%L", ClassName("", name), literalToEnumName(type.value))
        ConvexFunction.Type.Int64 -> CodeBlock.of("%L", 0L)
        ConvexFunction.Type.Float64 -> CodeBlock.of("%L", 0.0)
        ConvexFunction.Type.Bool -> CodeBlock.of("false")
        ConvexFunction.Type.Bytes -> CodeBlock.of("byteArrayOf()")
        is ConvexFunction.Type.Array -> CodeBlock.of("emptyList()")
        is ConvexFunction.Type.Record -> CodeBlock.of("emptyMap()")
        is ConvexFunction.Type.Object -> CodeBlock.of("{}")
        is ConvexFunction.Type.Union -> {
            when {
                isLiteralUnion(type) -> {
                    val first = (type.value.first() as ConvexFunction.Type.Literal).value
                    CodeBlock.of("%T.%L", ClassName("", name), literalToEnumName(first))
                }
                else -> CodeBlock.of("null")
            }
        }
        ConvexFunction.Type.Null -> CodeBlock.of("%L", Unit)
        ConvexFunction.Type.Any -> CodeBlock.of("%L", Unit)
    }
}
