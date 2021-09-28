package com.lightningkite.exposedplus

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType

data class Field(
    val name: String,
    val baseType: KSDeclaration,
    val kotlinType: KSType,
    val annotations: List<ResolvedAnnotation>
) {
    fun resolve(): ResolvedField {
        return when(val qn = kotlinType.declaration.qualifiedName!!.asString()) {
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
                baseType is KSClassDeclaration && baseType.classKind == ClassKind.ENUM_CLASS -> ResolvedField.Single(
                    name,
                    Column(
                        name = name,
                        type = kotlinType,
                        annotations = annotations
                    )
                )
                else -> throw IllegalArgumentException("Cannot convert ${baseType.qualifiedName?.asString()} to column")
            }
        }
    }
}