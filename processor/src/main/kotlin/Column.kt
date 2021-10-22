package com.lightningkite.exposedplus

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import java.io.Writer

data class Column(
    val name: String,
    val type: ColumnType,
    val nullable: Boolean,
    val annotations: List<ResolvedAnnotation>
) {
    sealed interface ColumnType {
        abstract val valueName: String
        object TypeByte: ColumnType { override val valueName: String get() = "kotlin.Byte" }
        object TypeUbyte: ColumnType { override val valueName: String get() = "kotlin.UByte" }
        object TypeShort: ColumnType { override val valueName: String get() = "kotlin.Short" }
        object TypeUshort: ColumnType { override val valueName: String get() = "kotlin.UShort" }
        object TypeInteger: ColumnType { override val valueName: String get() = "kotlin.Int" }
        object TypeUinteger: ColumnType { override val valueName: String get() = "kotlin.UInt" }
        object TypeLong: ColumnType { override val valueName: String get() = "kotlin.Long" }
        object TypeUlong: ColumnType { override val valueName: String get() = "kotlin.ULong" }
        object TypeFloat: ColumnType { override val valueName: String get() = "kotlin.Float" }
        object TypeDouble: ColumnType { override val valueName: String get() = "kotlin.Double" }
        object TypeDecimal: ColumnType { override val valueName: String get() = "java.math.BigDecimal" }
        object TypeChar: ColumnType { override val valueName: String get() = "kotlin.Char" }
        object TypeVarchar: ColumnType { override val valueName: String get() = "kotlin.String" }
        object TypeBinary: ColumnType { override val valueName: String get() = "kotlin.ByteArray" }
        object TypeBlob: ColumnType { override val valueName: String get() = "org.jetbrains.exposed.sql.statements.api.ExposedBlob" }
        object TypeUuid: ColumnType { override val valueName: String get() = "java.util.UUID" }
        object TypeBool: ColumnType { override val valueName: String get() = "kotlin.Boolean" }
        class TypeEnum(val enumClass: KSClassDeclaration): ColumnType {
            override val valueName: String
                get() = enumClass.qualifiedName!!.asString()
        }
        companion object {
            fun fromQn(qn: String): ColumnType? = when(qn) {
                "kotlin.Byte", "Byte" -> TypeByte
                "kotlin.UByte", "UByte" -> TypeUbyte
                "kotlin.Short", "Short" -> TypeShort
                "kotlin.UShort", "UShort" -> TypeUshort
                "kotlin.Int", "Int" -> TypeInteger
                "kotlin.UInt", "UInt" -> TypeUinteger
                "kotlin.Long", "Long" -> TypeLong
                "kotlin.ULong", "ULong" -> TypeUlong
                "kotlin.Float", "Float" -> TypeFloat
                "kotlin.Double", "Double" -> TypeDouble
                "java.math.BigDecimal", "BigDecimal" -> TypeDecimal
                "kotlin.Char", "Char" -> TypeChar
                "kotlin.String", "String" -> TypeVarchar
                "kotlin.ByteArray", "ByteArray" -> TypeBinary
                "org.jetbrains.exposed.sql.statements.api.ExposedBlob", "ExposedBlob" -> TypeBlob
                "java.util.UUID", "UUID" -> TypeUuid
                "kotlin.Boolean", "Boolean" -> TypeBool
                else -> null
            }
        }
    }
    fun writeColumnType(out: TabAppendable) {
        out.append("Column<")
        out.append(type.valueName)
        if(nullable) {
            out.append("?")
        }
        out.append(">")
    }
    fun writeBaseColumnDeclaration(out: TabAppendable, name: String = this.name, additionalWrite: ()->Unit = {}) {
        when(type) {
            ColumnType.TypeByte -> out.append("byte(\"$name\")")
            ColumnType.TypeUbyte -> out.append("ubyte(\"$name\")")
            ColumnType.TypeShort -> out.append("short(\"$name\")")
            ColumnType.TypeUshort -> out.append("ushort(\"$name\")")
            ColumnType.TypeInteger -> out.append("integer(\"$name\")")
            ColumnType.TypeUinteger -> out.append("uinteger(\"$name\")")
            ColumnType.TypeLong -> out.append("long(\"$name\")")
            ColumnType.TypeUlong -> out.append("ulong(\"$name\")")
            ColumnType.TypeFloat -> out.append("float(\"$name\")")
            ColumnType.TypeDouble -> out.append("double(\"$name\")")
            ColumnType.TypeDecimal -> out.append("decimal(\"$name\", ${annotations.byNameNumber("Precision") ?: 10}, ${annotations.byNameNumber("Scale") ?: 2})")
            ColumnType.TypeChar -> out.append("char(\"$name\")")
            ColumnType.TypeVarchar -> out.append("varchar(\"$name\", ${annotations.byNameNumber("Size") ?: 255})")
            ColumnType.TypeBinary -> out.append("binary(\"$name\", ${annotations.byNameNumber("Size") ?: 255})")
            ColumnType.TypeBlob -> out.append("blob(\"$name\")")
            ColumnType.TypeUuid -> out.append("uuid(\"$name\")")
            ColumnType.TypeBool -> out.append("bool(\"$name\")")
            is ColumnType.TypeEnum -> out.append("enumeration(\"$name\", ${type.enumClass.qualifiedName!!.asString()}::class)")
        }
        additionalWrite()
        if(nullable) out.append(".nullable()")
    }
    fun writeValueType(out: TabAppendable) {
        out.append(type.valueName)
        if(nullable) {
            out.append("?")
        }
    }
    fun writeColumnDeclaration(out: TabAppendable, additionalWrite: ()->Unit = {}) {
        writeBaseColumnDeclaration(out, additionalWrite = additionalWrite)
        if(annotations.byName("AutoIncrement") != null) out.append(".autoIncrement()")
        annotations.byName("Index")?.let {
            val name = it.arguments.values.firstOrNull()?.toString()
            if(name != null) {
                out.append(".index(\"$name\")")
            } else {
                out.append(".index()")
            }
        }
    }
}