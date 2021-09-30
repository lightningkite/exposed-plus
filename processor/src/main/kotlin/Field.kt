package com.lightningkite.exposedplus

import com.google.devtools.ksp.symbol.*

data class Field(
    val name: String,
    val baseType: KSDeclaration,
    val kotlinType: KSType,
    val annotations: List<ResolvedAnnotation>
) {
    fun resolve(): ResolvedField {
        return when (val qn = kotlinType.declaration.qualifiedName!!.asString()) {
            "kotlin.Byte",
            "kotlin.UByte",
            "kotlin.Short",
            "kotlin.UShort",
            "kotlin.Int",
            "kotlin.UInt",
            "kotlin.Long",
            "kotlin.ULong",
            "kotlin.Float",
            "kotlin.Double",
            "java.math.BigDecimal",
            "kotlin.Char",
            "kotlin.String",
            "kotlin.ByteArray",
            "org.jetbrains.exposed.sql.statements.api.ExposedBlob",
            "java.util.UUID",
            "kotlin.Boolean" -> ResolvedField.Single(
                name,
                Column(
                    name = name,
                    type = kotlinType,
                    annotations = annotations
                )
            )
            "com.lightningkite.exposedplus.ForeignKey",
            "com.lightningkite.exposedplus.FK" -> ResolvedField.ForeignKey(
                name = name,
                otherTable = tables[kotlinType.arguments[2].type!!.resolve().declaration.qualifiedName!!.asString()]!!
            )
            else -> when {
                baseType is KSClassDeclaration -> {
                    if (baseType.classKind == ClassKind.ENUM_CLASS)
                        ResolvedField.Single(
                        name,
                        Column(
                            name = name,
                            type = kotlinType,
                            annotations = annotations
                        )
                    )
                    else if (Modifier.DATA in baseType.modifiers) ResolvedField.Compound(name, baseType.subTable)
                    else throw IllegalArgumentException("Cannot convert ${baseType.qualifiedName?.asString()} to column")
                }
                else -> throw IllegalArgumentException("Cannot convert ${baseType.qualifiedName?.asString()} to column")
            }
        }
    }
}

fun KSPropertyDeclaration.toField(): Field {
    val k = this.type.resolve()
    return Field(
        name = this.simpleName.getShortName(),
        baseType = k.declaration,
        kotlinType = k,
        annotations = this.annotations.map { it.resolve() }.toList()
    )
}

fun KSClassDeclaration.fields(): List<Field> {
    val allProps = getAllProperties().associateBy { it.simpleName.getShortName() }
    return primaryConstructor!!
        .parameters
        .filter { it.isVal || it.isVar }
        .mapNotNull { allProps[it.name?.getShortName()] }
        .map { it.toField() }
}